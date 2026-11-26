package mcdesk;

import java.io.*;
import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import mcshim.Platform;
import mcshim.awt.image.BufferedImage;
import mcshim.gl.GLBackend;

public class DesktopPlatform extends Platform {
    final int w, h;
    private final DesktopGL gl = new DesktopGL();
    volatile int frames;
    volatile String screenshotPath;
    volatile boolean pinkRequest;
    volatile int[] pink; // {minX, minY, maxX, maxY, cx, cy, count} of pig-pink pixels, y from top
    volatile String crashText;
    volatile boolean grabbed;

    DesktopPlatform(int w, int h) { this.w = w; this.h = h; }

    public GLBackend gl() { return gl; }

    public void createDisplay() throws Exception {
        Display.setDisplayMode(new DisplayMode(w, h));
        Display.create();
        log("GL: " + gl.glGetString(0x1F01) + " / " + gl.glGetString(0x1F02));
    }

    public void swapBuffers() {
        String shot = screenshotPath;
        if (shot != null) { screenshotPath = null; saveShot(shot); }
        if (pinkRequest) { pink = findPink(); pinkRequest = false; }
        Display.update();
        frames++;
    }

    /** Locate the pig on screen from the rendered frame (pink skin pixels). */
    int[] findPink() {
        ByteBuffer b = BufferUtils.createByteBuffer(w * h * 4);
        gl.glReadPixels(0, 0, w, h, 0x1908, 0x1401, b);
        int minX = w, minY = h, maxX = -1, maxY = -1, n = 0;
        long sx = 0, sy = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = ((h - 1 - y) * w + x) * 4;
            int r = b.get(i) & 255, g = b.get(i + 1) & 255, bl = b.get(i + 2) & 255;
            if (r > 180 && g > 100 && g < 200 && bl > 100 && bl < 200 && r - g > 35 && Math.abs(g - bl) < 35) {
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                sx += x; sy += y; n++;
            }
        }
        return n < 50 ? null : new int[] {minX, minY, maxX, maxY, (int) (sx / n), (int) (sy / n), n};
    }

    void saveShot(String path) {
        ByteBuffer b = BufferUtils.createByteBuffer(w * h * 4);
        gl.glReadPixels(0, 0, w, h, 0x1908, 0x1401, b);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = ((h - 1 - y) * w + x) * 4;
            img.setRGB(x, y, (b.get(i) & 255) << 16 | (b.get(i + 1) & 255) << 8 | (b.get(i + 2) & 255));
        }
        try { javax.imageio.ImageIO.write(img, "png", new File(path)); log("saved " + path); } catch (IOException e) { e.printStackTrace(); }
    }

    public int width() { return w; }
    public int height() { return h; }

    public BufferedImage decodeImage(InputStream in) throws IOException {
        java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(in);
        if (src == null) throw new IOException("undecodable image");
        int iw = src.getWidth(), ih = src.getHeight();
        BufferedImage out = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
        src.getRGB(0, 0, iw, ih, out.pixels(), 0, iw);
        return out;
    }

    public void crash(String report) { crashText = report; System.err.println("=== GAME CRASH ===\n" + report); }

    public mcshim.audio.AudioOut openAudio(int rate) {
        String path = System.getProperty("mcdesk.audio");
        if (path == null) return null;
        try { return new RawAudioOut(path, rate); } catch (java.io.IOException e) { return null; }
    }

    public void mouseGrabbed(boolean g) { grabbed = g; log("mouse grabbed=" + g); }
}
