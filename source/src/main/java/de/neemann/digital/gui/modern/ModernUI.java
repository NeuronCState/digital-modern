/*
 * Copyright (c) 2026 晨光
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.modern;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import de.neemann.digital.draw.graphics.ColorScheme;
import de.neemann.digital.gui.components.CircuitComponent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.UIResource;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Modern desktop chrome, leaving the circuit renderer and simulator intact. */
public final class ModernUI {
    private static final Color LIGHT_INK = new Color(0x252628);
    private static final Color LIGHT_MUTED = new Color(0x73767C);
    private static final Color LIGHT_SURFACE = new Color(0xF5F5F4);
    private static final Color LIGHT_TOOLBAR = new Color(0xFAFAF9);
    private static final Color LIGHT_BORDER = new Color(0xDEDFE1);
    private static final Color LIGHT_SELECTION = new Color(0xE2E8F0);
    private static final Color LIGHT_TOOLTIP = new Color(0xFFFFFF);
    private static final Color DARK_INK = new Color(0xF2F2F7);
    private static final Color DARK_MUTED = new Color(0xA1A1AA);
    private static final Color DARK_SURFACE = new Color(0x1C1C1E);
    private static final Color DARK_TOOLBAR = new Color(0x242426);
    private static final Color DARK_BORDER = new Color(0x3A3A3C);
    private static final Color DARK_SELECTION = new Color(0x35465D);
    private static final Color DARK_TOOLTIP = new Color(0x2C2C2E);
    private static volatile boolean darkAppearance;
    private static volatile Color ink = LIGHT_INK;
    private static volatile Color muted = LIGHT_MUTED;
    private static volatile Color surface = LIGHT_SURFACE;
    private static volatile Color toolbar = LIGHT_TOOLBAR;
    private static volatile Color border = LIGHT_BORDER;
    private static volatile Color selection = LIGHT_SELECTION;
    private static volatile Color tooltip = LIGHT_TOOLTIP;
    private static final Map<String, String> LEGACY = new HashMap<>();
    private static final Properties GLYPHS = new Properties();
    private static Font iconFont;
    private static boolean installed;
    private static Timer appearanceMonitor;
    private static volatile boolean appearanceCheckInFlight;
    private static final String STYLED = "modern.windowStyled";
    private static boolean reduceMotion = Boolean.getBoolean("digital.reduceMotion")
            || java.util.prefs.Preferences.userNodeForPackage(ModernUI.class).getBoolean("reduceMotion", false);
    static {
        String[][] pairs = {
            {"document-new.png", "square-pen"}, {"document-new-sub.png", "file-plus-2"},
            {"document-open.png", "folder-open"}, {"document-open-new.png", "folder-open"},
            {"document-save.png", "save"}, {"document-save-as.png", "save"},
            {"media-playback-start.png", "play"}, {"media-playback-start-2.png", "step-forward"},
            {"media-playback-start-T.png", "list-checks"}, {"media-seek-forward.png", "step-forward"},
            {"media-seek-forward-f.png", "skip-forward"}, {"media-skip-forward.png", "fast-forward"},
            {"media-playback-stop.png", "square"}, {"View-zoom-fit.png", "maximize"},
            {"View-zoom-in.png", "zoom-in"}, {"View-zoom-out.png", "zoom-out"},
            {"help.png", "circle-help"}, {"edit-undo.png", "undo-2"},
            {"edit-redo.png", "redo-2"}, {"delete.png", "trash-2"}
        };
        for (String[] pair : pairs) LEGACY.put(pair[0], pair[1]);
        try (InputStream font = ModernUI.class.getResourceAsStream("/modern/lucide.ttf");
             InputStream codes = ModernUI.class.getResourceAsStream("/modern/icons.properties")) {
            if (font != null && codes != null) {
                iconFont = Font.createFont(Font.TRUETYPE_FONT, font).deriveFont(19f);
                GLYPHS.load(codes);
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot load the bundled icon font", e); }
    }
    private ModernUI() { }

    public static void install() {
        if (installed) return;
        installed = true;
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Digital");
        applyAppearance(detectSystemDarkAppearance());
        UIManager.put("defaultFont", new Font(".AppleSystemUIFont", Font.PLAIN, 13));
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 14);
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.focusColor", new Color(0x92B9F4));
        UIManager.put("Component.minimumWidth", 72);
        UIManager.put("Button.minimumHeight", 34);
        UIManager.put("ToggleButton.minimumHeight", 34);
        UIManager.put("TextField.minimumHeight", 34);
        UIManager.put("FormattedTextField.minimumHeight", 34);
        UIManager.put("PasswordField.minimumHeight", 34);
        UIManager.put("ComboBox.minimumHeight", 34);
        UIManager.put("Spinner.minimumHeight", 34);
        UIManager.put("Button.margin", new Insets(7, 14, 7, 14));
        UIManager.put("OptionPane.messageAreaBorder", new EmptyBorder(4, 4, 12, 4));
        UIManager.put("OptionPane.buttonAreaBorder", new EmptyBorder(8, 0, 0, 0));
        UIManager.put("OptionPane.maxCharactersPerLineCount", 72);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.tabArc", 12);
        UIManager.put("TabbedPane.tabHeight", 34);
        UIManager.put("TabbedPane.tabInsets", new Insets(6, 14, 6, 14));
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));
        UIManager.put("TableHeader.height", 32);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("ProgressBar.horizontalSize", new Dimension(146, 8));
        UIManager.put("PopupMenu.borderCornerRadius", 12);
        UIManager.put("PopupMenu.dropShadowPainted", true);
        UIManager.put("MenuItem.selectionType", "underline");
        UIManager.put("Tree.selectionArc", 10);
        UIManager.put("Tree.rowHeight", 32);
        UIManager.put("Tree.paintLines", false);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("SplitPaneDivider.gripDotCount", 0);
        UIManager.put("ToolTip.border", new EmptyBorder(8, 10, 8, 10));
        applyPaletteDefaults();

