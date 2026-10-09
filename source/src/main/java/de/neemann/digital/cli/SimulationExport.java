/*
 * Copyright (c) 2026 Digital contributors.
 * Use of this source code is governed by the GPL v3 license.
 */
package de.neemann.digital.cli;

import de.neemann.digital.cli.cli.Argument;
import de.neemann.digital.cli.cli.BasicCommand;
import de.neemann.digital.cli.cli.CLIException;
import de.neemann.digital.core.Model;
import de.neemann.digital.core.Signal;
import de.neemann.digital.core.element.Element;
import de.neemann.digital.core.element.ElementAttributes;
import de.neemann.digital.core.io.Button;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.graphics.Export;
import de.neemann.digital.draw.graphics.GraphicSVG;
import de.neemann.digital.draw.model.ModelCreator;
import de.neemann.digital.draw.model.ModelEntry;
import de.neemann.digital.lang.Lang;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Exports a rendered circuit after applying a deterministic input state.
 * This keeps simulation snapshots headless and does not need to drive the GUI.
 */
public final class SimulationExport extends BasicCommand {
    private final Argument<String> digFile;
    private final Argument<String> svgFile;
    private final Argument<String> inputs;
    private final Argument<Integer> scale;
    private final Argument<Boolean> hideTest;

    /** Creates the snapshot export command. */
    public SimulationExport() {
        super("snapshot");
        digFile = addArgument(new Argument<>("dig", "", false));
        svgFile = addArgument(new Argument<>("svg", "", false));
        inputs = addArgument(new Argument<>("inputs", "", true));
        scale = addArgument(new Argument<>("scale", 15, true));
        hideTest = addArgument(new Argument<>("hideTest", true, true));
    }

    @Override
    protected void execute() throws CLIException {
        if (scale.get() < 1)
            throw new CLIException("scale must be a positive integer", 200);

        Model model = null;
        try {
            CircuitLoader loader = new CircuitLoader(digFile.get(), false);
            Circuit circuit = loader.getCircuit();
            ModelCreator creator = new ModelCreator(circuit, loader.getLibrary());
            model = creator.createModel(true);
            creator.connectToGui(null);
            model.init();

            Map<String, Long> state = parseInputs(inputs.get());
            applyState(state, creator, model);
            if (!state.isEmpty())
                model.doStep();

            File output = new File(svgFile.get());
            File parent = output.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                throw new CLIException("unable to create output directory: " + parent, 200);

            ElementAttributes svgSettings = new ElementAttributes();
            new Export(circuit, out -> new GraphicSVG(out, scale.get(), svgSettings), hideTest.get()).export(output);
        } catch (CLIException e) {
            throw e;
        } catch (Exception e) {
            throw new CLIException(Lang.get("cli_errorCreatingSVG"), e);
        } finally {
            if (model != null)
                model.close();
        }
    }

    private static Map<String, Long> parseInputs(String specification) throws CLIException {
        Map<String, Long> result = new HashMap<>();
        if (specification == null || specification.trim().isEmpty())
            return result;

        for (String item : specification.split("[,;\\s]+")) {
            int separator = item.indexOf('=');
            if (separator <= 0 || separator == item.length() - 1)
                throw new CLIException("inputs must use NAME=VALUE pairs, for example A=1,B=0", 200);
            String name = item.substring(0, separator).trim();
            String valueText = item.substring(separator + 1).trim();
            try {
                result.put(name, parseValue(valueText));
            } catch (NumberFormatException e) {
                throw new CLIException("invalid input value for " + name + ": " + valueText, e);
            }
        }
        return result;
    }

    private static long parseValue(String valueText) {
        String value = valueText.toLowerCase();
        if (value.startsWith("0b"))
            return Long.parseLong(value.substring(2), 2);
        return Long.decode(valueText);
    }

    private static void applyState(Map<String, Long> state, ModelCreator creator, Model model) throws CLIException {
        Map<String, Button> buttons = new HashMap<>();
        for (ModelEntry entry : creator) {
            Element element = entry.getElement();
            if (element instanceof Button) {
                VisualElement visual = entry.getVisualElement();
                buttons.put(visual.getElementAttributes().getLabel(), (Button) element);
            }
        }

        for (Map.Entry<String, Long> item : state.entrySet()) {
            String name = item.getKey();
            Button button = buttons.get(name);
            if (button != null) {
                button.setPressed(item.getValue() != 0);
                continue;
            }

            Signal.Setter setter = model.getSignalSetter(name);
            if (setter == null)
                setter = model.getSignalSetter(name.replace(' ', '_'));
            if (setter == null)
                throw new CLIException("input or button not found: " + name, 200);
            setter.set(item.getValue(), 0);
        }
    }
}
