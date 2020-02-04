package org.esa.snap.grapheditor.ui.components.graph;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Map;

import javax.swing.JComponent;

import com.bc.ceres.binding.dom.DomElement;
import com.bc.ceres.binding.dom.XppDomElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.graph.GraphException;
import org.esa.snap.core.gpf.graph.Node;
import org.esa.snap.core.gpf.graph.NodeSource;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.grapheditor.gpf.ui.OperatorUI;
import org.esa.snap.grapheditor.gpf.ui.UIValidation;
import org.esa.snap.grapheditor.ui.components.utils.GridUtils;
import org.esa.snap.grapheditor.ui.components.utils.NodeDragAction;
import org.esa.snap.grapheditor.ui.components.utils.NodeListener;
import org.esa.snap.grapheditor.ui.components.utils.OperatorManager;
import org.esa.snap.grapheditor.ui.components.utils.OperatorManager.SimplifiedMetadata;
import org.esa.snap.ui.AppContext;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class NodeGui implements NodeListener {
    public static final int STATUS_MASK_OVER = 1 << 1;
    public static final int STATUS_MASK_SELECTED = 1 << 2;

    private static final int MAX_LINE_LENGTH = 45;

    // private static final Color errorColor = new Color(255, 80, 80, 128);
    // private static final Color validateColor =  new Color(0, 177, 255, 128);
    private static final Color unknownColor =  new Color(233, 229, 225, 230); //Color
    // private static final Color connectionColor = new Color(66, 66, 66, 255);
    private static final Color activeColor = new Color(254, 223, 176, 180);

    private static final Color tooltipBackground = new Color(0, 0, 0, 180);
    private static final Color tooltipBorder = Color.white;
    private static final Color tooltipColor = Color.lightGray;

    static final private BasicStroke borderStroke = new BasicStroke(3);
    static final private BasicStroke tooltipStroke = new BasicStroke(1.5f);
    static final private BasicStroke textStroke = new BasicStroke(1);
    static final private BasicStroke activeStroke = new BasicStroke(6);

    static final private int connectionSize = 10;
    static final private int connectionHalfSize = connectionSize / 2;
    static final private int connectionOffset = 15;

    static final private Font textFont = new Font("Ariel", Font.BOLD, 11);

    static final private int minWidth = 60;

    static final public int CONNECTION_NONE = -202;
    static final public int CONNECTION_OUTPUT = -1;

    private int x;
    private int y;
    private int width = 90;
    private int height = 30;

    private int textW = -1;
    private int textH = -1;
    
    private String name;
    
    private int status = 0;

    private final SimplifiedMetadata metadata;
    private final OperatorUI operatorUI;
    private final Node node;
    private final Map<String, Object> configuration;
    private int numInputs;

    private JComponent preferencePanel = null;
    private String[] tooltipText_ = null;
    private boolean tooltipVisible_ = false;
    private int tooltipIndex_ = CONNECTION_NONE;

    private ArrayList<NodeListener> nodeListeners = new ArrayList<>();
    private ArrayList<Connection> connections = new ArrayList<>();

    private XppDomElement config = null;
    private boolean hasInputsChanged = false;
    private Product output = null;

    public NodeGui (Node node, Map<String, Object> configuration, @NotNull SimplifiedMetadata metadata, OperatorUI operatorUI){
        this.x = 0;
        this.y = 0;
        this.metadata = metadata;
        this.operatorUI = operatorUI;

        this.node = node;
        this.name = this.node.getId();
        this.configuration = configuration;
        numInputs = metadata.getMinNumberOfInputs();
        height = Math.max(height, connectionOffset * (numInputs + 1));

        config = new XppDomElement("parameters");
    }

    @NotNull
    private XppDomElement updateParameters() {
        XppDomElement update = new XppDomElement("parameters");
        this.operatorUI.updateParameters();
        try {
            this.operatorUI.convertToDOM(update);
        } catch (GraphException e) {
            e.printStackTrace();
            return new XppDomElement("parameters");
        }
        return update;
    }

    public void paintNode(@NotNull Graphics2D g) {
        g.setFont(textFont);
        
        if (textW <= 0) {
            FontMetrics fontMetrics = g.getFontMetrics();
                    
            textH = fontMetrics.getHeight();
            textW = fontMetrics.stringWidth(name);

            width = Math.max(GridUtils.floor(textW + 30), minWidth);
        }

        if ((this.status & STATUS_MASK_SELECTED) > 0) {
            Graphics2D gactive = (Graphics2D)g.create();
            gactive.setColor(activeColor);
            gactive.setStroke(activeStroke);
            gactive.drawRoundRect(x-2, y-2, width + 4,  height + 4, 8, 8);
            gactive.dispose();
        }

        g.setColor(this.color());
        g.fillRoundRect(x, y, width, height, 8, 8);
        g.setStroke(borderStroke);
        g.setColor(this.borderColor());
        g.drawRoundRect(x, y, width, height, 8, 8);
        
        g.setStroke(textStroke);
        g.setColor(Color.darkGray);

        g.drawString(name, x + (width - textW) / 2 , y + (5 + textH));
        
        paintInputs(g);
        paintOutput(g);
    }

    public void paintConnections(Graphics2D g) {
        for (Connection c: connections) {
            if (c != null)
                c.draw(g);
        }
    }

    @NotNull
    static private ArrayList<String> split_line(@NotNull String line) {
        ArrayList<String> result = new ArrayList<>();

        if (line.length() <= MAX_LINE_LENGTH) {
            result.add(line);
        } else {
            int start = 0;
            int N = (int)Math.ceil((double)line.length() / MAX_LINE_LENGTH);
            for (int i = 0; i < N; i++) {
                int end = Math.min(start + MAX_LINE_LENGTH, line.length());
                String subline = line.substring(start, end);
                if (end < line.length()
                        && Character.isLetter(line.charAt(end-1))
                        && Character.isLetter(line.charAt(end))) {
                    subline += "-";
                }
                result.add(subline);
                start = end;
            }
        }
        return  result;
    }

    @Contract("null -> null")
    static private String[] split_text(String input) {
        if (input == null) return null;
        ArrayList<String> result = new ArrayList<>();
        for (String line: input.split("\n")) {
            result.addAll(split_line(line));
        }
        return result.toArray(new String[0]);
    }

    public void tooltip(Graphics2D g) {
        if (tooltipVisible_ && tooltipText_ != null) {
            FontMetrics fontMetrics = g.getFontMetrics();

            int textH = fontMetrics.getHeight();
            int tooltipH = textH + (textH+4) * (tooltipText_.length - 1) + 8;

            int tooltipW = fontMetrics.stringWidth(tooltipText_[0]) + 8;
            for (int i = 1; i < tooltipText_.length; i++) {
                tooltipW = Math.max(tooltipW, fontMetrics.stringWidth(tooltipText_[i]) + 8);
            }

            int tx;
            int ty;
            if (tooltipIndex_ == CONNECTION_OUTPUT) {
                tx = x + width + connectionSize;
                ty = y + connectionOffset - (tooltipH / 2);
            } else {
                tx = x - tooltipW - connectionSize;
                ty = y + (tooltipIndex_ + 1) * connectionOffset - (tooltipH / 2);
            }
            g.setColor(tooltipBackground);
            g.fillRoundRect(tx, ty, tooltipW, tooltipH, 8, 8);
            g.setStroke(tooltipStroke);
            g.setColor(tooltipBorder);
            g.drawRoundRect(tx, ty, tooltipW, tooltipH, 8, 8);
            g.setStroke(textStroke);
            g.setColor(tooltipColor);
            int stringY = ty + 8 + (textH / 2);
            for (String line: tooltipText_){
                g.drawString(line, tx + 4, stringY);
                stringY += textH + 4;
            }
        }
    }

    private void paintInputs(Graphics2D g) {
        if (metadata.hasInputs()) {
            int xc = x - connectionHalfSize;
            int yc = y + connectionOffset - connectionHalfSize;
            for (int i = 0; i < numInputs(); i++) {
                g.setColor(Color.white);
                g.fillOval(xc, yc, connectionSize, connectionSize);
                g.setStroke(borderStroke);
                g.setColor(borderColor());
                g.drawOval(xc, yc, connectionSize, connectionSize);
                if (i >= metadata.getMinNumberOfInputs()) {
                    g.setColor(Color.lightGray);
                    g.setStroke(textStroke);
                    g.fillOval(xc + 3, yc + 3, connectionSize - 6, connectionSize - 6);
                    g.drawOval(xc + 3, yc + 3, connectionSize - 6, connectionSize - 6);
                }
                yc += connectionOffset;
            }
        }
    }

    @Contract(pure = true)
    private int numInputs() {
        return numInputs;
    }  

    private void paintOutput(Graphics2D g) {
        if (metadata.hasOutput()) {
            int xc = x + width - connectionHalfSize;
            int yc = y + connectionOffset - connectionHalfSize;
            g.setColor(Color.white);
            g.fillRect(xc, yc, connectionSize, connectionSize);
            g.setStroke(borderStroke);
            g.setColor(borderColor());
            g.drawRect(xc, yc, connectionSize, connectionSize);
        }        
    }

    private Color color() {
        if ((this.status & STATUS_MASK_OVER) > 0) {
            return unknownColor.brighter();
        }
        return unknownColor;
    }
    
    private Color borderColor() {
        return color().darker().darker();
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setPosition(int x, int y){
        this.x = x;
        this.y = y;
    }

    public void setPosition(@NotNull Point p) {
        this.x = p.x;
        this.y = p.y;
    }

    public Point getPostion() {
        return new Point(x, y);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getName() {
        return name;
    }

    private int getInputIndex(@NotNull Point p) {
        int dx = p.x - x;
        int dy = p.y - y;
        if (Math.abs(dx) <= connectionHalfSize && dy > 0) {
            int iy = Math.round((float) dy / connectionOffset);
            if (iy - 1 < numInputs()) {
                int cy = iy * connectionOffset;
                if (Math.abs(dy - cy) <= connectionHalfSize) {
                    return iy - 1;
                }
            }
        }
        return -1;
    }

    private boolean isOverOutput(@NotNull Point p) {
        int dx = p.x - x;
        int dy = p.y - y;
        return (metadata.hasOutput()
                && Math.abs(dx - width) <= connectionHalfSize
                && Math.abs(dy - connectionOffset) <= connectionHalfSize);
    }

    public boolean contains(@NotNull Point p) {
        int dx = p.x - x;
        int dy = p.y - y;
        boolean inside = (dx >= 0 && dy >= 0 && dx <= width && dy <= height);
        if (inside)
            return true;
        // check if is over a connection input
        if (getInputIndex(p) >= 0) return true;
        // check if is over a connection output
        return isOverOutput(p);
    }

    public void over(Point p) {
        if ((status & STATUS_MASK_OVER) == 0) 
            status += STATUS_MASK_OVER;

        int iy = getConnectionAt(p);
        if (iy != CONNECTION_NONE) {
            show_tooltip(iy);
            return;
        }
        hide_tooltip();
    }

    public void none() {
        if ((status & STATUS_MASK_OVER) > 0) 
            status -= STATUS_MASK_OVER;
        hide_tooltip();
    }

    public void select() {
        if ((status & STATUS_MASK_SELECTED) == 0) 
            status += STATUS_MASK_SELECTED;
        System.out.println("\033[1;32m>> SELECTED\033[0m");
        if (hasInputsChanged) {
            // TODO UPDATE inputs...
            // Remove Sources
            Product products[] = new Product[connections.size()];
            for (int i = 0; i < products.length; i++) {
                products[i] = connections.get(i).getSourceProduct();
            }
            operatorUI.setSourceProducts(products);
            operatorUI.updateParameters();
            hasInputsChanged = false;
        }
    }

    public Product getProduct() {
        if (operatorUI.validateParameters().getState() == UIValidation.State.OK) {
            operatorUI.initParameters();
        }
        return output;
    }

    public void deselect() {
        if ((status & STATUS_MASK_SELECTED) > 0)
            status -= STATUS_MASK_SELECTED;
        if (check_changes()) {
            System.out.println("\033[1;32m>> SOMETHING CHANGED\033[0m");
            for (NodeListener l : nodeListeners) {
                l.outputChanged(this);
            }
        }
    }

    private boolean equals(DomElement a, DomElement b){
        if (b.getName() == a.getName()) {
            if (b.getValue() == a.getValue()) {
                if (a.getChildCount() == b.getChildCount()) {
                    for (int i = 0; i < a.getChildCount(); i++) {
                        if (!equals(a.getChild(i), b.getChild(i))) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean check_changes() {
        XppDomElement update = updateParameters();
        boolean res =  equals(config, update);
        config = update;
        return !res;
    }

    public void disconnect(int index) {
        for (int i = index + 1; i < this.connections.size(); i++) {
            this.connections.get(i).setTargetIndex(i - 1);
        }

        numInputs = Math.max(metadata.getMinNumberOfInputs(), numInputs - 1);
        height = (numInputs + 1) * connectionOffset;
        this.connections.get(index).getSource().removeNodeListener(this);
        this.connections.remove(index);
        hasInputsChanged = true;
    }

    public NodeDragAction drag(Point p) {
        int iy = getInputIndex(p);
        if (iy >= 0) {
            if (this.connections.size() > iy) {
                Connection c = this.connections.get(iy);
                if (c != null) {
                    disconnect(iy);
                    c.showSourceTooltip();
                    return new NodeDragAction(new Connection(c.getSource(), p));
                }
            }

            return new NodeDragAction(new Connection(this, iy, p));
        }
        if (isOverOutput(p)) {
            return new NodeDragAction(new Connection(this, p));
        }
        return new NodeDragAction(this,  p);
    }

    private void hide_tooltip() {
        tooltipVisible_ = false;
        tooltipText_ = null;
        tooltipIndex_ = CONNECTION_NONE;
    }

    private void show_tooltip(int connectionIndex) {
        if (connectionIndex == CONNECTION_OUTPUT && metadata.hasOutput()) {
            // OUTPUT
            tooltipVisible_ = true;
            tooltipText_ = split_text(metadata.getOutputDescription());
            tooltipIndex_ = connectionIndex;
        } else if (connectionIndex >= 0 && metadata.hasInputs()) {
            // INPUT
            tooltipVisible_ = true;
            tooltipText_ = split_text(metadata.getInputDescription(connectionIndex));
            tooltipIndex_ = connectionIndex;
        } else {
            hide_tooltip();
        }
    }

    public JComponent getPreferencePanel(AppContext context){
        if (preferencePanel == null) {
            try {
                preferencePanel = operatorUI.CreateOpTab(this.metadata.getName(), configuration ,context);
            } catch (Exception e) {
                SystemUtils.LOG.info(e.getMessage());
                preferencePanel = null;
                return null;
            }
        }
        return preferencePanel;
    }

    public Point getInputPosition(int index) {
        return new Point(x, y + connectionOffset * (index + 1));
    }

    public Point getOutputPosition() {
        return new Point(x + width, y + connectionOffset);
    }

    public boolean hasTooltip() {
        return tooltipVisible_;
    }

    public int getConnectionAt(Point p) {
        int iy = getInputIndex(p);
        if (iy >= 0) {
            return iy;
        }
        if (isOverOutput(p)) {
            return CONNECTION_OUTPUT;
        }
        return CONNECTION_NONE;
    }

    public boolean isConnectionAvailable(int index, NodeGui other) {
        if (index == CONNECTION_OUTPUT)
            return true;
        if (index == CONNECTION_NONE)
            return false;
        for (Connection c: connections) {
            if (c != null && other == c.getSource()) {
                return false;
            }
        }
        return (connections.size() == index && (metadata.getMaxNumberOfInputs() <0 || index < metadata.getMaxNumberOfInputs()));
    }

    private void connect(Connection c){
        connections.add(c);
        c.getSource().addNodeListener(this);
        hasInputsChanged = true;
    }

    public void addConnection(Connection connection, int index) {
        if (index == connections.size())  {
            connect(connection);
        } else {
            return;
        }

        if (metadata.getMaxNumberOfInputs() == -1) {
            if (connections.size() < metadata.getMinNumberOfInputs()) {
                numInputs = metadata.getMinNumberOfInputs();
            } else if (metadata.getMaxNumberOfInputs() > 0 ) {
                numInputs = Math.min(metadata.getMaxNumberOfInputs(), numInputs + 1);
            }else {
                numInputs = connections.size() + 1;
            }

            height = (numInputs + 1) * connectionOffset;
        }
    }

    @Override
    public void outputChanged(NodeGui source) {
        hasInputsChanged = true;
    }

    public void addNodeListener(NodeListener l) {
        nodeListeners.add(l);
    }

    public void removeNodeListener(NodeListener l) {
        nodeListeners.remove(l);
    }
}