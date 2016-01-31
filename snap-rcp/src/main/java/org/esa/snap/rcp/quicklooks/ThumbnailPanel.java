/*
 * Copyright (C) 2016 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.snap.rcp.quicklooks;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;


/**
 * Displays a list of thumbnails
 */
public class ThumbnailPanel extends JPanel {

    private final static int imgWidth = 150;
    private final static int imgHeight = 150;
    private final static int margin = 4;
    private final boolean multiRow;

    private enum SelectionMode {CHECK, RECT}

    private SelectionMode selectionMode = SelectionMode.CHECK;

    private ThumbnailDrawing selection = null;

    public ThumbnailPanel(final boolean multiRow, final Thumbnail[] imageList) {
        super(new FlowLayout(FlowLayout.LEADING));
        this.multiRow = multiRow;
        update(imageList);
    }

    public void update(final Thumbnail[] imageList) {
        this.removeAll();

        final Insets insets = getInsets();
        final int width = getWidth() - (insets.left + insets.right);

        if (imageList.length == 0) {
            setPreferredSize(new Dimension(width, imgHeight + 2 * margin));
            JLabel label = new JLabel("Nothing to see here");
            this.add(label);
        } else {
            if (multiRow) {
                int numImages = 1;
                int effecitveImageWidth = imgWidth * numImages + margin;

                int numCol = Math.max(width / effecitveImageWidth, 1);
                int numRow = (int)Math.ceil(imageList.length / (double)numCol);

                int preferedWidth = effecitveImageWidth * numCol + margin;
                int preferedHeight = (imgHeight + margin) * numRow + margin;
                setPreferredSize(new Dimension(preferedWidth, preferedHeight));
            }

            for (Thumbnail thumbnail : imageList) {
                this.add(new ThumbnailDrawing(thumbnail));
            }
        }
        updateUI();
    }

    public class ThumbnailDrawing extends JLabel implements MouseListener {
        private ImageIcon icon1;

        public ThumbnailDrawing(final Thumbnail thumbnail) {

            setPreferredSize(new Dimension(imgWidth, imgHeight));

            addMouseListener(this);
        }

        @Override
        public void paintComponent(Graphics graphics) {
            final Graphics2D g = (Graphics2D) graphics;

            drawIcon(g, icon1, 0);

            if (this.equals(selection) && selectionMode == SelectionMode.RECT) {
                g.setColor(Color.CYAN);
                g.setStroke(new BasicStroke(5));
                g.drawRoundRect(0, 0, imgWidth, imgHeight - 5, 25, 25);
            }
        }

        private void drawIcon(Graphics2D g, ImageIcon icon, int xOff) {
            if (icon != null && icon.getImage() != null) {
                g.drawImage(icon.getImage(), xOff, 0, imgWidth, imgHeight, null);
            } else {
                // Draw cross to indicate missing image
                g.setColor(Color.DARK_GRAY);
                g.setStroke(new BasicStroke(1));
                g.drawLine(xOff, 0, xOff + imgWidth, imgHeight);
                g.drawLine(xOff + imgWidth, 0, xOff, imgHeight);
                g.drawRect(xOff, 0, imgWidth-1, imgHeight-1);
            }
        }

        /**
         * Invoked when the mouse button has been clicked (pressed
         * and released) on a component.
         */
        @Override
        public void mouseClicked(MouseEvent e) {

        }

        /**
         * Invoked when a mouse button has been pressed on a component.
         */
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {

                repaint();
            } else if (e.getButton() == MouseEvent.BUTTON3) {

            }
        }

        /**
         * Invoked when a mouse button has been released on a component.
         */
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        /**
         * Invoked when the mouse enters a component.
         */
        @Override
        public void mouseEntered(MouseEvent e) {
        }

        /**
         * Invoked when the mouse exits a component.
         */
        @Override
        public void mouseExited(MouseEvent e) {
        }
    }
}