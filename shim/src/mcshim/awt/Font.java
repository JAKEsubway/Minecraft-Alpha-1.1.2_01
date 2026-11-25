package mcshim.awt;

public class Font {
    public static final int PLAIN = 0, BOLD = 1, ITALIC = 2;
    private final String name; private final int style, size;
    public Font(String name, int style, int size) { this.name = name; this.style = style; this.size = size; }
    public int getSize() { return size; }
    public String getName() { return name; }
    public int getStyle() { return style; }
}
