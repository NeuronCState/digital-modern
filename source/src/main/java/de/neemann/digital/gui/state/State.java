/*
 * Copyright (c) 2016 Helmut Neemann
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.state;

import de.neemann.gui.ToolTipAction;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.ActionEvent;

/**
 * A simple state
 */
public class State implements StateInterface {
    // The toggle indicator's "selected" state used to be drawn with a Swing
    // BevelBorder, which painted a hard, square-cornered rectangle on top of
    // the toolbar button and clashed with the rounded-rectangle look used
    // everywhere else. The selection visual is now produced by the modern
    // toolbar's MotionButtonUI through ButtonModel.isSelected(); the border
    // itself stays empty so the rounded shape is never overdrawn.
    private static final Border ENABLED_BORDER = BorderFactory.createEmptyBorder(8, 9, 8, 9);
    private static final Border DISABLED_BORDER = BorderFactory.createEmptyBorder(8, 9, 8, 9);
    private JComponent indicator;
    private StateManager stateManager;
    private ToolTipAction action;

    /**
     * Creates new state
     */
    public State() {
    }

    /**
     * The JComponent used to indicate the state
     *
     * @param indicator the JComponent
     * @param <C>       the type of the JComponent
     * @return the JComponent for call chaining
     */
    public <C extends JComponent> C setIndicator(C indicator) {
        this.indicator = indicator;
        indicator.setBorder(DISABLED_BORDER);
        return indicator;
    }

    void setStateManager(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    /**
     * Sets the state indicator to "activated"
     */
    public void enter() {
        stateManager.leaveActualStateAndSet(this);
        if (indicator != null)
            indicator.setBorder(ENABLED_BORDER);
    }

    @Override
    public void leave() {
        if (indicator != null)
            indicator.setBorder(DISABLED_BORDER);
    }

    /**
     * @return the action associated with this state
     */
    public ToolTipAction getAction() {
        return action;
    }

    /**
     * Creates a tooltip action which activates the state
     *
     * @param name the name of the action to create
     * @param icon the icon to use
     * @return the action
     */
    public ToolTipAction createToolTipAction(String name, Icon icon) {
        if (action == null)
            action = new ToolTipAction(name, icon) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    enter();
                }
            };
        return action;
    }

    /**
     * @return true if this state is active
     */
    public boolean isActive() {
        return stateManager.isActive(this);
    }
}
