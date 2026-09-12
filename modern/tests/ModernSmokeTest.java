/*
 * Copyright (c) 2026 晨光
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
import de.neemann.digital.gui.modern.ModernUI;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.shapes.ShapeFactory;
import de.neemann.digital.gui.components.tree.LibraryTreeModel;
import de.neemann.digital.gui.components.tree.SelectTree;
import de.neemann.digital.gui.InsertHistory;
import de.neemann.digital.gui.components.CircuitComponent;
import de.neemann.digital.gui.TextSearchFilter;
import de.neemann.digital.testing.TestExecutor;
import de.neemann.digital.testing.TestResult;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.swing.tree.TreePath;

public class ModernSmokeTest {
    public static void main(String[] args) throws Exception {
        ModernUI.install();
        Color expectedSurface = ModernUI.isDarkAppearance()
                ? new Color(0x1C1C1E) : new Color(0xF5F5F4);
        if (!expectedSurface.equals(UIManager.getColor("Panel.background")))
            throw new AssertionError("Automatic appearance palette was not installed");
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics g = image.getGraphics();
            // Native macOS menus render icons with a null component.
            ModernUI.icon("play").paintIcon(null, g, 0, 0);
            JButton disabled = new JButton(); disabled.setEnabled(false);
            ModernUI.icon("save").paintIcon(disabled, g, 0, 0);
            g.dispose();

            JPanel dialogPage = new JPanel();
            JTextField field = new JTextField();
            JTable table = new JTable(2, 2);
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("One", new JPanel());
            JScrollPane partialScrollPane = new JScrollPane();
            partialScrollPane.setHorizontalScrollBar(null);
            dialogPage.add(field);
            dialogPage.add(new JScrollPane(table));
            dialogPage.add(partialScrollPane);
            dialogPage.add(tabs);
            ModernUI.styleComponentTree(dialogPage);
            if (!Boolean.TRUE.equals(field.getClientProperty("JComponent.roundRect")))
                throw new AssertionError("Text editor was not modernized");
            if (table.getRowHeight() < 30 || table.getShowHorizontalLines() || table.getShowVerticalLines())
                throw new AssertionError("Table page was not modernized");
            if (tabs.getTabLayoutPolicy() != JTabbedPane.SCROLL_TAB_LAYOUT)
                throw new AssertionError("Tabbed page was not modernized");

            BufferedImage qualityImage = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            Graphics2D qualityGraphics = qualityImage.createGraphics();
            ModernUI.applyQualityRenderingHints(qualityGraphics);
            if (qualityGraphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                    != RenderingHints.VALUE_ANTIALIAS_ON)
                throw new AssertionError("Quality rendering preset missing antialiasing");
            qualityGraphics.dispose();

            JPanel sidebar = new JPanel(), canvas = new JPanel();
            sidebar.setMinimumSize(new Dimension(0, 0));
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, canvas);
            split.setSize(1100,700); split.setDividerLocation(280);
            ModernUI.setSidebarVisible(split, false);
            if (sidebar.isVisible() || split.getDividerLocation() != 0) throw new AssertionError("Sidebar did not close");
            ModernUI.setSidebarVisible(split, true);
            if (!sidebar.isVisible() || split.getDividerLocation() != 280 || split.getRightComponent() != canvas)
                throw new AssertionError("Sidebar state or canvas identity lost");
        });
        ElementLibrary library = new ElementLibrary();
        LibraryTreeModel absent = new LibraryTreeModel(library, new TextSearchFilter("nonexistentcomponent928163"));
        if (absent.getChildCount(absent.getRoot()) != 0) throw new AssertionError("Empty search failed");
        absent.close();
        LibraryTreeModel found = new LibraryTreeModel(library, new TextSearchFilter("Xor"));
        if (found.getChildCount(found.getRoot()) == 0) throw new AssertionError("Xor search failed");
        found.close();
        LibraryTreeModel treeModel = new LibraryTreeModel(library);
        ShapeFactory shapeFactory = new ShapeFactory(library);
        CircuitComponent circuitComponent = new CircuitComponent(null, library, shapeFactory);
        SelectTree tree = new SelectTree(treeModel, circuitComponent, shapeFactory,
                new InsertHistory(new JToolBar(), library));
        tree.setSize(320, 520);
        tree.doLayout();
        TreePath branch = null;
        Rectangle branchBounds = null;
        for (int row = 0; row < tree.getRowCount(); row++) {
            TreePath path = tree.getPathForRow(row);
            if (path != null && !treeModel.isLeaf(path.getLastPathComponent())) {
                branch = path;
                branchBounds = tree.getRowBounds(row);
                break;
            }
        }
        if (branch == null || branchBounds == null) throw new AssertionError("No library folder row found");
        boolean expandedBeforeClick = tree.isExpanded(branch);
        TreePath clickedBranch = branch;
        Rectangle clickedBounds = branchBounds;
        SwingUtilities.invokeAndWait(() -> tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, clickedBounds.x + 8,
                clickedBounds.y + clickedBounds.height / 2, 1, false, MouseEvent.BUTTON1)));
        SwingUtilities.invokeAndWait(() -> { });
        if (tree.isExpanded(clickedBranch) == expandedBeforeClick)
            throw new AssertionError("Library folder click did not toggle expansion");
        SwingUtilities.invokeAndWait(() -> tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, tree.getWidth() - 8,
                clickedBounds.y + clickedBounds.height / 2, 2, false, MouseEvent.BUTTON1)));
        if (tree.isExpanded(clickedBranch) != expandedBeforeClick)
            throw new AssertionError("Second press of double-click did not restore expansion");
        SwingUtilities.invokeAndWait(() -> tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, clickedBounds.x + clickedBounds.width / 2,
                clickedBounds.y + clickedBounds.height / 2, 3, false, MouseEvent.BUTTON1)));
        if (tree.isExpanded(clickedBranch) == expandedBeforeClick)
            throw new AssertionError("Third press of multi-click did not toggle expansion");

        // Exercise the real timer-driven path as well. The regular smoke run
        // disables motion for deterministic rendering, so temporarily enable
        // it and make the component report that it is on screen.
        Field reduceMotion = ModernUI.class.getDeclaredField("reduceMotion");
        reduceMotion.setAccessible(true);
        boolean previousReduceMotion = reduceMotion.getBoolean(null);
        reduceMotion.setBoolean(null, false);
        try {
            SelectTree animatedTree = new SelectTree(treeModel, circuitComponent, shapeFactory,
                    new InsertHistory(new JToolBar(), library)) {
                @Override public boolean isShowing() { return true; }
                @Override public boolean isDisplayable() { return true; }
            };
            animatedTree.setSize(320, 520);
            animatedTree.doLayout();
            TreePath animatedBranch = null;
            Rectangle animatedBounds = null;
            for (int row = 0; row < animatedTree.getRowCount(); row++) {
                TreePath path = animatedTree.getPathForRow(row);
                if (path != null && !treeModel.isLeaf(path.getLastPathComponent())) {
                    animatedBranch = path;
                    animatedBounds = animatedTree.getRowBounds(row);
                    break;
                }
            }
            if (animatedBranch == null || animatedBounds == null)
                throw new AssertionError("No animated library folder row found");
            boolean animatedInitial = animatedTree.isExpanded(animatedBranch);
            for (int clickCount = 1; clickCount <= 3; clickCount++) {
                int count = clickCount;
                Rectangle bounds = animatedBounds;
                SwingUtilities.invokeAndWait(() -> animatedTree.dispatchEvent(new MouseEvent(animatedTree,
                        MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                        bounds.x + 8, bounds.y + bounds.height / 2, count, false, MouseEvent.BUTTON1)));
            }
            Thread.sleep(400);
            SwingUtilities.invokeAndWait(() -> { });
            if (animatedTree.isExpanded(animatedBranch) == animatedInitial)
                throw new AssertionError("Three rapid animated presses did not end at the toggled state");

            for (int clickCount = 1; clickCount <= 2; clickCount++) {
                int count = clickCount;
                Rectangle bounds = animatedBounds;
                SwingUtilities.invokeAndWait(() -> animatedTree.dispatchEvent(new MouseEvent(animatedTree,
                        MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                        bounds.x + 8, bounds.y + bounds.height / 2, count, false, MouseEvent.BUTTON1)));
            }
            Thread.sleep(400);
            SwingUtilities.invokeAndWait(() -> { });
            if (animatedTree.isExpanded(animatedBranch) == animatedInitial)
                throw new AssertionError("Two more rapid animated presses did not preserve the toggled state");
        } finally {
            reduceMotion.setBoolean(null, previousReduceMotion);
        }
        treeModel.close();
        int suites = 0;
        for (String name : new String[]{"FullAdder", "HalfAdder", "FullSub", "Xor1"}) {
            File file = new File("examples/combinatorial/" + name + ".dig");
            library.setRootFilePath(file.getAbsoluteFile().getParentFile());
            Circuit circuit = Circuit.loadCircuit(file, new ShapeFactory(library));
            for (Circuit.TestCase tc : circuit.getTestCases()) {
                TestResult result = new TestExecutor(tc, circuit, library).execute();
                if (!result.allPassed()) throw new AssertionError("Circuit failed: " + name);
                suites++;
            }
        }
        if (suites == 0) throw new AssertionError("No circuit tests ran");
        System.out.println("PASS: automatic " + (ModernUI.isDarkAppearance() ? "dark" : "light")
                + " appearance, global pages, antialiasing, native menu icons, disabled icons, sidebar state, search, "
                + suites + " circuit test suites");
    }
}
