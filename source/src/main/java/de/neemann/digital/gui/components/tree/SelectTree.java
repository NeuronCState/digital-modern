/*
 * Copyright (c) 2017 Helmut Neemann
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components.tree;

import de.neemann.digital.core.element.ElementTypeDescription;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.library.LibraryNode;
import de.neemann.digital.draw.shapes.ShapeFactory;
import de.neemann.digital.gui.InsertAction;
import de.neemann.digital.gui.InsertHistory;
import de.neemann.digital.gui.components.CircuitComponent;
import de.neemann.digital.lang.Lang;
import de.neemann.gui.ErrorMessage;
import com.formdev.flatlaf.ui.FlatTreeUI;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tree to select items
 */
public class SelectTree extends JTree {

    private static final int EXPANSION_ANIMATION_MS = 170;
    private static final int ANIMATION_FRAME_MS = 8;
    private final ShapeFactory shapeFactory;
    private Enumeration<TreePath> storedExpanded;
    private TreeAnimation treeAnimation;
    private Timer treeAnimationTimer;
    private boolean suppressTreeAnimation;
    private int hoverRow = -1;

    /**
     * Create a new instance
     *
     * @param model         the model to use
     * @param component     the component to insert the components to
     * @param shapeFactory  the shape factory
     * @param insertHistory the insert history
     */
    public SelectTree(LibraryTreeModel model, CircuitComponent component, ShapeFactory shapeFactory, InsertHistory insertHistory) {
        super(model);
        this.shapeFactory = shapeFactory;
        setSelectionModel(null);
        setUI(new FlatTreeUI() {
            @Override
            protected boolean shouldPaintExpandControl(TreePath path, int row,
                                                       boolean expanded, boolean leaf,
                                                       boolean hasFocus) {
                return false;
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() != 1
                        || !SwingUtilities.isLeftMouseButton(mouseEvent)) return;
                int row = rowForY(mouseEvent.getY());
                TreePath path = row < 0 ? null : getPathForRow(row);
                if (path != null && path.getPathCount() > 0) {
                    LibraryNode node = (LibraryNode) path.getLastPathComponent();
                    if (node.isLeaf() && node.isUnique()) {
                        try {
                            ElementTypeDescription d = node.getDescription();
                            final VisualElement element = node.setWideShapeFlagTo(new VisualElement(d.getName()).setShapeFactory(shapeFactory));
                            component.setPartToInsert(element);
                            insertHistory.add(new InsertAction(node, insertHistory, component, shapeFactory));
                        } catch (IOException e) {
                            SwingUtilities.invokeLater(new ErrorMessage(Lang.get("msg_errorImportingModel_N0", node.getName())).addCause(e));
                        }
                    }
                }
            }
        });
        setCellRenderer(new MyCellRenderer());
        setToolTipText("");
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHoverRow(event);
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent event) {
                setHoverRow(-1);
            }
        });

        // open first child
        expandPath(new TreePath(model.getFirstLeafParent().getPath()));
    }

    /**
     * Handle disclosure rows before FlatTreeUI sees the event.  On macOS the
     * UI delegate may consume a press while doing its own disclosure hit
     * testing; an ordinary MouseListener therefore sees hover/move events but
     * does not reliably get an actionable click.  Intercepting at the
     * component event boundary also prevents the UI delegate and our custom
     * full-row hit target from toggling the same path twice.
     */
    @Override
    protected void processMouseEvent(MouseEvent event) {
        if (event.getID() == MouseEvent.MOUSE_PRESSED
                && SwingUtilities.isLeftMouseButton(event)) {
            int row = rowForY(event.getY());
            TreePath path = row < 0 ? null : getPathForRow(row);
            if (path != null && path.getPathCount() > 0) {
                LibraryNode node = (LibraryNode) path.getLastPathComponent();
                if (!node.isLeaf()) {
                    // Every press is a toggle, including the second and later
                    // presses of a rapid multi-click gesture. During collapse
                    // animation the Swing model is deliberately kept expanded
                    // for painting, so use the animation's destination as the
                    // logical state instead of reading that temporary model.
                    setExpandedState(path, !effectiveExpandedState(path));
                    event.consume();
                    return;
                }
            }
        }
        super.processMouseEvent(event);
    }

    private boolean effectiveExpandedState(TreePath path) {
        TreeAnimation animation = treeAnimation;
        if (animation != null && animation.path.equals(path))
            return animation.expanding;
        return isExpanded(path);
    }

    /**
     * Sets a new model to this SelectTree.
     *
     * @param newModel the new model
     */
    public void setModel(LibraryTreeModel newModel) {
        LibraryTreeModel oldModel = (LibraryTreeModel) getModel();
        if (!oldModel.isFiltered() && newModel.isFiltered())
            storedExpanded = getExpandedDescendants(new TreePath(getModel().getRoot()));

        oldModel.close();

        boolean restore = oldModel.isFiltered() && !newModel.isFiltered();
        suppressTreeAnimation = true;
        try {
            super.setModel(newModel);
        } finally {
            suppressTreeAnimation = false;
        }
        if (restore && storedExpanded != null) {
            while (storedExpanded.hasMoreElements())
                expandPath(storedExpanded.nextElement());
            storedExpanded = null;
        } else
            expandPath(new TreePath(newModel.getFirstLeafParent().getPath()));
    }

    /**
     * Animate every branch opened by the standard JTree UI. Overriding this
     * method means mouse clicks, keyboard arrows, and accessibility actions
     * all get the same behavior.
     */
    @Override
    public void setExpandedState(TreePath path, boolean state) {
        TreeAnimation running = treeAnimation;
        if (running != null && running.path.equals(path)
                && isShowing()
                && !de.neemann.digital.gui.modern.ModernUI.isReduceMotion()) {
            if (running.expanding != state) reverseTreeAnimation(running);
            return;
        }
        finishTreeAnimation();
        boolean wasExpanded = isExpanded(path);
        if (wasExpanded == state || suppressTreeAnimation || !isShowing()
                || de.neemann.digital.gui.modern.ModernUI.isReduceMotion()) {
            super.setExpandedState(path, state);
            return;
        }

        TreeSnapshot before = captureSnapshot();
        if (before == null || !before.rows.containsKey(path)) {
            super.setExpandedState(path, state);
            return;
        }

        if (state) {
            super.setExpandedState(path, true);
            // JTree updates its row mapper synchronously. Capture the expanded
            // layout in this event cycle instead of posting invokeLater: a
            // rapid third press could otherwise start a collapse and then be
            // overwritten by the stale delayed expansion callback.
            revalidate();
            TreeSnapshot after = captureSnapshot();
            if (after != null && after.rows.size() > before.rows.size())
                startTreeAnimation(new TreeAnimation(path, true, before, after));
            else
                repaint();
        } else {
            if (getRowCount() <= 1) {
                super.setExpandedState(path, false);
                return;
            }

            // Capture the collapsed layout, then restore the expanded model
            // while the rows animate into that layout.
            suppressTreeAnimation = true;
            try {
                super.setExpandedState(path, false);
            } finally {
                suppressTreeAnimation = false;
            }
            revalidate();
            TreeSnapshot after = captureSnapshot();
            suppressTreeAnimation = true;
            try {
                super.setExpandedState(path, true);
            } finally {
                suppressTreeAnimation = false;
            }
            revalidate();
            if (after != null && after.rows.size() < before.rows.size())
                startTreeAnimation(new TreeAnimation(path, false, before, after));
            else
                finishTreeAnimation();
        }
    }

    private void startTreeAnimation(TreeAnimation animation) {
        treeAnimation = animation;
        animation.startedNanos = System.nanoTime();
        treeAnimationTimer = new Timer(ANIMATION_FRAME_MS, event -> {
            double t = Math.min(1.0, (System.nanoTime() - animation.startedNanos)
                    / (EXPANSION_ANIMATION_MS * 1_000_000.0));
            animation.progress = t;
            repaint();
            if (t >= 1.0 || !isDisplayable()) {
                finishTreeAnimation();
            }
        });
        treeAnimationTimer.setInitialDelay(0);
        treeAnimationTimer.start();
    }

    /** Reverse an in-flight disclosure animation without snapping to an end. */
    private void reverseTreeAnimation(TreeAnimation animation) {
        long now = System.nanoTime();
        double current = Math.max(0.0, Math.min(1.0,
                (now - animation.startedNanos) / (EXPANSION_ANIMATION_MS * 1_000_000.0)));
        TreeSnapshot oldBefore = animation.before;
        animation.before = animation.after;
        animation.after = oldBefore;
        animation.expanding = !animation.expanding;
        animation.progress = 1.0 - current;
        animation.startedNanos = now - (long) (animation.progress
                * EXPANSION_ANIMATION_MS * 1_000_000.0);
        repaint();
    }

    private void finishTreeAnimation() {
        if (treeAnimationTimer != null) {
            treeAnimationTimer.stop();
            treeAnimationTimer = null;
        }
        TreeAnimation finished = treeAnimation;
        treeAnimation = null;
        if (finished != null && !finished.expanding) {
            suppressTreeAnimation = true;
            try {
                super.setExpandedState(finished.path, false);
            } finally {
                suppressTreeAnimation = false;
            }
        }
        if (finished != null) {
            revalidate();
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        TreeAnimation animation = treeAnimation;
        if (animation == null) {
            Graphics2D background = (Graphics2D) graphics.create();
            paintHoverButton(background);
            background.dispose();
            super.paintComponent(graphics);
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        paintMovingRows(g, animation);
        g.dispose();
    }

    private void paintMovingRows(Graphics2D g, TreeAnimation animation) {
        TreeSnapshot collapsed = animation.expanding ? animation.before : animation.after;
        TreeSnapshot expanded = animation.expanding ? animation.after : animation.before;
        Rectangle parent = expanded.rows.get(animation.path);
        if (parent == null) parent = collapsed.rows.get(animation.path);
        if (parent == null) {
            super.paintComponent(g);
            return;
        }

        int width = Math.min(getWidth(), Math.min(collapsed.image.getWidth(), expanded.image.getWidth()));
        int height = Math.min(getHeight(), Math.min(collapsed.image.getHeight(), expanded.image.getHeight()));
        int parentBottom = parent.y + parent.height;
        int childrenBottom = parentBottom;
        for (Map.Entry<TreePath, Rectangle> entry : expanded.rows.entrySet()) {
            TreePath path = entry.getKey();
            if (!animation.path.equals(path) && animation.path.isDescendant(path))
                childrenBottom = Math.max(childrenBottom, entry.getValue().y + entry.getValue().height);
        }
        int childrenHeight = childrenBottom - parentBottom;
        if (childrenHeight <= 0 || width <= 0 || height <= 0) {
            super.paintComponent(g);
            return;
        }

        // ChatGPT-style disclosure: the child list lives underneath the
        // parent row inside a growing clipped viewport. Rows following the
        // group move as one solid sheet, so children never float above them.
        double local = animation.progress;
        double eased = local * local * (3.0 - 2.0 * local);
        double revealed = animation.expanding ? eased : 1.0 - eased;
        int revealedHeight = (int) Math.round(childrenHeight * revealed);
        int childLift = (int) Math.round(-Math.min(10, childrenHeight) * (1.0 - revealed));

        g.setColor(getBackground());
        g.fillRect(0, 0, width, height);

        Shape originalClip = g.getClip();
        g.clip(new Rectangle(0, parentBottom, width, revealedHeight));
        g.drawImage(expanded.image,
                0, parentBottom + childLift, width, childrenBottom + childLift,
                0, parentBottom, width, childrenBottom, null);
        g.setClip(originalClip);

        int tailHeight = height - parentBottom;
        if (tailHeight > 0) {
            g.drawImage(collapsed.image,
                    0, parentBottom + revealedHeight,
                    width, parentBottom + revealedHeight + tailHeight,
                    0, parentBottom, width, height, null);
        }

        // Paint the parent and everything above it last. This is the masking
        // layer that makes children slide out from under the disclosure row.
        g.drawImage(collapsed.image,
                0, 0, width, parentBottom,
                0, 0, width, parentBottom, null);
    }

    private TreeSnapshot captureSnapshot() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return null;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // FlatTreeUI expects the same non-null clip a normal RepaintManager
        // paint supplies. Snapshot rendering is offscreen, so establish it
        // explicitly before asking the UI delegate to paint.
        g.setClip(0, 0, width, height);
        super.paintComponent(g);
        g.dispose();
        Map<TreePath, Rectangle> rows = new LinkedHashMap<>();
        for (int row = 0; row < getRowCount(); row++) {
            TreePath path = getPathForRow(row);
            Rectangle bounds = getRowBounds(row);
            if (path != null && bounds != null) rows.put(path, new Rectangle(bounds));
        }
        return new TreeSnapshot(image, rows);
    }

    private void paintHoverButton(Graphics2D g) {
        if (hoverRow < 0) return;
        Rectangle row = getRowBounds(hoverRow);
        if (row == null || getWidth() < 24 || row.height < 8) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        int x = 8;
        int y = row.y + 2;
        int w = getWidth() - 16;
        int h = row.height - 4;
        int arc = Math.min(16, h);
        // Keep the hover treatment flat: one quiet surface, no offset shadow.
        g.setColor(de.neemann.digital.gui.modern.ModernUI.sidebarHoverFill());
        g.fillRoundRect(x, y, w, h, arc, arc);
    }

    private static final class TreeAnimation {
        final TreePath path;
        boolean expanding;
        TreeSnapshot before;
        TreeSnapshot after;
        double progress;
        long startedNanos;

        TreeAnimation(TreePath path, boolean expanding, TreeSnapshot before, TreeSnapshot after) {
            this.path = path;
            this.expanding = expanding;
            this.before = before;
            this.after = after;
        }
    }

    private static final class TreeSnapshot {
        final BufferedImage image;
        final Map<TreePath, Rectangle> rows;

        TreeSnapshot(BufferedImage image, Map<TreePath, Rectangle> rows) {
            this.image = image;
            this.rows = rows;
        }
    }

    private void updateHoverRow(MouseEvent event) {
        setHoverRow(rowForY(event.getY()));
    }

    private int rowForY(int y) {
        for (int row = 0; row < getRowCount(); row++) {
            Rectangle bounds = getRowBounds(row);
            if (bounds != null && y >= bounds.y && y < bounds.y + bounds.height)
                return row;
        }
        return -1;
    }

    private void setHoverRow(int row) {
        if (hoverRow == row) return;
        hoverRow = row;
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        TreePath selPath = getPathForLocation(e.getX(), e.getY());
        if (selPath != null && selPath.getPathCount() > 0) {
            Object lp = selPath.getLastPathComponent();
            if (lp instanceof LibraryNode) {
                return ((LibraryNode) lp).getToolTipText();
            }
        }
        return null;
    }

    private class MyCellRenderer extends JPanel implements TreeCellRenderer {
        private final JLabel label = new JLabel();

        private MyCellRenderer() {
            super(new BorderLayout());
            setOpaque(false);
            label.setOpaque(false);
            label.setIconTextGap(8);
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {
            LibraryNode node = (LibraryNode) value;
            label.setText(node.getTranslatedName());
            label.setFont(tree.getFont());
            label.setForeground(de.neemann.digital.gui.modern.ModernUI.sidebarRowText(selected));
            if (leaf) {
                label.setIcon(de.neemann.digital.gui.modern.ModernUI
                        .componentIcon(node.getIconOrNull(shapeFactory)));
            } else {
                label.setIcon(de.neemann.digital.gui.modern.ModernUI.icon("folder"));
            }
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            return this;
        }
    }
}
