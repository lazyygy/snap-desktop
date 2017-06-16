/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.snap.rcp.statistics;

import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.internal.SliderAdapter;
import org.esa.snap.core.datamodel.MetadataAttribute;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductNode;
import org.esa.snap.core.datamodel.ProductNodeEvent;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.ui.RectangleInsets;
import org.openide.windows.TopComponent;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.util.Arrays;
import java.util.Hashtable;

import static org.esa.snap.rcp.statistics.MetadataPlotSettings.PROP_NAME_FIELD_X;
import static org.esa.snap.rcp.statistics.MetadataPlotSettings.PROP_NAME_FIELD_Y1;
import static org.esa.snap.rcp.statistics.MetadataPlotSettings.PROP_NAME_FIELD_Y2;
import static org.esa.snap.rcp.statistics.MetadataPlotSettings.PROP_NAME_METADATA_ELEMENT;
import static org.esa.snap.rcp.statistics.MetadataPlotSettings.PROP_NAME_RECORDS_PER_PLOT;
import static org.esa.snap.rcp.statistics.MetadataPlotSettings.PROP_NAME_RECORD_START_INDEX;


/**
 * The metadata plot pane within the statistics window.
 */
class MetadataPlotPanel extends ChartPagePanel {
    private static final String DEFAULT_SAMPLE_DATASET_NAME = "Sample";

    private static final String NO_DATA_MESSAGE = "No metadata plot computed yet.\n" +
            "To create a plot, select metadata elements in both combo boxes.\n" +
            "The plot will be computed when you click the 'Refresh View' button.\n" +
            HELP_TIP_MESSAGE + "\n" +
            ZOOM_TIP_MESSAGE;
    private static final String CHART_TITLE = "Metadata Plot";
    private static final String DATA_SERIES_1 = "data_series_01";
    private static final String DATA_SERIES_2 = "data_series_02";

    private JFreeChart chart;
    private DefaultXYDataset dataset;
    private MetadataPlotSettings plotSettings;
    private boolean isInitialized;
    private JSlider recordSlider;
    private SpinnerNumberModel numRecSpinnerModel;


    MetadataPlotPanel(TopComponent parentComponent, String helpId) {
        super(parentComponent, helpId, CHART_TITLE, true);
    }

