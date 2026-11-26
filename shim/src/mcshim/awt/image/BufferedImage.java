package mcshim.awt.image;

import mcshim.awt.Graphics;
import mcshim.awt.Graphics2D;
import mcshim.awt.Image;

/** int[] ARGB image. TYPE_INT_RGB images report opaque alpha when read. */
public class BufferedImage extends Image implements RenderedImage {
    public static final int TYPE_CUSTOM = 0, TYPE_INT_RGB = 1, TYPE_INT_ARGB = 2, TYPE_INT_ARGB_PRE = 3, TYPE_INT_BGR = 4;
    private final int w, h, type;
    private final int[] px;
    private WritableRaster raster;

    public BufferedImage(int w, int h, int type) { this(w, h, type, new int[w * h]); }

    public BufferedImage(int w, int h, int type, int[] argb) {
        this.w = w; this.h = h; this.type = type; this.px = argb;
    }

    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public int getWidth(ImageObserver o) { return w; }
    public int getHeight(ImageObserver o) { return h; }
    public int getType() { return type; }

    /** Raw backing array (for Graphics and the platform image decoder). */
    public int[] pixels() { return px; }

    public int readPixel(int raw) { return type == TYPE_INT_RGB ? raw | 0xFF000000 : raw; }

    public int getRGB(int x, int y) { return readPixel(px[y * w + x]); }

    public void setRGB(int x, int y, int argb) { px[y * w + x] = argb; }

    public int[] getRGB(int sx, int sy, int cw, int ch, int[] out, int off, int scan) {
        if (out == null) out = new int[off + ch * scan];
        for (int y = 0; y < ch; y++) {
            int src = (sy + y) * w + sx, dst = off + y * scan;
            for (int x = 0; x < cw; x++) out[dst + x] = readPixel(px[src + x]);
        }
        return out;
    }

    public void setRGB(int sx, int sy, int cw, int ch, int[] in, int off, int scan) {
        for (int y = 0; y < ch; y++) System.arraycopy(in, off + y * scan, px, (sy + y) * w + sx, cw);
    }

    public Graphics getGraphics() { return new Graphics2D(this); }
    public Graphics2D createGraphics() { return new Graphics2D(this); }

    public WritableRaster getRaster() {
        if (raster == null) raster = new WritableRaster(new DataBufferInt(px));
        return raster;
    }

    public BufferedImage getSubimage(int x, int y, int cw, int ch) {
        BufferedImage b = new BufferedImage(cw, ch, type);
        for (int yy = 0; yy < ch; yy++) System.arraycopy(px, (y + yy) * w + x, b.px, yy * cw, cw);
        return b;
    }
}
