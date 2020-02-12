package org.esa.snap.grapheditor.ui.components.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;

import java.awt.event.ActionListener;

import javax.swing.*;

import com.bc.ceres.binding.ConversionException;
import com.bc.ceres.binding.Converter;
import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.dom.DomConverter;
import com.bc.ceres.binding.dom.DomElement;
import com.bc.ceres.binding.dom.XppDomElement;

import com.thoughtworks.xstream.io.xml.xppdom.XppDom;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.OperatorSpiRegistry;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.ParameterDescriptorFactory;
import org.esa.snap.core.gpf.descriptor.OperatorDescriptor;
import org.esa.snap.core.gpf.graph.Graph;
import org.esa.snap.core.gpf.graph.GraphIO;
import org.esa.snap.core.gpf.graph.Node;
import org.esa.snap.core.gpf.graph.NodeSource;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.grapheditor.gpf.ui.OperatorUI;
import org.esa.snap.grapheditor.gpf.ui.OperatorUIRegistry;
import org.esa.snap.grapheditor.ui.components.graph.Connection;
import org.esa.snap.grapheditor.ui.components.graph.NodeGui;

public class GraphManager implements NodeListener {

    private final GPF gpf;
    private final OperatorSpiRegistry opSpiRegistry;

    private final ArrayList<OperatorMetadata> metadatas = new ArrayList<>();
    private final HashMap<String, UnifiedMetadata> simpleMetadatas = new HashMap<>();

    private final ArrayList<NodeGui> nodes = new ArrayList<>();

    static private GraphManager instance = null;

    private HashSet<RefreshListener> listeners = new HashSet<>();

    static public GraphManager getInstance() {
        if (instance == null) {
            instance = new GraphManager();
        }
        return instance;
    }

    private GraphManager() {
        gpf = GPF.getDefaultInstance();
        opSpiRegistry = gpf.getOperatorSpiRegistry();
        for (final OperatorSpi opSpi : opSpiRegistry.getOperatorSpis()) {
            OperatorDescriptor descriptor = opSpi.getOperatorDescriptor();
            if (descriptor != null && !descriptor.isInternal()) {
                final OperatorMetadata operatorMetadata = opSpi.getOperatorClass()
                        .getAnnotation(OperatorMetadata.class);

                metadatas.add(operatorMetadata);
                simpleMetadatas.put(operatorMetadata.alias(), new UnifiedMetadata(operatorMetadata, descriptor));
                              
            }
        }
    }

    public void addEventListener(RefreshListener l) {
        listeners.add(l);
    }

    public void removeEventListener(RefreshListener l) {
        listeners.remove(l);
    }

    private void triggerEvent() {
        for (RefreshListener l : listeners) {
            l.refresh();
        }
    }

    public Operator getOperator(UnifiedMetadata metadata) {
        OperatorSpi spi = opSpiRegistry.getOperatorSpi(metadata.getName());
        if (spi != null) {
            return spi.createOperator();
        }
        return null;
    }

    public Collection<UnifiedMetadata> getSimplifiedMetadatas() {
        return simpleMetadatas.values();
    }

    public ArrayList<OperatorMetadata> getMetadata() {
        return metadatas;
    }

    private Node createNode(final String operator) {
        final Node newNode = new Node(id(operator), operator);

        final XppDomElement parameters = new XppDomElement("parameters");
        newNode.setConfiguration(parameters);

        return newNode;
    }

    public Map<String, Object> getConfiguration(final Node node) {
        final HashMap<String, Object> parameterMap = new HashMap<>();
        final String opName = node.getOperatorName();
        final OperatorSpi operatorSpi = opSpiRegistry.getOperatorSpi(opName);

        final ParameterDescriptorFactory parameterDescriptorFactory = new ParameterDescriptorFactory();
        final PropertyContainer valueContainer = PropertyContainer.createMapBacked(parameterMap,
                operatorSpi.getOperatorClass(), parameterDescriptorFactory);

        final DomElement config = node.getConfiguration();
        final int count = config.getChildCount();
        for (int i = 0; i < count; ++i) {
            final DomElement child = config.getChild(i);
            final String name = child.getName();
            final String value = child.getValue();
            try {
                if (name == null || value == null || value.startsWith("$")) {
                    continue;
                }
                if (child.getChildCount() == 0) {
                    final Converter<?> converter = getConverter(valueContainer, name);
                    if (converter != null) {
                        parameterMap.put(name, converter.parse(value));
                    }
                } else {
                    final DomConverter domConverter = getDomConverter(valueContainer, name);
                    if (domConverter != null) {
                        try {
                            final Object obj = domConverter.convertDomToValue(child, null);
                            parameterMap.put(name, obj);
                        } catch (final Exception e) {
                            SystemUtils.LOG.warning(e.getMessage());
                        }
                    } else {
                        final Converter<?> converter = getConverter(valueContainer, name);
                        final Object[] objArray = new Object[child.getChildCount()];
                        int c = 0;
                        for (final DomElement ch : child.getChildren()) {
                            final String v = ch.getValue();

                            if (converter != null) {
                                objArray[c++] = converter.parse(v);
                            } else {
                                objArray[c++] = v;
                            }
                        }
                        parameterMap.put(name, objArray);
                    }
                }
            } catch (final ConversionException e) {
                SystemUtils.LOG.info(e.getMessage());
            }
        }
        return parameterMap;
    }

