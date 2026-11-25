package mcshim.awt;

public class Cursor {
    public Cursor(int type) {}
    public static Cursor getPredefinedCursor(int t) { return new Cursor(t); }
}
