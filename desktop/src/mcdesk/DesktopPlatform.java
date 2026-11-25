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
        Display.update();
        frames++;
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

    public void mouseGrabbed(boolean g) { grabbed = g; log("mouse grabbed=" + g); }
}
