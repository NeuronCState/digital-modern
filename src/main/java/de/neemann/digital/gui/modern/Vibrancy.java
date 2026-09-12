/*
 * Copyright (c) 2026 晨光
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.modern;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

/**
 * Apply a real system-level macOS vibrancy effect underneath a Swing panel,
 * replicating the macOS 26 "Liquid Glass" / ChatGPT sidebar look.
 *
 * <p>The effect is rendered by a native {@code NSVisualEffectView} (macOS 13+)
 * or {@code NSGlassEffectView} (macOS 26 "Liquid Glass") hosted as the
 * sibling behind the AWT {@code NSWindow}'s content view. Because the
 * effect view lives inside the same window that hosts the Swing tree, mouse
 * events flow straight through to Swing — there is no floating panel to
 * steal them. Translucency comes from the macOS compositor, so it tracks
 * live desktop / window changes (including windows moving behind the app)
 * without any per-frame screenshot capture.</p>
 *
 * <p>On non-macOS systems, when the native dylib fails to load, or when the
 * panel is not yet on screen, {@link #apply(JComponent)} is a no-op and the
 * existing FlatLaf styling stays in effect — the rest of the UI is
 * unchanged.</p>
 *
 * <p>Call once per sidebar after it has been added to its container. Calling
 * twice on the same panel is safe (subsequent calls are no-ops).</p>
 */
public final class Vibrancy {

    /** HUD window material (always available, ChatGPT-sidebar look on 13+). */
    public static final int STYLE_HUD = 0;
    /** Sidebar material (macOS 14+; falls back to HUD if unavailable). */
    public static final int STYLE_SIDEBAR = 1;
    /** Under-window background (classic vibrancy). */
    public static final int STYLE_UNDER_WINDOW = 2;

    private static final String DISABLE_FLAG = "digital.disableNativeVibrancy";
    private static final String PREF_KEY = "vibrancyEnabled";

    private interface Dylib extends Library {
        Pointer dv_vibrancy_attach(Pointer window, int style, double cornerRadius);
        void     dv_vibrancy_set_frame(Pointer handle, double x, double y, double w, double h);
        void     dv_vibrancy_set_hidden(Pointer handle, int hidden);
        void     dv_vibrancy_set_corner_radius(Pointer handle, double r);
        void     dv_vibrancy_set_active(Pointer handle, int active);
        void     dv_vibrancy_set_window_transparent(Pointer window, int transparent);
        void     dv_vibrancy_detach(Pointer handle);
        int      dv_vibrancy_has_glass();
    }

    /**
     * Default-on now that the effect view is hosted inside the AWT NSWindow
     * (the previous opt-in flag guarded a floating NSPanel implementation
     * that ate mouse events). The opt-out for testing is
     * {@code -Ddigital.disableNativeVibrancy=true} on the JVM command line
     * or {@code vibrancyEnabled=false} in the per-user preferences.
     */
    private static final boolean ENABLED = !Boolean.getBoolean(DISABLE_FLAG)
            && Preferences.userNodeForPackage(Vibrancy.class).getBoolean(PREF_KEY, true);

    private static final Dylib DYLIB;
    static {
        Dylib lib = null;
        if (Platform.isMac() && ENABLED) {
            try {
                lib = loadBundledLibrary();
            } catch (Throwable t) {
                System.err.println("[Vibrancy] native library not loaded: " + t);
                lib = null;
            }
        }
        DYLIB = lib;
    }