        // One event listener covers every editor, wizard, simulator window and
        // dynamically-created option pane without coupling the theme to dozens
        // of individual dialog implementations.
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent windowEvent
                    && windowEvent.getID() == WindowEvent.WINDOW_OPENED) {
                styleWindow(windowEvent.getWindow());
            } else if (event instanceof ContainerEvent containerEvent
                    && containerEvent.getID() == ContainerEvent.COMPONENT_ADDED) {
                styleComponentTree(containerEvent.getChild());
            }
        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
        for (Window window : Window.getWindows()) styleWindow(window);
        startAppearanceMonitor();
    }

    /**
     * Uses the macOS system appearance as the source of truth. The small
     * polling fallback is intentional: the desktop property is not emitted
     * consistently by every JDK/macOS combination, while the defaults value
     * changes immediately when the user switches Appearance in System
     * Settings.
     */
    private static boolean detectSystemDarkAppearance() {
        String forced = System.getProperty("apple.awt.application.appearance", "").toLowerCase();
        if (forced.contains("dark")) return true;
        if (forced.contains("light")) return false;
        if (!SystemInfo.isMacOS) return false;

        Process process = null;
        try {
            process = new ProcessBuilder("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(400, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.exitValue() == 0 && output.toLowerCase().contains("dark");
        } catch (InterruptedException ignored) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException ignored) {
            if (process != null) process.destroyForcibly();
            return false;
        }
    }

    private static void startAppearanceMonitor() {
        if (!SystemInfo.isMacOS || GraphicsEnvironment.isHeadless()) return;
        appearanceMonitor = new Timer(1200, event -> {
            if (appearanceCheckInFlight) return;
            appearanceCheckInFlight = true;
            Thread detector = new Thread(() -> {
                boolean dark = detectSystemDarkAppearance();
                SwingUtilities.invokeLater(() -> {
                    appearanceCheckInFlight = false;
                    if (dark != darkAppearance) applyAppearance(dark);
                });
            }, "Digital-appearance-monitor");
            detector.setDaemon(true);
            detector.start();
        });
        appearanceMonitor.setRepeats(true);
        appearanceMonitor.start();
    }

    private static void applyAppearance(boolean dark) {
        darkAppearance = dark;
        if (dark) FlatMacDarkLaf.setup();
        else FlatMacLightLaf.setup();
        ColorScheme.setSystemAppearanceDark(dark);
        ink = dark ? DARK_INK : LIGHT_INK;
        muted = dark ? DARK_MUTED : LIGHT_MUTED;
        surface = dark ? DARK_SURFACE : LIGHT_SURFACE;
        toolbar = dark ? DARK_TOOLBAR : LIGHT_TOOLBAR;
        border = dark ? DARK_BORDER : LIGHT_BORDER;
        selection = dark ? DARK_SELECTION : LIGHT_SELECTION;
        tooltip = dark ? DARK_TOOLTIP : LIGHT_TOOLTIP;
        applyPaletteDefaults();
        if (!GraphicsEnvironment.isHeadless()) {
            FlatLaf.updateUI();
            for (Window window : Window.getWindows()) refreshAppearance(window);
        }
    }

    private static void applyPaletteDefaults() {
        UIManager.put("Panel.background", surface);
        UIManager.put("Label.foreground", ink);
        UIManager.put("Component.borderColor", border);
        UIManager.put("Tree.background", surface);
        UIManager.put("Tree.selectionBackground", selection);
        UIManager.put("Tree.selectionForeground", ink);
        UIManager.put("ToolTip.background", tooltip);
    }

    private static void refreshAppearance(Component component) {
        if (component instanceof JToolBar bar) {
            bar.setBackground(toolbar);
            for (Component child : bar.getComponents()) styleToolbarChild(child);
        } else if (component instanceof JPanel panel
                && Boolean.TRUE.equals(panel.getClientProperty("modern.sidebar"))) {
            boolean nativeVibrancy = Vibrancy.isAvailable();
            panel.setBackground(nativeVibrancy ? new Color(0, 0, 0, 0) : surface);
        } else if (component instanceof JLabel label
                && Boolean.TRUE.equals(label.getClientProperty("modern.brand"))) {
            label.setForeground(ink);
        } else if (component instanceof JLabel label
                && Boolean.TRUE.equals(label.getClientProperty("modern.muted"))) {
            label.setForeground(muted);
        } else if (component instanceof CircuitComponent canvas) {
            canvas.graphicHasChanged();
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) refreshAppearance(child);
        }
    }

    /** True when the current OS appearance resolves to the dark palette. */
    public static boolean isDarkAppearance() {
        return darkAppearance;
    }

    /** Colors used by the sidebar renderer, which paints outside FlatLaf UI delegates. */
    public static Color sidebarRowText(boolean selected) {
        if (selected) return darkAppearance ? new Color(0xF8FAFC) : new Color(0x1F2937);
        return ink;
    }

    public static Color sidebarHoverFill() {
        return darkAppearance ? new Color(0x343438) : new Color(0xFF, 0xFF, 0xFF, 220);
    }

    public static Color sidebarHoverShadow(int alpha) {
        return darkAppearance ? new Color(0, 0, 0, alpha) : new Color(0, 0, 0, alpha);
    }

    /** Apply the shared modern surface rules to a complete window. */
    public static void styleWindow(Window window) {
        if (!(window instanceof RootPaneContainer rootContainer)) return;
        JRootPane root = rootContainer.getRootPane();
        if (Boolean.TRUE.equals(root.getClientProperty(STYLED))) return;
        root.putClientProperty(STYLED, true);
        // Keep native window controls and title outside the Swing toolbar.
        root.putClientProperty("apple.awt.transparentTitleBar", false);
        root.putClientProperty("apple.awt.windowTitleVisible", true);
        root.putClientProperty("apple.awt.fullWindowContent", false);
        if (window instanceof Dialog) {
            root.setBorder(new EmptyBorder(14, 16, 16, 16));
            window.setBackground(surface);
        }
        styleComponentTree(rootContainer.getContentPane());
        JButton primary = root.getDefaultButton();
        if (primary != null) {
            primary.putClientProperty("modern.primary", true);
            primary.putClientProperty("JButton.buttonType", "roundRect");
        }
        animateWindowIn(window, root);
    }

    /**
     * Styles an arbitrary component hierarchy. Public for headless smoke tests
     * and for embedders that construct Digital panels without a top-level AWT
     * window.
     */
    public static void styleComponentTree(Component component) {
        if (component == null) return;
        if (component instanceof JComponent jc
                && !Boolean.TRUE.equals(jc.getClientProperty(STYLED))) {
            jc.putClientProperty(STYLED, true);
            styleComponent(jc);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) styleComponentTree(child);
        }
    }

    private static void styleComponent(JComponent component) {
        if (component instanceof JButton button) {
            button.putClientProperty("JButton.buttonType", "roundRect");
            button.setRolloverEnabled(true);
            button.setFocusPainted(true);
            if (button.getMargin() == null || button.getMargin() instanceof UIResource)
                button.setMargin(new Insets(7, 14, 7, 14));
        } else if (component instanceof JToggleButton button) {
            button.putClientProperty("JButton.buttonType", "roundRect");
            button.setRolloverEnabled(true);
        } else if (component instanceof JTextComponent text) {
            text.putClientProperty("JComponent.roundRect", true);
            Insets margin = text.getMargin();
            if (margin == null || margin instanceof UIResource)
                text.setMargin(new Insets(6, 9, 6, 9));
        } else if (component instanceof JComboBox<?>) {
            component.putClientProperty("JComponent.roundRect", true);
        } else if (component instanceof JSpinner spinner) {
            spinner.putClientProperty("JComponent.roundRect", true);
        } else if (component instanceof JTable table) {
            table.setRowHeight(Math.max(30, table.getRowHeight()));
            table.setShowHorizontalLines(false);
            table.setShowVerticalLines(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setFillsViewportHeight(true);
        } else if (component instanceof JTableHeader header) {
            header.setReorderingAllowed(false);
        } else if (component instanceof JTree tree) {
            tree.setRowHeight(Math.max(30, tree.getRowHeight()));
            tree.putClientProperty("JTree.wideSelection", true);
        } else if (component instanceof JTabbedPane tabs) {
            tabs.putClientProperty("JTabbedPane.tabType", "card");
            tabs.putClientProperty("JTabbedPane.showTabSeparators", false);
            tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        } else if (component instanceof JScrollPane scroll) {
            if (scroll.getBorder() == null || scroll.getBorder() instanceof UIResource)
                scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
            if (scroll.getVerticalScrollBar() != null)
                scroll.getVerticalScrollBar().setUnitIncrement(18);
            if (scroll.getHorizontalScrollBar() != null)
                scroll.getHorizontalScrollBar().setUnitIncrement(18);
        } else if (component instanceof JSplitPane split) {
            split.setContinuousLayout(true);
            split.setDividerSize(Math.max(1, split.getDividerSize()));
            // Don't fill the split's own background — otherwise it paints an
            // opaque band over the native vibrancy effect view underneath.
            split.setOpaque(false);
        } else if (component instanceof JProgressBar progress) {
            progress.putClientProperty("JProgressBar.largeHeight", false);
            progress.setBorderPainted(false);
        } else if (component instanceof JPanel panel) {
            if (panel.getBackground() instanceof UIResource) panel.setBackground(surface);
            if (panel.getBorder() instanceof TitledBorder titled) {
                titled.setTitleColor(muted);
                titled.setTitleFont(panel.getFont().deriveFont(Font.BOLD));
            }
        }
    }

    private static void animateWindowIn(Window window, JRootPane root) {
        if (reduceMotion || GraphicsEnvironment.isHeadless() || !window.isShowing()
                || Boolean.TRUE.equals(root.getClientProperty("modern.animatedIn"))) return;
        // Repeated setOpacity() calls on decorated macOS windows emit native
        // window-ordering warnings and can make accessibility inspection time
        // out. macOS still gets the stable sidebar, tree and hover animations.
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) return;
        root.putClientProperty("modern.animatedIn", true);
        GraphicsDevice device = window.getGraphicsConfiguration().getDevice();
        if (!device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) return;
        try {
            window.setOpacity(0f);
            long started = System.nanoTime();
            Timer timer = new Timer(1000 / 60, null);
            timer.addActionListener(event -> {
                float t = Math.min(1f, (System.nanoTime() - started) / 180_000_000f);
                float eased = 1f - (1f - t) * (1f - t) * (1f - t);
                try { window.setOpacity(eased); } catch (RuntimeException ignored) { timer.stop(); }
                if (t >= 1f || !window.isDisplayable()) timer.stop();
            });
            timer.start();
        } catch (RuntimeException ignored) {
            // Some window managers expose translucency but reject decorated
            // windows. Styling remains fully functional without the fade.
        }
    }

    /** Shared quality preset for charts, editors and generated component art. */
    public static void applyQualityRenderingHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    public static Icon legacyIcon(String filename) {
        String key = LEGACY.get(filename);
        return key == null || iconFont == null ? null : icon(key);
    }

    /** Scale the original circuit symbol uniformly into the library row. */
    public static Icon componentIcon(Icon source) {
        if (source == null) return null;
        return new Icon() {
            public int getIconWidth() { return 30; }
            public int getIconHeight() { return 26; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                double scale = Math.min(26.0 / source.getIconWidth(), 24.0 / source.getIconHeight());
                g.translate(x + (30 - source.getIconWidth() * scale) / 2,
                            y + (26 - source.getIconHeight() * scale) / 2);
                g.scale(scale, scale);
                applyQualityRenderingHints(g);
                source.paintIcon(c, g, 0, 0);
                g.dispose();
            }
        };
    }

    public static Icon icon(String name) {
        final String code = GLYPHS.getProperty(name);
        if (code == null || iconFont == null) throw new IllegalArgumentException("Missing icon: " + name);
        final String glyph = String.valueOf((char) Integer.parseInt(code, 16));
        return new Icon() {
            public int getIconWidth() { return 20; }
            public int getIconHeight() { return 20; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                applyQualityRenderingHints(g);
                g.setFont(iconFont);
                g.setColor(c == null ? ink : (c.isEnabled() ? c.getForeground() : new Color(0xB8BABD)));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(glyph, x + (20 - fm.stringWidth(glyph)) / 2, y + (20 - fm.getHeight()) / 2 + fm.getAscent());
                g.dispose();
            }
        };
    }

    public static void decorate(JFrame frame, JToolBar bar, JLabel status) {
        frame.getRootPane().putClientProperty("apple.awt.transparentTitleBar", false);
        frame.getRootPane().putClientProperty("apple.awt.windowTitleVisible", true);
        frame.getRootPane().putClientProperty("apple.awt.fullWindowContent", false);
        // Leave the window's content area transparent so the native vibrancy
        // window positioned below it can be seen through the sidebar.
        frame.getRootPane().setOpaque(false);
        frame.getLayeredPane().setOpaque(false);
        frame.getLayeredPane().setBackground(new Color(0, 0, 0, 0));
        if (frame.getContentPane() instanceof JComponent contentPane) {
            contentPane.setOpaque(false);
            contentPane.setBackground(new Color(0, 0, 0, 0));
        }
        bar.setFloatable(false);
        bar.setOpaque(true);
        bar.putClientProperty("modern.toolbar", true);
        bar.setBackground(toolbar);
        bar.setBorder(new EmptyBorder(10, 12, 10, 12));
        for (Component c : bar.getComponents()) styleToolbarChild(c);
        bar.addContainerListener(new ContainerAdapter() {
            @Override public void componentAdded(ContainerEvent e) { styleToolbarChild(e.getChild()); }
        });
        JLabel brand = new JLabel("Digital");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 16f));
        brand.putClientProperty("modern.brand", true);
        brand.setForeground(ink);
        brand.setBorder(new EmptyBorder(0, 8, 0, 22));
        bar.add(brand, 1);
        bar.add(Box.createHorizontalGlue());
        JToggleButton motion = new JToggleButton(icon("accessibility"), reduceMotion);
        motion.setToolTipText(words("减少动态效果（切换）", "Toggle reduced motion"));
        motion.getAccessibleContext().setAccessibleName(words("减少动态效果", "Reduce motion"));
        motion.addActionListener(e -> {
            reduceMotion = motion.isSelected();
            java.util.prefs.Preferences.userNodeForPackage(ModernUI.class).putBoolean("reduceMotion", reduceMotion);
        });
        bar.add(motion);
        status.setBorder(new EmptyBorder(7, 18, 8, 18));
        status.setFont(status.getFont().deriveFont(11f));
        status.putClientProperty("modern.muted", true);
        status.setForeground(muted);
        status.setPreferredSize(new Dimension(100, 32));
        status.setText(words("就绪   ·   选择组件开始搭建电路", "Ready   ·   Choose a component to start building"));
    }

    /**
     * Makes the canvas fill the workspace while keeping status text available
     * as a small, non-blocking overlay at the bottom-left.
     */
    public static JComponent createCanvasWorkspace(JComponent canvas, JLabel status) {
        JPanel workspace = new JPanel(null) {
            @Override
            public void doLayout() {
                canvas.setBounds(0, 0, getWidth(), getHeight());
                Dimension preferred = status.getPreferredSize();
                int width = Math.min(getWidth(), Math.max(1, preferred.width));
                int height = Math.min(getHeight(), Math.max(1, preferred.height));
                status.setBounds(0, getHeight() - height, width, height);
            }
        };
        workspace.setOpaque(false);
        status.setOpaque(false);
        workspace.add(canvas);
        workspace.add(status);
        return workspace;
    }

    private static void styleToolbarChild(Component c) {
        if (c instanceof AbstractButton) {
            AbstractButton b = (AbstractButton) c;
            b.setUI(new MotionButtonUI());
            b.setMargin(new Insets(8, 9, 8, 9));
            b.setBorder(new EmptyBorder(8, 9, 8, 9));
            boolean primary = Boolean.TRUE.equals(b.getClientProperty("modern.primary"));
            b.setForeground(primary ? primaryText() : ink);
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setRolloverEnabled(true);
            // Toolbar controls should not retain a large keyboard-focus ring
            // after a mouse click; the hover/pressed treatment is sufficient.
            b.setFocusPainted(false);
            int width = primary ? 108 : 38;
            b.setPreferredSize(new Dimension(width, 36));
            b.setMinimumSize(new Dimension(width, 36));
            b.setMaximumSize(new Dimension(width, 36));
            if (b.getAccessibleContext().getAccessibleName() == null)
                b.getAccessibleContext().setAccessibleName(b.getToolTipText());
        } else if (c instanceof JToolBar.Separator) {
            ((JToolBar.Separator) c).setSeparatorSize(new Dimension(14, 24));
        }
    }

    public static void decorateSidebar(JPanel panel, JPanel field, JTextField search, JButton clear, JTree tree) {
        panel.setMinimumSize(new Dimension(0, 0));
        panel.setPreferredSize(new Dimension(252, 500));
        panel.setBorder(new EmptyBorder(20, 14, 12, 14));
        // Use one backing surface for the whole sidebar. Mixing an opaque
        // Swing surface with a native vibrancy window makes the scroll area
        // reveal the native material as a separate gray rectangle.
        boolean nativeVibrancy = Vibrancy.isAvailable();
        Color sidebarBackground = nativeVibrancy
                ? new Color(0, 0, 0, 0)
                : surface;
        panel.putClientProperty("modern.translucentSurface", true);
        panel.putClientProperty("modern.sidebar", true);
        panel.setOpaque(!nativeVibrancy);
        panel.setBackground(sidebarBackground);
        field.setOpaque(false);
        search.setBorder(UIManager.getBorder("TextField.border"));
        search.putClientProperty("JTextField.placeholderText", words("搜索组件…", "Search components…"));
        search.putClientProperty("JTextField.leadingIcon", icon("search"));
        search.getAccessibleContext().setAccessibleName(words("搜索组件", "Search components"));
        search.setPreferredSize(new Dimension(160, 36));
        clear.setText(null);
        clear.setIcon(icon("x"));
        clear.setToolTipText(words("清除搜索", "Clear search"));
        clear.getAccessibleContext().setAccessibleName(words("清除搜索", "Clear search"));
        styleToolbarChild(clear);
        field.remove(clear);
        search.putClientProperty("JTextField.showClearButton", true);
        JPanel heading = new JPanel(new BorderLayout(0, 14));
        heading.setOpaque(false);
        JLabel title = new JLabel(words("组件库", "Components"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setForeground(ink);
        heading.add(title, BorderLayout.NORTH);
        heading.add(field, BorderLayout.CENTER);
        heading.setBorder(new EmptyBorder(0, 2, 18, 2));
        panel.add(heading, BorderLayout.NORTH);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(false);
        tree.setRowHeight(34);
        tree.setBorder(new EmptyBorder(0, 0, 12, 0));
        tree.putClientProperty("JTree.wideSelection", true);
        // Keep the tree transparent so selected rows can paint their own
        // highlight. The viewport itself must follow the same backing mode as
        // the parent; otherwise FlatLaf paints a solid block behind the rows.
        tree.setOpaque(false);
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tree);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.setOpaque(!nativeVibrancy);
        scroll.setBackground(sidebarBackground);
        scroll.getViewport().setOpaque(!nativeVibrancy);
        scroll.getViewport().setBackground(sidebarBackground);
        JLabel hint = new JLabel(words("选择组件，然后单击画布放置", "Select a component, then place it"));
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.putClientProperty("modern.muted", true);
        hint.setForeground(muted);
        hint.setBorder(new EmptyBorder(14, 2, 0, 0));
        panel.add(hint, BorderLayout.SOUTH);
        // Layer the native vibrancy effect underneath the panel. No-op on
        // non-macOS, when the dylib is missing, or when the panel is not
        // yet on screen.
        Vibrancy.apply(panel, Vibrancy.STYLE_SIDEBAR, 0.0);
    }

    /** Used by animated Swing controls to honor the global accessibility toggle. */
    public static boolean isReduceMotion() {
        return reduceMotion;
    }

    public static void setSidebarVisible(JSplitPane split, boolean visible) {
        Timer old = (Timer) split.getClientProperty("modern.animation");
        if (old != null) old.stop();

        // When showing, the left component must be visible *before* the divider
        // animates out from 0, otherwise it won't paint at all and the user
        // sees a sliding empty strip.
        if (visible) split.getLeftComponent().setVisible(true);

        // Read the current divider location as the start of the animation.
        // For a freshly-built split (first click), JSplitPane reports 0 even
        // when we just called setDividerLocation(252) in Main; guard with the
        // "last visible width" we cached on the previous hide.
        int from = Math.max(0, split.getDividerLocation());
        if (!visible && from <= 0) {
            Object last = split.getClientProperty("modern.sidebarWidth");
            if (last instanceof Integer) from = (Integer) last;
        }
        if (!visible && from > 20) split.putClientProperty("modern.sidebarWidth", from);

        Object saved = split.getClientProperty("modern.sidebarWidth");
        int desired = saved instanceof Integer ? (Integer) saved : 252;
        int to = visible ? Math.min(desired, Math.max(220, split.getWidth() - 480)) : 0;

        if (reduceMotion || !split.isShowing()) {
            split.setDividerLocation(to);
            split.getLeftComponent().setVisible(visible);
            return;
        }
        final long start = System.nanoTime();
        final int fromFinal = from;
        Timer timer = new Timer(1000 / 60, null);
        timer.addActionListener(e -> {
            double t = Math.min(1, (System.nanoTime() - start) / 220_000_000.0);
            double ease = 1 - Math.pow(1 - t, 3);
            split.setDividerLocation((int) Math.round(fromFinal + (to - fromFinal) * ease));
            if (t >= 1 || !split.isDisplayable()) {
                timer.stop();
                // Only hide the left component *after* the hide animation
                // finishes so the user doesn't see content snap away at the
                // very first frame.
                if (!visible) split.getLeftComponent().setVisible(false);
                split.putClientProperty("modern.animation", null);
            }
        });
        split.putClientProperty("modern.animation", timer);
        timer.start();
    }

    private static String words(String chinese, String english) {
        return java.util.Locale.getDefault().getLanguage().equals("zh") ? chinese : english;
    }

    /** A bounded hover transition. No timer runs while a control is idle. */
    private static final class MotionButtonUI extends BasicButtonUI {
        private Timer timer;
        private float amount;
        private javax.swing.event.ChangeListener listener;
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            listener = e -> {
                float to = b.isEnabled() && (b.getModel().isRollover() || b.getModel().isPressed()) ? 1 : 0;
                if (timer != null) timer.stop();
                if (reduceMotion) { amount = to; b.repaint(); return; }
                float from = amount;
                long start = System.nanoTime();
                timer = new Timer(16, event -> {
                    float t = Math.min(1f, (System.nanoTime() - start) / 140_000_000f);
                    amount = from + (to - from) * (1 - (1 - t) * (1 - t));
                    b.repaint();
                    if (t == 1 || !b.isDisplayable()) ((Timer) event.getSource()).stop();
                });
                timer.start();
            };
            b.getModel().addChangeListener(listener);
        }
        @Override public void uninstallUI(JComponent c) {
            if (timer != null) timer.stop();
            ((AbstractButton) c).getModel().removeChangeListener(listener);
            super.uninstallUI(c);
        }
        @Override public void paint(Graphics graphics, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int inset = b.getModel().isPressed() ? 2 : 1;
            boolean primary = Boolean.TRUE.equals(b.getClientProperty("modern.primary"));
            if (primary) {
                Color fill = primaryBackground();
                if (b.isEnabled() && amount > 0) fill = blend(fill, darkAppearance ? Color.WHITE : new Color(0x4A4C50), amount * .12f);
                g.setColor(b.isEnabled() ? fill : disabledPrimaryBackground());
            } else {
                // Idle state: paint a faint, fully-opaque card so every toolbar
                // button reads as a rounded rectangle (matching the primary
                // "start simulation" control). Hover/pressed/selected layers
                // stack on top of it instead of replacing it.
                Color overlay = darkAppearance ? Color.WHITE : new Color(34, 36, 40);
                int baseAlpha = darkAppearance ? 22 : 18;
                int alpha = b.isSelected() ? Math.max(baseAlpha, 36)
                        : baseAlpha + (int) (amount * (b.getModel().isPressed() ? 28 : 16));
                g.setColor(new Color(overlay.getRed(), overlay.getGreen(), overlay.getBlue(), alpha));
            }
            g.fillRoundRect(inset, inset, c.getWidth() - inset * 2, c.getHeight() - inset * 2, 12, 12);
            if (b.hasFocus()) {
                g.setColor(new Color(0x4285E8));
                g.setStroke(new BasicStroke(2));
                g.drawRoundRect(2, 2, c.getWidth() - 5, c.getHeight() - 5, 12, 12);
            }
            g.dispose();
            super.paint(graphics, c);
        }
    }

    private static Color primaryBackground() {
        return darkAppearance ? new Color(0x0A84FF) : new Color(0x232427);
    }

    private static Color disabledPrimaryBackground() {
        return darkAppearance ? new Color(0x454548) : new Color(0xE4E5E7);
    }

    private static Color primaryText() {
        return Color.WHITE;
    }

    private static Color blend(Color from, Color to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }
}
