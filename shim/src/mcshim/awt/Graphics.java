package mcshim.awt;

import mcshim.awt.image.BufferedImage;
import mcshim.awt.image.ImageObserver;

/** Software drawing into a BufferedImage (or nowhere, for screen graphics). */
public class Graphics {
    protected final BufferedImage target;
    protected Color color = Color.BLACK;
    protected Font font;
    protected int tx, ty;

    protected Graphics(BufferedImage target) { this.target = target; }

    public void setColor(Color c) { if (c != null) color = c; }
    public Color getColor() { return color; }
    public void setFont(Font f) { font = f; }
    public Font getFont() { return font; }
    public FontMetrics getFontMetrics() { return new FontMetrics(font); }
    public FontMetrics getFontMetrics(Font f) { return new FontMetrics(f); }
    public void translate(int x, int y) { tx += x; ty += y; }
    public void dispose() {}
    public void drawString(String s, int x, int y) {}
    public void drawRect(int x, int y, int w, int h) {}
    public void drawLine(int x1, int y1, int x2, int y2) {}
    public void setClip(int x, int y, int w, int h) {}
    public Rectangle getClipBounds() {
        return target == null ? new Rectangle(0, 0, 0, 0) : new Rectangle(0, 0, target.getWidth(), target.getHeight());
    }

    public void fillRect(int x, int y, int w, int h) {
        if (target == null) return;
        int[] px = target.pixels();
        int tw = target.getWidth(), th = target.getHeight();
        int c = color.getRGB();
        x += tx; y += ty;
        for (int yy = Math.max(0, y); yy < Math.min(th, y + h); yy++)
            for (int xx = Math.max(0, x); xx < Math.min(tw, x + w); xx++) px[yy * tw + xx] = c;
    }

    public void clearRect(int x, int y, int w, int h) {
        Color old = color; color = new Color(0, true); fillRect(x, y, w, h); color = old;
    }

    public boolean drawImage(Image img, int x, int y, ImageObserver o) {
        if (target == null || !(img instanceof BufferedImage)) return true;
        BufferedImage src = (BufferedImage) img;
        int sw = src.getWidth(), sh = src.getHeight();
        return drawImage(img, x, y, sw, sh, o);
    }

    public boolean drawImage(Image img, int x, int y, Color bg, ImageObserver o) { return drawImage(img, x, y, o); }

    public boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver o) {
        if (target == null || !(img instanceof BufferedImage)) return true;
        BufferedImage src = (BufferedImage) img;
        int sw = src.getWidth(), sh = src.getHeight();
        int[] s = src.pixels(), d = target.pixels();
        int tw = target.getWidth(), th = target.getHeight();
        x += tx; y += ty;
        for (int yy = 0; yy < h; yy++) {
            int dy = y + yy;
            if (dy < 0 || dy >= th) continue;
            int sy = yy * sh / h;
            for (int xx = 0; xx < w; xx++) {
                int dx = x + xx;
                if (dx < 0 || dx >= tw) continue;
                int sp = src.readPixel(s[sy * sw + xx * sw / w]);
                int a = sp >>> 24;
                if (a == 255) d[dy * tw + dx] = sp;
                else if (a != 0) d[dy * tw + dx] = blend(sp, d[dy * tw + dx]);
            }
        }
        return true;
    }

    static int blend(int s, int d) {
        int sa = s >>> 24, da = d >>> 24;
        int oa = sa + da * (255 - sa) / 255;
        if (oa == 0) return 0;
        int r = ((s >> 16 & 255) * sa + (d >> 16 & 255) * da * (255 - sa) / 255) / oa;
        int g = ((s >> 8 & 255) * sa + (d >> 8 & 255) * da * (255 - sa) / 255) / oa;
        int b = ((s & 255) * sa + (d & 255) * da * (255 - sa) / 255) / oa;
        return oa << 24 | r << 16 | g << 8 | b;
    }
}
