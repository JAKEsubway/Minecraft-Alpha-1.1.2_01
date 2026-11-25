package mcshim.awt;

public class Color {
    public static final Color white = new Color(0xFFFFFF), WHITE = white;
    public static final Color black = new Color(0x000000), BLACK = black;
    public static final Color red = new Color(0xFF0000), RED = red;
    public static final Color gray = new Color(0x808080), GRAY = gray;
    public static final Color darkGray = new Color(0x404040), DARK_GRAY = darkGray;
    private final int argb;

    public Color(int rgb) { argb = 0xFF000000 | rgb; }
    public Color(int argb, boolean hasAlpha) { this.argb = hasAlpha ? argb : 0xFF000000 | argb; }
    public Color(int r, int g, int b) { this(r, g, b, 255); }
    public Color(int r, int g, int b, int a) { argb = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255); }
    public Color(float r, float g, float b) { this((int) (r * 255 + .5f), (int) (g * 255 + .5f), (int) (b * 255 + .5f)); }
    public int getRGB() { return argb; }
    public int getRed() { return argb >> 16 & 255; }
    public int getGreen() { return argb >> 8 & 255; }
    public int getBlue() { return argb & 255; }
    public int getAlpha() { return argb >>> 24; }

    public static int HSBtoRGB(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - (saturation * (1.0f - f)));
            switch ((int) h) {
                case 0: r = (int) (brightness * 255 + .5f); g = (int) (t * 255 + .5f); b = (int) (p * 255 + .5f); break;
                case 1: r = (int) (q * 255 + .5f); g = (int) (brightness * 255 + .5f); b = (int) (p * 255 + .5f); break;
                case 2: r = (int) (p * 255 + .5f); g = (int) (brightness * 255 + .5f); b = (int) (t * 255 + .5f); break;
                case 3: r = (int) (p * 255 + .5f); g = (int) (q * 255 + .5f); b = (int) (brightness * 255 + .5f); break;
                case 4: r = (int) (t * 255 + .5f); g = (int) (p * 255 + .5f); b = (int) (brightness * 255 + .5f); break;
                case 5: r = (int) (brightness * 255 + .5f); g = (int) (p * 255 + .5f); b = (int) (q * 255 + .5f); break;
            }
        }
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }
}