    private static Dylib loadBundledLibrary() throws Exception {
        Path location = Paths.get(Vibrancy.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path contents = Files.isDirectory(location) ? location.getParent() : location.getParent().getParent();
        Path bundled = contents.resolve("MacOS/libDigitalVibrancy.dylib");
        if (Files.isRegularFile(bundled))
            return Native.load(bundled.toAbsolutePath().toString(), Dylib.class);
        return Native.load("DigitalVibrancy", Dylib.class);
    }

    /** True if the native bridge was successfully loaded. */
    public static boolean isAvailable() {
        return DYLIB != null;
    }

    /** True if macOS 26 "Liquid Glass" (NSGlassEffectView) is in use. */
    public static boolean isGlassActive() {
        return DYLIB != null && DYLIB.dv_vibrancy_has_glass() == 1;
    }

    /**
     * Reflection lookup for the AWT NSWindow pointer. JNA's
     * {@code Native.getWindowPointer()} stops returning the same pointer
     * after the window becomes transparent (the LWWindowPeer's cached
     * native id is invalidated during the opaque flip), so we go straight
     * to the CPlatformWindow's {@code ptr} field which is the actual
     * retained NSWindow reference. Set up once on first use.
     */
    private static final Field COMPONENT_PEER_FIELD = locateField("java.awt.Component", "peer");
    private static final Field PLATFORM_WINDOW_FIELD = locateField("sun.lwawt.LWWindowPeer", "platformWindow");
    private static final Field PTR_FIELD = locateField("sun.lwawt.macosx.CFRetainedResource", "ptr");

    private static Field locateField(String className, String fieldName) {
        try {
            Class<?> c = Class.forName(className);
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable t) {
            // Non-mac or non-OpenJDK runtime — caller will fall back to JNA.
        }
        return null;
    }

    /**
     * Returns the NSWindow pointer for {@code window} as a JNA Pointer, or
     * null when the platform lookup fails. Tries reflection first (stable
     * across opaque flips) and falls back to JNA's helper.
     */
    private static Pointer nsWindowPointer(Window window) {
        if (COMPONENT_PEER_FIELD != null && PLATFORM_WINDOW_FIELD != null && PTR_FIELD != null) {
            try {
                Object peer = COMPONENT_PEER_FIELD.get(window);
                if (peer == null) return null;
                Object platformWindow = PLATFORM_WINDOW_FIELD.get(peer);
                if (platformWindow == null) return null;
                long ptr = PTR_FIELD.getLong(platformWindow);
                if (ptr != 0) return new Pointer(ptr);
            } catch (Throwable ignored) { }
        }
        try {
            return Native.getWindowPointer(window);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Apply the default HUD-style vibrancy to {@code panel}. The panel and
     * its opaque descendants are made transparent so the blur shows through.
     * Safe to call before the panel is shown; the native view is created
     * lazily on the first size/position event.
     */
    public static void apply(JComponent panel) {
        apply(panel, STYLE_HUD, 0.0);
    }

    /**
     * Apply a styled vibrancy to {@code panel}. {@code cornerRadius} of 0
     * produces the square edges that ChatGPT's sidebar has; pass a positive
     * value to round the panel corners.
     */
    public static void apply(JComponent panel, int style, double cornerRadius) {
        if (panel == null) return;
        if (DYLIB == null) return;
        synchronized (ACTIVE) {
            if (ACTIVE.containsKey(panel)) return;
        }

        // The modern sidebar keeps a translucent alpha surface so Swing
        // doesn't paint a solid block over the native effect layer beneath.
        if (!Boolean.TRUE.equals(panel.getClientProperty("modern.translucentSurface")))
            panel.setOpaque(false);
        for (Component c : panel.getComponents()) {
            if (c instanceof JComponent) ((JComponent) c).setOpaque(false);
        }

        Active a = new Active();
        a.style = style;
        a.cornerRadius = cornerRadius;

        Runnable sync = () -> sync(panel, a);

        a.componentListener = new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { sync.run(); }
            @Override public void componentMoved(ComponentEvent e)   { sync.run(); }
            @Override public void componentShown(ComponentEvent e)   { sync.run(); }
            @Override public void componentHidden(ComponentEvent e)   { hide(a); }
        };
        panel.addComponentListener(a.componentListener);

        a.hierarchyListener = e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (panel.isShowing()) {
                    attachWindowListeners(panel, a);
                    sync.run();
                } else {
                    hide(a);
                }
            }
        };
        panel.addHierarchyListener(a.hierarchyListener);

        if (panel.isShowing()) {
            attachWindowListeners(panel, a);
        }

        synchronized (ACTIVE) {
            ACTIVE.put(panel, a);
        }

        // First sync after Swing has had a chance to lay the panel out.
        SwingUtilities.invokeLater(sync);
    }

    /** Detach listeners and release the native view. Idempotent. */
    public static void detach(JComponent panel) {
        Active a;
        synchronized (ACTIVE) {
            a = ACTIVE.remove(panel);
        }
        if (a == null) return;
        if (a.componentListener != null) panel.removeComponentListener(a.componentListener);
        if (a.hierarchyListener != null) panel.removeHierarchyListener(a.hierarchyListener);
        if (a.window != null) {
            if (a.windowListener != null) a.window.removeComponentListener(a.windowListener);
            if (a.windowStateListener != null) a.window.removeWindowStateListener(a.windowStateListener);
            if (a.windowEventListener != null) a.window.removeWindowListener(a.windowEventListener);
        }
        if (a.handle != null) {
            try {
                DYLIB.dv_vibrancy_detach(a.handle);
            } catch (Throwable ignored) { }
            a.handle = null;
        }
    }

    // ---------------------------------------------------------------------

    private static final Map<JComponent, Active> ACTIVE = new WeakHashMap<>();

    private static final class Active {
        Pointer handle;
        Pointer nsWindow;          /* Cached on first successful lookup; stable
                                    * across the opaque flip that JNA's
                                    * Native.getWindowPointer() doesn't survive. */
        int     style;
        double  cornerRadius;
        int     lastX = Integer.MIN_VALUE;
        int     lastY = Integer.MIN_VALUE;
        int     lastWidth = -1;
        int     lastHeight = -1;
        boolean hidden = true;
        boolean windowTransparent;
        java.awt.event.ComponentListener componentListener;
        HierarchyListener                hierarchyListener;
        Window                           window;
        java.awt.event.ComponentListener windowListener;
        java.awt.event.WindowStateListener windowStateListener;
        java.awt.event.WindowListener    windowEventListener;
    }