    private String id(final String opName) {
        final String res = opName + " ";
        int counter = 0;
        int N = res.length();
        for (NodeGui n : nodes) {

            if (n.getName().startsWith(res)) {
                String postfix = n.getName().substring(N);
                try {
                    int id = Integer.parseInt(postfix);
                    if (id >= counter) {
                        counter = id + 1;
                    }
                } catch (NumberFormatException e) {
                    // not a problem
                    continue;
                }
            }
        }

        return res + counter;
    }

    private static Converter<?> getConverter(final PropertyContainer valueContainer, final String name) {
        final Property[] properties = valueContainer.getProperties();

        for (final Property p : properties) {

            final PropertyDescriptor descriptor = p.getDescriptor();
            if (descriptor != null && (descriptor.getName().equals(name)
                    || (descriptor.getAlias() != null && descriptor.getAlias().equals(name)))) {
                return descriptor.getConverter();
            }
        }
        return null;
    }

    private static DomConverter getDomConverter(final PropertyContainer valueContainer, final String name) {
        final Property[] properties = valueContainer.getProperties();

        for (final Property p : properties) {

            final PropertyDescriptor descriptor = p.getDescriptor();
            if (descriptor != null && (descriptor.getName().equals(name) ||
                    (descriptor.getAlias() != null && descriptor.getAlias().equals(name)))) {
                return descriptor.getDomConverter();
            }
        }
        return null;
    }

