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

package org.esa.snap.rcp.colormanip;

import com.bc.ceres.binding.ValueRange;
import org.esa.snap.core.datamodel.ColorPaletteDef;
import org.esa.snap.core.datamodel.ImageInfo;
import org.esa.snap.core.datamodel.ProductNodeEvent;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.ui.color.ColorTableCellEditor;
import org.esa.snap.ui.color.ColorTableCellRenderer;

import javax.swing.AbstractButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import java.awt.Color;
import java.awt.Component;

/**
 *
 * @author Brockmann Consult
 * @author Daniel Knowles (NASA)
 * @author Bing Yang (NASA)
 */
// OCT 2019 - Knowles / Yang
//          - Added checks to ensure that only positive values are allowed in log scaling mode.
//          - Added checks to ensure that palette values are numeric.
//          - Added checks to ensure that value entries are numerically between the adjacent values.


public class Continuous1BandTabularForm implements ColorManipulationChildForm {

    private static final String[] COLUMN_NAMES = new String[]{"Color", "Value"};
    private static final Class<?>[] COLUMN_TYPES = new Class<?>[]{Color.class, String.class};

    private final ColorManipulationForm parentForm;
    private ImageInfoTableModel tableModel;
    private JScrollPane contentPanel;
    private final MoreOptionsForm moreOptionsForm;
    private TableModelListener tableModelListener;
    private final DiscreteCheckBox discreteCheckBox;

    public Continuous1BandTabularForm(final ColorManipulationForm parentForm) {
        this.parentForm = parentForm;
        tableModel = new ImageInfoTableModel();
        tableModelListener = e -> {
            tableModel.removeTableModelListener(tableModelListener);
            parentForm.applyChanges();
            tableModel.addTableModelListener(tableModelListener);
        };
        moreOptionsForm = new MoreOptionsForm(this, parentForm.getFormModel().canUseHistogramMatching());
        discreteCheckBox = new DiscreteCheckBox(parentForm);
        moreOptionsForm.addRow(discreteCheckBox);
        parentForm.getFormModel().modifyMoreOptionsForm(moreOptionsForm);

        final JTable table = new JTable(tableModel);
        table.setDefaultRenderer(Color.class, new ColorTableCellRenderer());
        table.setDefaultEditor(Color.class, new ColorTableCellEditor());
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        final JScrollPane tableScrollPane = new JScrollPane(table);
        tableScrollPane.getViewport().setPreferredSize(table.getPreferredSize());
        contentPanel = tableScrollPane;
    }

    @Override
    public ColorManipulationForm getParentForm() {
        return parentForm;
    }

    @Override
    public void handleFormShown(ColorFormModel formModel) {
        updateFormModel(formModel);
        tableModel.addTableModelListener(tableModelListener);
    }

    @Override
    public void handleFormHidden(ColorFormModel formModel) {
        tableModel.removeTableModelListener(tableModelListener);
    }

    @Override
    public void updateFormModel(ColorFormModel formModel) {
        tableModel.fireTableDataChanged();
        discreteCheckBox.setDiscreteColorsMode(parentForm.getFormModel().getModifiedImageInfo().getColorPaletteDef().isDiscrete());
    }

    @Override
    public void resetFormModel(ColorFormModel formModel) {
        tableModel.fireTableDataChanged();
    }

    @Override
    public void handleRasterPropertyChange(ProductNodeEvent event, RasterDataNode raster) {
    }

    @Override
    public Component getContentPanel() {
        return contentPanel;
    }

    @Override
    public AbstractButton[] getToolButtons() {
        return new AbstractButton[0];
    }

    @Override
    public MoreOptionsForm getMoreOptionsForm() {
        return moreOptionsForm;
    }

    @Override
    public RasterDataNode[] getRasters() {
        return parentForm.getFormModel().getRasters();
    }

    private class ImageInfoTableModel extends AbstractTableModel {

        private ImageInfoTableModel() {
        }

        public ImageInfo getImageInfo() {
            return parentForm.getFormModel().getModifiedImageInfo();
        }

        @Override
        public String getColumnName(int columnIndex) {
            return COLUMN_NAMES[columnIndex];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return COLUMN_TYPES[columnIndex];
        }

        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public int getRowCount() {
            if (getImageInfo() == null) {
                return 0;
            }
            return getImageInfo().getColorPaletteDef().getNumPoints();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            final ColorPaletteDef.Point point = getImageInfo().getColorPaletteDef().getPointAt(rowIndex);
            if (columnIndex == 0) {
                final Color color = point.getColor();
                return color.equals(ImageInfo.NO_COLOR) ? null : color;
            } else if (columnIndex == 1) {
                return point.getSample();
            }
            return null;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            final ColorPaletteDef.Point point = getImageInfo().getColorPaletteDef().getPointAt(rowIndex);
            final ValueRange valueRange;
            if (rowIndex == 0) {
                valueRange = new ValueRange(Double.NEGATIVE_INFINITY, getImageInfo().getColorPaletteDef().getPointAt(1).getSample());
            } else if (rowIndex == getImageInfo().getColorPaletteDef().getNumPoints() - 1) {
                valueRange = new ValueRange(getImageInfo().getColorPaletteDef().getPointAt(rowIndex - 1).getSample(), Double.POSITIVE_INFINITY);
            } else {
                valueRange = new ValueRange(getImageInfo().getColorPaletteDef().getPointAt(rowIndex - 1).getSample(),
                        getImageInfo().getColorPaletteDef().getPointAt(rowIndex + 1).getSample());
            }
            if (columnIndex == 0) {
                final Color color = (Color) aValue;
                point.setColor(color == null ? ImageInfo.NO_COLOR : color);
                fireTableCellUpdated(rowIndex, columnIndex);
            } else if (columnIndex == 1) {

                if (ColorUtils.isNumber((String) aValue, "Table value", true)) {
                    double aValueDouble = Double.parseDouble((String) aValue);
                    if (ColorUtils.checkTableRangeCompatibility(aValueDouble, valueRange.getMin(), valueRange.getMax()) && ColorUtils.checkLogCompatibility(aValueDouble, "Value",
                            parentForm.getFormModel().getModifiedImageInfo().isLogScaled())) {
                        point.setSample(aValueDouble);
                        fireTableCellUpdated(rowIndex, columnIndex);
                    }
                }
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0 || columnIndex == 1;
        }

    }
}