    private static void attachWindowListeners(JComponent panel, Active a) {
        Window window = SwingUtilities.getWindowAncestor(panel);
        if (window == null || window == a.window) return;
        if (a.window != null) {
            if (a.windowListener != null) a.window.removeComponentListener(a.windowListener);
            if (a.windowStateListener != null) a.window.removeWindowStateListener(a.windowStateListener);
            if (a.windowEventListener != null) a.window.removeWindowListener(a.windowEventListener);
        }
        a.window = window;
        a.windowTransparent = false;

        a.windowListener = new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent e)   { sync(panel, a); }
            @Override public void componentResized(ComponentEvent e) { sync(panel, a); }
        };
        window.addComponentListener(a.windowListener);

        a.windowStateListener = e -> sync(panel, a);
        window.addWindowStateListener(a.windowStateListener);

        a.windowEventListener = new WindowAdapter() {
            @Override public void windowActivated(WindowEvent e)   {
                if (a.handle != null) DYLIB.dv_vibrancy_set_active(a.handle, 1);
                sync(panel, a);
            }
            @Override public void windowDeactivated(WindowEvent e) {
                if (a.handle != null) DYLIB.dv_vibrancy_set_active(a.handle, 0);
            }
            @Override public void windowClosing(WindowEvent e)    { detach(panel); }
        };
        window.addWindowListener(a.windowEventListener);
    }

    private static void sync(JComponent panel, Active a) {
        if (DYLIB == null) return;
        if (!panel.isShowing()) { hide(a); return; }
        Window window = SwingUtilities.getWindowAncestor(panel);
        if (window == null || !window.isVisible()) { hide(a); return; }
        if (!(window instanceof RootPaneContainer)) { hide(a); return; }

        // Resolve the NSWindow pointer once and cache it. The cached pointer
        // is a CFRetainedResource-owned ObjC object that AppKit keeps alive
        // for the lifetime of the AWT frame, so it stays valid after the
        // opaque flip that JNA's Native.getWindowPointer() can't survive.
        if (a.nsWindow == null) {
            Pointer p = nsWindowPointer(window);
            if (p == null) return;
            a.nsWindow = p;
        }

        // A Java alpha background is not legal on a decorated macOS AWT
        // Frame. Let AppKit make the actual NSWindow transparent instead.
        if (!a.windowTransparent) {
            try {
                DYLIB.dv_vibrancy_set_window_transparent(a.nsWindow, 1);
                a.windowTransparent = true;
            } catch (Throwable ignored) { return; }
        }

        JRootPane root = ((RootPaneContainer) window).getRootPane();
        Rectangle b = panel.getBounds();
        if (b.width <= 0 || b.height <= 0) { hide(a); return; }

        // Translate panel bounds to the AWT content view's coordinate
        // space. The native bridge converts from AWT content coordinates, so
        // it must be framed in that local space (top-left origin). The
        // dylib flips the y internally if the content view isn't flipped.
        Point panelRel = SwingUtilities.convertPoint(panel, new Point(0, 0), root.getContentPane());
        // We want coords relative to the AWT content view (root.getContentPane()
        // and root both live inside it), so convert to root's coords.
        Point rootRel = SwingUtilities.convertPoint(panel, new Point(0, 0), root);
        int x = rootRel.x;
        int y = rootRel.y;

        try {
            if (a.handle == null) {
                a.handle = DYLIB.dv_vibrancy_attach(a.nsWindow, a.style, a.cornerRadius);
                if (a.handle == null) return;
                a.lastX = Integer.MIN_VALUE;
                a.lastY = Integer.MIN_VALUE;
                a.lastWidth = -1;
                a.lastHeight = -1;
            }
            if (x != a.lastX || y != a.lastY
                    || b.width != a.lastWidth || b.height != a.lastHeight) {
                DYLIB.dv_vibrancy_set_frame(a.handle, x, y, b.width, b.height);
                a.lastX = x;
                a.lastY = y;
                a.lastWidth = b.width;
                a.lastHeight = b.height;
            }
            if (a.hidden) {
                DYLIB.dv_vibrancy_set_hidden(a.handle, 0);
                a.hidden = false;
            }
            DYLIB.dv_vibrancy_set_active(a.handle, window.isActive() ? 1 : 0);
        } catch (Throwable t) {
            // The native view may have been destroyed by the system. Clear
            // the handle so a future sync recreates it.
            a.handle = null;
        }
    }

    private static void hide(Active a) {
        if (a == null || DYLIB == null) return;
        if (a.handle != null) {
            try { DYLIB.dv_vibrancy_set_hidden(a.handle, 1); } catch (Throwable ignored) { }
        }
        a.hidden = true;
    }
}