    static private JMenu getCategoryMenu(JMenu menu, String category) {
        if (category == null || category.length() == 0) 
            return menu;
        String first = category.split("/")[0];
        
        String rest = "";
        if (first.length() < category.length()) {
            rest = category.substring(first.length()+1);
        }
        int menusCounter = 0;
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item instanceof JMenu) {
                if (item.getText().equals(first)) {
                    return getCategoryMenu((JMenu) item, rest);
                }
                menusCounter ++;
            }
        }
        JMenu newMenu = new JMenu(first);
        menu.insert(newMenu, menusCounter);
        if (rest.length() > 0)
            return getCategoryMenu(newMenu, rest);
        return newMenu;
    }


    public JMenu createOperatorMenu(ActionListener listener) {
        JMenu addMenu = new JMenu("Add");
        for (UnifiedMetadata metadata: getSimplifiedMetadatas()) {
            JMenu menu = getCategoryMenu(addMenu, metadata.getCategory());
            JMenuItem item = new JMenuItem(metadata.getName());
            item.setHorizontalTextPosition(JMenuItem.RIGHT);
            item.addActionListener(listener);
            menu.add(item);
        }
        return addMenu;
    }

    public NodeGui newNode(String opName){
        return newNode(simpleMetadatas.get(opName));
    }

    public NodeGui newNode(UnifiedMetadata metadata) {
        OperatorUI ui = OperatorUIRegistry.CreateOperatorUI(metadata.getName());
        Node node = createNode(metadata.getName());
        NodeGui newNode = new NodeGui(node, getConfiguration(node), metadata, ui);
        this.nodes.add(newNode);
        newNode.addNodeListener(this);
        NotificationManager.getInstance().info(newNode.getName(), "Created");
        return newNode;
    }

    public List<NodeGui> getNodes() {
        return this.nodes;
    }

    public void evaluate() {
        // TODO evaluate graph
    }


    private void validateGraph(NodeGui source) {
        HashMap<Integer, HashSet<NodeGui>> orderedGraph = new HashMap<>();
        int total = 0;
        NotificationManager.getInstance().processStart();
        for (NodeGui n: nodes) {
            if (n != source) {
                int dist = n.distance(source);
                if (dist > 0) {
                    Integer key = new Integer(dist);
                    if (!orderedGraph.containsKey(key)) {
                        orderedGraph.put(key, new HashSet<>());
                    }
                    orderedGraph.get(key).add(n);
                    total ++;
                }
            }
        }
        NotificationManager.getInstance().processEnd();
        if (source.getValidationStatus() == NodeGui.ValidationStatus.ERROR) {
            int i = 0;
            for (Integer key: orderedGraph.keySet()) {
                for (NodeGui n: orderedGraph.get(key)) {
                    i ++;
                    NotificationManager.getInstance().progress((int)(i /(float) total) * 100);
                    n.invalidate();
                }
            }
        } else {
            int i = 0;
            ArrayList<Integer> indexes = new ArrayList<>(orderedGraph.keySet());
            Collections.sort(indexes);
            for (Integer key: indexes) {
                for (NodeGui n: orderedGraph.get(key)) {
                    i ++;
                    NotificationManager.getInstance().progress((int)(i /(float) total) * 100);
                    n.validate();
                }
            }
        }
        NotificationManager.getInstance().processEnd();
        triggerEvent();
    }

    @Override
    public void outputChanged(NodeGui source) {
        // TODO Revalidate rest of the graph.
        validateGraph(source);
    }

    @Override
    public void sourceDeleted(NodeGui source) {
        NotificationManager.getInstance().info(source.getName(), "Deleted");
        this.nodes.remove(source);
    }

    private void clearGraph() {
        for (NodeGui n: nodes) {
            n.removeNodeListener(this);
        }
        this.nodes.clear();
    }

    /***
     * Initialize a simple graph composed by a Reader and a Writer
     */
    public void createEmptyGraph() {
        clearGraph();

        NodeGui n = newNode("Read");
        n.setPosition(90, 30);
        n = newNode("Write");
        n.setPosition(390, 30);
    }

    /**
     * Open an existing Graph.
     * @param selectedFile file to open
     */
    public void openGraph(File selectedFile) {
        clearGraph();
        // TODO implement XML loading
        NotificationManager.getInstance().processStart();
        GraphLoadWorker worker = new GraphLoadWorker(selectedFile);
        worker.execute();

    }

    private void loadGraph(ArrayList<NodeGui> nodes) {
        clearGraph();
        for (NodeGui n: nodes) {
            n.addNodeListener(this);
            this.nodes.add(n);
        }
        NotificationManager.getInstance().processEnd();
        NotificationManager.getInstance().info("Graph", "Loaded and ready");
        triggerEvent();
    }

    public boolean saveGraph(File f) {
        NotificationManager.getInstance().processStart();
        Graph graph = new Graph("graph");
        XppDom presentationEl = new XppDom("applicationData");
        presentationEl.setAttribute("id", "Presentation");

        for (NodeGui n: nodes) {
            for (Connection c: n.getIncomingConnections()) {
                NodeSource source = new NodeSource("sourceProduct", c.getSource().getNode().getId());
                n.getNode().addSource(source);
            }
            presentationEl.addChild(n.saveParameters());
            graph.addNode(n.getNode());
        }
        graph.setAppData("Presentation", presentationEl);
        try {
            OutputStreamWriter fileWriter = new OutputStreamWriter(new FileOutputStream(f));
            GraphIO.write(graph, fileWriter);
            NotificationManager.getInstance().processEnd();
            NotificationManager.getInstance().info("Graph Saver", "file saved `"+f.getName()+"`");
            return true;
        } catch (FileNotFoundException e){
            NotificationManager.getInstance().processEnd();
            NotificationManager.getInstance().error("Graph Saver", "file saving error `" + e.getMessage() + "`");
        }
        return  false;
    }

    private class GraphLoadWorker extends SwingWorker<ArrayList<NodeGui>, Object> {
        private File source;

        GraphLoadWorker(File file) {
            source = file;
        }

        @Override
        protected ArrayList<NodeGui> doInBackground() throws Exception {
            ArrayList<NodeGui> nodes = new ArrayList<>();
            Graph graph;
            InputStreamReader fileReader = new InputStreamReader(new FileInputStream(source));
            try {
                graph = GraphIO.read(fileReader);
            } finally {
                fileReader.close();
            }
            if (graph != null) {
                for (Node n : graph.getNodes()) {
                    if (simpleMetadatas.containsKey(n.getOperatorName())) {
                        UnifiedMetadata meta = simpleMetadatas.get(n.getOperatorName());
                        OperatorUI ui = OperatorUIRegistry.CreateOperatorUI(meta.getName());
                        NodeGui ng = new NodeGui(n, getConfiguration(n), meta, ui);
                        nodes.add(ng);
                    } else {
                        NotificationManager.getInstance().error("Graph",
                                                                "Operator '" + n.getOperatorName() +"' not known.");
                    }
                }
                // Load position
                final XppDom presentationXML = graph.getApplicationData("Presentation");
                if (presentationXML != null) {
                    for (XppDom el : presentationXML.getChildren()) {
                        if (el.getName().equals("node")) {
                            String id = el.getAttribute("id");
                            for (NodeGui n: nodes) {
                                if (n.getName().equals(id)) {
                                    n.loadParameters(el);
                                    break;
                                }
                            }
                        }
                    }
                }
                //Connect nodes
                for (NodeGui trgNode: nodes) {
                    int index = 0;
                    for (NodeSource src: trgNode.getNode().getSources()) {
                        String id = src.getSourceNodeId();
                        NodeGui srcNode = null;
                        for (NodeGui ns: nodes) {
                            if (ns.getName().equals(id)) {
                                srcNode = ns;
                                break;
                            }
                        }
                        if (srcNode != null) {
                            Connection cnn = new Connection(srcNode, trgNode, index);
                            trgNode.addConnection(cnn, index);
                            index ++;
                        }
                    }
                }

                loadGraph(nodes);
            }
            return nodes;
        }
    }
}