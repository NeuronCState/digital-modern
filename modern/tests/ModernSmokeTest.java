import de.neemann.digital.gui.modern.ModernUI;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.shapes.ShapeFactory;
import de.neemann.digital.gui.components.tree.LibraryTreeModel;
import de.neemann.digital.gui.TextSearchFilter;
import de.neemann.digital.testing.TestExecutor;
import de.neemann.digital.testing.TestResult;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class ModernSmokeTest {
    public static void main(String[] args) throws Exception {
        ModernUI.install();
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics g = image.getGraphics();
            // Native macOS menus render icons with a null component.
            ModernUI.icon("play").paintIcon(null, g, 0, 0);
            JButton disabled = new JButton(); disabled.setEnabled(false);
            ModernUI.icon("save").paintIcon(disabled, g, 0, 0);
            g.dispose();
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
        System.out.println("PASS: native menu icons, disabled icons, sidebar state, search, " + suites + " circuit test suites");
    }
}
