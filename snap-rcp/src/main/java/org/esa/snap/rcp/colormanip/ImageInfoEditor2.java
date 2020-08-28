/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
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
package org.esa.snap.rcp.colormanip;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.swing.ActionLabel;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.util.PropertyMap;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.ui.ImageInfoEditor;
import org.esa.snap.ui.ImageInfoEditorModel;
import org.esa.snap.ui.UIUtils;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;

/**
 *
 * @author Brockmann Consult
 * @author Daniel Knowles (NASA)
 * @author Bing Yang (NASA)
 */
// OCT 2019 - Knowles / Yang
//          - Added method to override abstract method "checkSliderRangeCompatibility".
//          - Added method to override abstract method "checkLogCompatibility".
// Feb 2020 - Knowles
//          - Added calls to reset the color scheme selector


class ImageInfoEditor2 extends ImageInfoEditor {

    private final ColorManipulationForm parentForm;
    private boolean showExtraInfo;

    ImageInfoEditor2(final ColorManipulationForm parentForm) {
        this.parentForm = parentForm;
        setLayout(new BorderLayout());

        PropertyMap configuration = parentForm.getFormModel().getProductSceneView().getSceneImage().getConfiguration();
        boolean showExtraInformation = configuration.getPropertyBool(ColorManipulationDefaults.PROPERTY_SLIDERS_SHOW_INFORMATION_KEY, ColorManipulationDefaults.PROPERTY_SLIDERS_SHOW_INFORMATION_DEFAULT);

        setShowExtraInfo(showExtraInformation);
        addPropertyChangeListener("model", new ModelChangeHandler());
    }

    public boolean getShowExtraInfo() {
        return showExtraInfo;
    }

    public ColorManipulationForm getParentForm() {
        return parentForm;
    }

    public void setShowExtraInfo(boolean showExtraInfo) {
        boolean oldValue = this.showExtraInfo;
        if (oldValue != showExtraInfo) {
            this.showExtraInfo = showExtraInfo;
            updateStxOverlayComponent();
            firePropertyChange("showExtraInfo", oldValue, this.showExtraInfo);
        }
    }

    private void updateStxOverlayComponent() {
        removeAll();
        add(createStxOverlayComponent(), BorderLayout.NORTH);
        revalidate();
        repaint();
    }

    private JPanel createStxOverlayComponent() {
        JPanel stxOverlayComponent = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        stxOverlayComponent.setOpaque(false);

        final ImageInfoEditorModel model = getModel();
        if (!showExtraInfo || model == null) {
            return stxOverlayComponent;
        }

        stxOverlayComponent.setBorder(new EmptyBorder(4, 0, 0, 8));

        JComponent labels = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        labels.setBorder(new EmptyBorder(0, 2, 0, 2));
        labels.setLayout(new GridLayout(-1, 1));
        labels.setBackground(new Color(255, 255, 255, 127));
        stxOverlayComponent.add(labels);

        labels.add(new JLabel("Name: " + model.getParameterName()));
        labels.add(new JLabel("Unit: " + model.getParameterUnit()));

        final Stx stx = model.getSampleStx();
        if (stx == null) {
            return stxOverlayComponent;
        }

        labels.add(new JLabel("Min: " + getValueForDisplay(model.getMinSample())));
        labels.add(new JLabel("Max: " + getValueForDisplay(model.getMaxSample())));
        if (stx.getResolutionLevel() > 0 && model.getSampleScaling() == Scaling.IDENTITY) {
            final ActionLabel label = new ActionLabel("Rough statistics!");
            label.setToolTipText("Click to compute accurate statistics.");
            label.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    askUser();
                }
            });
            labels.add(label);
        }

        return stxOverlayComponent;
    }

    private String getValueForDisplay(double value) {
        if (!Double.isNaN(value)) { // prevents NaN to be converted to zero
            if (value < 0.001 && value > -0.001 && value != 0.0) {
                return new DecimalFormat("0.##E0").format(value);
            }
            value = MathUtils.round(value, 1000.0);
        }
        return "" + value;
    }

    void askUser() {
        final int i = JOptionPane.showConfirmDialog(this,
                                                    "Compute accurate statistics?\n" +
                                                            "Note that this action may take some time.",
                                                    "Question",
                                                    JOptionPane.YES_NO_OPTION);
        if (i == JOptionPane.YES_OPTION) {
            SwingWorker sw = new StxComputer();
            sw.execute();
        }
    }


    public void updateShowExtraInformationFromPreferences() {
        PropertyMap configuration = parentForm.getFormModel().getProductSceneView().getSceneImage().getConfiguration();
        boolean showExtraInformation = configuration.getPropertyBool(ColorManipulationDefaults.PROPERTY_SLIDERS_SHOW_INFORMATION_KEY, ColorManipulationDefaults.PROPERTY_SLIDERS_SHOW_INFORMATION_DEFAULT);
        setShowExtraInfo(showExtraInformation);
    }


    @Override
    protected void applyChanges() {
        resetColorSchemeSelector();
        updateShowExtraInformationFromPreferences();

        parentForm.applyChanges();
    }

    private class ModelChangeHandler implements PropertyChangeListener, ChangeListener {


        @Override
        public void stateChanged(ChangeEvent e) {
            updateStxOverlayComponent();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (!"model".equals(evt.getPropertyName())) {
                return;
            }

            final ImageInfoEditorModel oldModel = (ImageInfoEditorModel) evt.getOldValue();
            final ImageInfoEditorModel newModel = (ImageInfoEditorModel) evt.getNewValue();
            if (oldModel != null) {
                oldModel.removeChangeListener(this);
            }
            if (newModel != null) {
                newModel.addChangeListener(this);
            }

            updateStxOverlayComponent();
        }
    }

    private class StxComputer extends ProgressMonitorSwingWorker {

        private StxComputer() {
            super(ImageInfoEditor2.this, "Computing statistics");
        }

        @Override
        protected Object doInBackground(ProgressMonitor pm) throws Exception {
            UIUtils.setRootFrameWaitCursor(ImageInfoEditor2.this);
            if (parentForm.getFormModel().isValid()) {
                final RasterDataNode[] rasters = parentForm.getFormModel().getRasters();
                try {
                    pm.beginTask("Computing statistics", rasters.length);
                    for (RasterDataNode raster : rasters) {
                        raster.getStx(true, SubProgressMonitor.create(pm, 1));
                    }
                } finally {
                    pm.done();
                }
            }
            return null;
        }

        @Override
        protected void done() {
            UIUtils.setRootFrameDefaultCursor(ImageInfoEditor2.this);
        }
    }


    @Override
    protected boolean checkLogCompatibility(double value, String componentName, boolean isLogScaled) {
        return ColorUtils.checkLogCompatibility(value, componentName, isLogScaled);
    }

    @Override
    protected boolean checkSliderRangeCompatibility(double value, double min, double max) {
        return ColorUtils.checkSliderRangeCompatibility (value, min, max);
    }


    private void resetColorSchemeSelector() {
        ColorSchemeInfo colorSchemeNoneInfo = ColorSchemeManager.getDefault().getNoneColorSchemeInfo();
        parentForm.getFormModel().getProductSceneView().getImageInfo().setColorSchemeInfo(colorSchemeNoneInfo);
        parentForm.getFormModel().getModifiedImageInfo().setColorSchemeInfo(colorSchemeNoneInfo);
    }
}
