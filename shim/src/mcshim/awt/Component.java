package mcshim.awt;

import mcshim.awt.event.KeyListener;
import mcshim.awt.event.MouseListener;
import mcshim.awt.event.MouseMotionListener;
import mcshim.awt.image.BufferStrategy;

public class Component implements mcshim.awt.image.ImageObserver {
    int w, h;
    Container parent;
    private Font font;
    private Color bg = Color.WHITE, fg = Color.BLACK;
    private BufferStrategy strategy;

    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public Dimension getSize() { return new Dimension(getWidth(), getHeight()); }
    public void setSize(int w, int h) { this.w = w; this.h = h; }
    public void setSize(Dimension d) { setSize(d.width, d.height); }
    public void setBounds(int x, int y, int w, int h) { setSize(w, h); }
    public void setPreferredSize(Dimension d) { if (w == 0 && h == 0) setSize(d); }
    public Dimension getPreferredSize() { return getSize(); }
    public void setMinimumSize(Dimension d) {}
    public void setFocusable(boolean b) {}
    public void requestFocus() {}
    public boolean hasFocus() { return true; }
    public void addNotify() {}
    public void removeNotify() {}
    public void paint(Graphics g) {}
    public void update(Graphics g) { paint(g); }
    public void repaint() {}
    public Graphics getGraphics() { return new Graphics2D(null); }
    public void setBackground(Color c) { bg = c; }
    public Color getBackground() { return bg; }
    public void setForeground(Color c) { fg = c; }
    public Color getForeground() { return fg; }
    public void setFont(Font f) { font = f; }
    public Font getFont() { return font; }
    public FontMetrics getFontMetrics(Font f) { return new FontMetrics(f); }
    public void setVisible(boolean b) {}
    public boolean isVisible() { return true; }
    public boolean isDisplayable() { return true; }
    public boolean isShowing() { return true; }
    public void setEnabled(boolean b) {}
    public Container getParent() { return parent; }
    public void invalidate() {}
    public void validate() {}
    public void doLayout() {}
    public void addKeyListener(KeyListener l) {}
    public void addMouseListener(MouseListener l) {}
    public void addMouseMotionListener(MouseMotionListener l) {}
    public void removeKeyListener(KeyListener l) {}
    public void removeMouseListener(MouseListener l) {}
    public void removeMouseMotionListener(MouseMotionListener l) {}
    public void setCursor(Cursor c) {}
    public void createBufferStrategy(int n) { strategy = new BufferStrategy(); }
    public BufferStrategy getBufferStrategy() { if (strategy == null) strategy = new BufferStrategy(); return strategy; }
    public Image createImage(int w, int h) { return new mcshim.awt.image.BufferedImage(w, h, 2); }
    public Toolkit getToolkit() { return Toolkit.getDefaultToolkit(); }
    public void setName(String n) {}
    public boolean imageUpdate(Image img, int flags, int x, int y, int w, int h) { return false; }
}
