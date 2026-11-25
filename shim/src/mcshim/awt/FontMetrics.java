package mcshim.awt;

public class FontMetrics {
    private final int size;
    public FontMetrics(Font f) { size = f == null ? 12 : f.getSize(); }
    public int stringWidth(String s) { return s == null ? 0 : s.length() * size * 6 / 10; }
    public int getHeight() { return size; }
    public int getAscent() { return size * 8 / 10; }
}
