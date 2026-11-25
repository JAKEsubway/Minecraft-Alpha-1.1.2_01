package mcshim.awt;

public abstract class Image {
    public static final int SCALE_DEFAULT = 1;
    public abstract int getWidth(mcshim.awt.image.ImageObserver o);
    public abstract int getHeight(mcshim.awt.image.ImageObserver o);
    public Graphics getGraphics() { return new Graphics2D(null); }
    public void flush() {}
}