    @Override
    protected void initComponents() {
        if (hasAlternativeView()) {
            getAlternativeView().initComponents();
        }
        dataset = new DefaultXYDataset();
        chart = ChartFactory.createXYLineChart(
                CHART_TITLE,
                "x-values",
                DEFAULT_SAMPLE_DATASET_NAME,
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        final XYPlot plot = chart.getXYPlot();

        plot.setNoDataMessage(NO_DATA_MESSAGE);
        plot.setAxisOffset(new RectangleInsets(5, 5, 5, 5));

        final ValueAxis domainAxis = plot.getDomainAxis();
        final ValueAxis rangeAxis = plot.getRangeAxis();
        // allow transfer from bounds into min/max fields, if auto min/maxis enabled
        domainAxis.setAutoRange(true);
        rangeAxis.setAutoRange(true);

        ChartPanel profilePlotDisplay = new ChartPanel(chart);

        profilePlotDisplay.setInitialDelay(200);
        profilePlotDisplay.setDismissDelay(1500);
        profilePlotDisplay.setReshowDelay(200);
        profilePlotDisplay.setZoomTriggerDistance(5);
        profilePlotDisplay.getPopupMenu().addSeparator();
        profilePlotDisplay.getPopupMenu().add(createCopyDataToClipboardMenuItem());


        plotSettings = new MetadataPlotSettings();
        final BindingContext bindingContext = plotSettings.getContext();

        JPanel settingsPanel = createSettingsPanel(bindingContext);
        createUI(profilePlotDisplay, settingsPanel, (RoiMaskSelector) null);

        bindingContext.setComponentsEnabled(PROP_NAME_RECORD_START_INDEX, false);
        bindingContext.setComponentsEnabled(PROP_NAME_RECORDS_PER_PLOT, false);

        isInitialized = true;

        updateComponents();
        updateChartData();

        bindingContext.addPropertyChangeListener(PROP_NAME_METADATA_ELEMENT, evt -> updateUiState());
        bindingContext.addPropertyChangeListener(evt -> updateChartData());

    }

    @Override
    public void nodeDataChanged(ProductNodeEvent event) {
        // probably nothing needs to be done here
        super.nodeDataChanged(event);
    }

    @Override
    protected void updateComponents() {
        super.updateComponents();
        updateSettings();
        updateUiState();
    }


    @Override
    protected void updateChartData() {
        removeAllDatasetSeries();

        MetadataElement metadataElement = plotSettings.getMetadataElement();
        String nameX = plotSettings.getNameX();
        String nameY1 = plotSettings.getNameY1();
        if (metadataElement == null || nameX == null || !isValidYFieldName(nameY1)) {
            return;
        }

        switch (nameX) {
            case MetadataPlotSettings.FIELD_NAME_RECORD_INDEX:
                int numRecords = plotSettings.getNumRecords();
                int recordsPerPlot = plotSettings.getRecordsPerPlot();
                int startIndex = plotSettings.getRecordStartIndex();
                double[] xAxisData = getRecordIndices(startIndex, recordsPerPlot, numRecords);
                String name = metadataElement.getName();
                String[] recordElementNames = new String[xAxisData.length];
                Arrays.setAll(recordElementNames, i -> String.format("%s.%.0f", name, xAxisData[i]));
                double[] y1AxisData =  new double[xAxisData.length];
                Arrays.setAll(y1AxisData, i -> metadataElement.getElement(recordElementNames[i]).getAttribute(nameY1).getData().getElemDouble());

                dataset.addSeries(DATA_SERIES_1, new double[][]{xAxisData, y1AxisData});
                String nameY2 = plotSettings.getFieldY2();
                if(isValidYFieldName(nameY2)) {
                    double[] y2AxisData =  new double[xAxisData.length];
                    Arrays.setAll(y2AxisData, i -> metadataElement.getElement(recordElementNames[i]).getAttribute(nameY2).getData().getElemDouble());
                    dataset.addSeries(DATA_SERIES_2, new double[][]{xAxisData, y2AxisData});
                }
                break;
            case MetadataPlotSettings.FIELD_NAME_ARRAY_FIELD_INDEX:
                break;
            default:
                break;
        }

    }

    private void removeAllDatasetSeries() {
        dataset.removeSeries(DATA_SERIES_1);
        dataset.removeSeries(DATA_SERIES_2);
    }

    private boolean isValidYFieldName(String yFieldName) {
        return yFieldName != null && !MetadataPlotSettings.FIELD_NAME_NONE.equals(yFieldName);
    }

    static double[] getRecordIndices(int startIndex, int recordsPerPlot, int numRecords) {
        int clippedStartIndex = Math.max(1, Math.min(startIndex, numRecords));
        int clippedEndIndex = Math.min(numRecords, Math.min((startIndex - 1) + recordsPerPlot, numRecords));
        double[] indexArray = new double[clippedEndIndex - clippedStartIndex + 1];
        Arrays.setAll(indexArray, index -> index + clippedStartIndex);
        return indexArray;
    }

    @Override
    protected String getDataAsText() {
        return "";
    }


    private JPanel createSettingsPanel(BindingContext bindingContext) {
        final JLabel datasetLabel = new JLabel("Dataset: ");
        final JComboBox<MetadataElement> datasetBox = new JComboBox<>();
        datasetBox.setRenderer(new ProductNodeListCellRenderer());
        JLabel recordLabel = new JLabel("Record: ");
        JTextField recordValueField = new JTextField(7);
        recordSlider = new JSlider(SwingConstants.HORIZONTAL, 1, 1, 1);
        recordSlider.setPaintTrack(true);
        recordSlider.setPaintTicks(true);
        JLabel numRecordsLabel = new JLabel("Records / Plot: ");
        numRecSpinnerModel = new SpinnerNumberModel(1, 1, 1, 1);
        JSpinner numRecordsSpinner = new JSpinner(numRecSpinnerModel);
        final JLabel xFieldLabel = new JLabel("X Field: ");
        final JComboBox<MetadataAttribute> xFieldBox = new JComboBox<>();
        xFieldBox.setRenderer(new ProductNodeListCellRenderer());
        final JLabel y1FieldLabel = new JLabel("Y Field: ");
        final JComboBox<MetadataAttribute> y1FieldBox = new JComboBox<>();
        y1FieldBox.setRenderer(new ProductNodeListCellRenderer());
        final JLabel y2FieldLabel = new JLabel("Y2 Field: ");
        final JComboBox<MetadataAttribute> y2FieldBox = new JComboBox<>();
        y2FieldBox.setRenderer(new ProductNodeListCellRenderer());

        bindingContext.bind(PROP_NAME_METADATA_ELEMENT, datasetBox);
        bindingContext.bind(PROP_NAME_RECORD_START_INDEX, recordValueField);
        bindingContext.bind(PROP_NAME_RECORD_START_INDEX, new SliderAdapter(recordSlider));
        bindingContext.bind(PROP_NAME_RECORDS_PER_PLOT, numRecordsSpinner);
        bindingContext.bind(PROP_NAME_FIELD_X, xFieldBox);
        bindingContext.bind(PROP_NAME_FIELD_Y1, y1FieldBox);
        bindingContext.bind(PROP_NAME_FIELD_Y2, y2FieldBox);

        TableLayout layout = new TableLayout(3);
        JPanel plotSettingsPanel = new JPanel(layout);

        layout.setTableWeightX(0.0);
        layout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setTablePadding(4, 4);

        layout.setCellWeightX(0, 1, 1.0);
        layout.setCellColspan(0, 1, 2);
        plotSettingsPanel.add(datasetLabel);
        plotSettingsPanel.add(datasetBox);

        layout.setCellWeightX(1, 1, 0.2);
        layout.setCellWeightX(1, 2, 0.8);
        plotSettingsPanel.add(recordLabel);
        plotSettingsPanel.add(recordValueField);
        plotSettingsPanel.add(recordSlider);

        layout.setCellWeightX(2, 1, 1.0);
        layout.setCellColspan(2, 1, 2);
        plotSettingsPanel.add(numRecordsLabel);
        plotSettingsPanel.add(numRecordsSpinner);

        layout.setCellWeightX(3, 1, 1.0);
        layout.setCellColspan(3, 1, 2);
        plotSettingsPanel.add(xFieldLabel);
        plotSettingsPanel.add(xFieldBox);

        layout.setCellWeightX(4, 1, 1.0);
        layout.setCellColspan(4, 1, 2);
        plotSettingsPanel.add(y1FieldLabel);
        plotSettingsPanel.add(y1FieldBox);

        layout.setCellWeightX(5, 1, 1.0);
        layout.setCellColspan(5, 1, 2);
        plotSettingsPanel.add(y2FieldLabel);
        plotSettingsPanel.add(y2FieldBox);

        updateSettings();
        updateUiState();

        return plotSettingsPanel;
    }

    private void updateUiState() {
        if (!isInitialized) {
            return;
        }


        int numRecords = plotSettings.getNumRecords();
        recordSlider.setMaximum(numRecords);
        Hashtable standardLabels = recordSlider.createStandardLabels(Math.max(recordSlider.getMaximum() - recordSlider.getMinimum(), 1), recordSlider.getMinimum());
        recordSlider.setLabelTable(standardLabels);

        numRecSpinnerModel.setMaximum(numRecords);

        plotSettings.getContext().setComponentsEnabled(PROP_NAME_RECORD_START_INDEX, numRecords > 1);
        plotSettings.getContext().setComponentsEnabled(PROP_NAME_RECORDS_PER_PLOT, numRecords > 1);

    }

    private void updateSettings() {
        Product product = getProduct();
        if (product == null) {
            plotSettings.setMetadataElements(null);
            return;
        }

        removeAllDatasetSeries();

        MetadataElement metadataRoot = product.getMetadataRoot();
        MetadataElement[] elements = metadataRoot.getElements();

        plotSettings.setMetadataElements(elements);

    }

    private static class ProductNodeListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel rendererComponent = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ProductNode) {
                ProductNode element = (ProductNode) value;
                rendererComponent.setText(element.getName());
            }
            return rendererComponent;
        }
    }

}
