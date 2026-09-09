/* Digital Modern: desktop styling. Licensed under GPL-3.0 with Digital. */
package de.neemann.digital.gui.modern;

import com.formdev.flatlaf.themes.FlatMacLightLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Modern desktop chrome, leaving the circuit renderer and simulator intact. */
public final class ModernUI {
    private static final Color INK = new Color(0x252628);
    private static final Color MUTED = new Color(0x73767C);
    private static final Color SURFACE = new Color(0xF5F5F4);
    private static final Map<String, String> LEGACY = new HashMap<>();
    private static final Properties GLYPHS = new Properties();
    private static Font iconFont;
    private static boolean installed;
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
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Digital Modern");
        FlatMacLightLaf.setup();
        UIManager.put("defaultFont", new Font(".AppleSystemUIFont", Font.PLAIN, 13));
        UIManager.put("Panel.background", SURFACE);
        UIManager.put("Label.foreground", INK);
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 14);
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.focusColor", new Color(0x92B9F4));
        UIManager.put("Component.borderColor", new Color(0xDEDFE1));
        UIManager.put("Tree.background", SURFACE);
        UIManager.put("Tree.selectionBackground", new Color(0xE2E8F0));
        UIManager.put("Tree.selectionForeground", INK);
        UIManager.put("Tree.selectionArc", 10);
        UIManager.put("Tree.rowHeight", 32);
        UIManager.put("Tree.paintLines", false);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("SplitPaneDivider.gripDotCount", 0);
        UIManager.put("ToolTip.background", new Color(0xFFFFFF));
        UIManager.put("ToolTip.border", new EmptyBorder(8, 10, 8, 10));
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
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(iconFont);
                g.setColor(c == null ? INK : (c.isEnabled() ? c.getForeground() : new Color(0xB8BABD)));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(glyph, x + (20 - fm.stringWidth(glyph)) / 2, y + (20 - fm.getHeight()) / 2 + fm.getAscent());
                g.dispose();
            }
        };
    }

    public static void decorate(JFrame frame, JToolBar bar, JLabel status) {
        frame.getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        frame.getRootPane().putClientProperty("apple.awt.windowTitleVisible", true);
        frame.getContentPane().setBackground(SURFACE);
        bar.setFloatable(false);
        bar.setOpaque(true);
        bar.setBackground(new Color(0xFAFAF9));
        bar.setBorder(new EmptyBorder(10, 12, 10, 12));
        for (Component c : bar.getComponents()) styleToolbarChild(c);
        bar.addContainerListener(new ContainerAdapter() {
            @Override public void componentAdded(ContainerEvent e) { styleToolbarChild(e.getChild()); }
        });
        JLabel brand = new JLabel("Digital");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 16f));
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
        status.setForeground(MUTED);
        status.setPreferredSize(new Dimension(100, 32));
        status.setText(words("就绪   ·   选择组件开始搭建电路", "Ready   ·   Choose a component to start building"));
    }

    private static void styleToolbarChild(Component c) {
        if (c instanceof AbstractButton) {
            AbstractButton b = (AbstractButton) c;
            b.setUI(new MotionButtonUI());
            b.setMargin(new Insets(8, 9, 8, 9));
            b.setBorder(new EmptyBorder(8, 9, 8, 9));
            boolean primary = Boolean.TRUE.equals(b.getClientProperty("modern.primary"));
            b.setForeground(primary ? Color.WHITE : INK);
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setRolloverEnabled(true);
            b.setFocusPainted(true);
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
        heading.add(title, BorderLayout.NORTH);
        heading.add(field, BorderLayout.CENTER);
        heading.setBorder(new EmptyBorder(0, 2, 18, 2));
        panel.add(heading, BorderLayout.NORTH);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(34);
        tree.setBorder(new EmptyBorder(0, 0, 12, 0));
        tree.putClientProperty("JTree.wideSelection", true);
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tree);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.getViewport().setBackground(SURFACE);
        JLabel hint = new JLabel(words("选择组件，然后单击画布放置", "Select a component, then place it"));
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(MUTED);
        hint.setBorder(new EmptyBorder(14, 2, 0, 0));
        panel.add(hint, BorderLayout.SOUTH);
    }

    public static void setSidebarVisible(JSplitPane split, boolean visible) {
        Timer old = (Timer) split.getClientProperty("modern.animation");
        if (old != null) old.stop();
        if (visible) split.getLeftComponent().setVisible(true);
        int from = split.getDividerLocation();
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
        Timer timer = new Timer(1000 / 60, null);
        timer.addActionListener(e -> {
            double t = Math.min(1, (System.nanoTime() - start) / 220_000_000.0);
            double ease = 1 - Math.pow(1 - t, 3);
            split.setDividerLocation((int) Math.round(from + (to - from) * ease));
            if (t >= 1 || !split.isDisplayable()) {
                timer.stop();
                split.getLeftComponent().setVisible(visible);
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
            if (primary) g.setColor(b.isEnabled() ? new Color(35 + (int) (amount * 22), 36 + (int) (amount * 22), 38 + (int) (amount * 22)) : new Color(0xE4E5E7));
            else g.setColor(new Color(34, 36, 40, b.isSelected() ? 27 : (int) (amount * (b.getModel().isPressed() ? 27 : 15))));
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
}
