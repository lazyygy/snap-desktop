/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.rcp.mask;

import org.esa.snap.rcp.windows.ToolTopComponent;
import org.esa.snap.ui.GridBagUtils;
import org.esa.snap.ui.UIUtils;
import org.esa.snap.ui.tool.ToolButtonFactory;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;

class MaskManagerForm extends MaskForm {

    private final AbstractButton helpButton;
    private final MaskFormActions actions;

    MaskManagerForm(ToolTopComponent maskTopComponent, ListSelectionListener selectionListener) {
        super(true, selectionListener);

        helpButton = ToolButtonFactory.createButton(UIUtils.loadImageIcon("icons/Help22.png"), false);
        helpButton.setName("helpButton");
        actions = new MaskFormActions(maskTopComponent, this);

        updateState();
    }

    @Override
    public Action getDoubleClickAction() {
        return actions.getEditAction();
    }

    @Override
    public AbstractButton getHelpButton() {
        return helpButton;
    }

    @Override
    public final void updateState() {
        for (MaskAction maskAction : actions.getAllActions()) {
            maskAction.updateState();
        }
    }


    @Override
    public JPanel createContentPanel() {
        JPanel buttonPanel = GridBagUtils.createPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.5;

        gbc.insets.bottom = 0;
        gbc.gridwidth = 1;
        final MaskAction[] allActions = actions.getAllActions();
        for (int i = 0; i < allActions.length; i += 1) {
            buttonPanel.add(allActions[i].createComponent(), gbc);
            gbc.gridy++;
        }

        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.weighty = 1.0;
        buttonPanel.add(new JLabel(" "), gbc); // filler
        gbc.fill = GridBagConstraints.NONE;
        gbc.weighty = 0.0;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        buttonPanel.add(helpButton, gbc);

        JPanel tablePanel = new JPanel(new BorderLayout(4, 4));
        tablePanel.add(new JScrollPane(getMaskTable()), BorderLayout.CENTER);

        JPanel contentPane1 = new JPanel(new BorderLayout(4, 4));
        contentPane1.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        contentPane1.add(BorderLayout.CENTER, tablePanel);
        contentPane1.add(BorderLayout.EAST, buttonPanel);

        updateState();

        return contentPane1;
    }
}
