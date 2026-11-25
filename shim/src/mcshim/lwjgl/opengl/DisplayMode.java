package mcshim.lwjgl.opengl;

public final class DisplayMode {
    private final int w, h;
    public DisplayMode(int w, int h) { this.w = w; this.h = h; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public int getBitsPerPixel() { return 32; }
    public int getFrequency() { return 60; }
    public String toString() { return w + " x " + h; }
}
