package mcshim.awt;

import mcshim.awt.geom.AffineTransform;
import mcshim.awt.image.BufferedImage;

public class Graphics2D extends Graphics {
    private AffineTransform xf = new AffineTransform();
    public Graphics2D(BufferedImage target) { super(target); }
    public void scale(double sx, double sy) {}
    public AffineTransform getTransform() { return xf; }
    public void setTransform(AffineTransform t) { if (t != null) xf = t; }
    public void setRenderingHint(RenderingHints.Key k, Object v) {}
}
