package mcshim.awt;

public class RenderingHints {
    public static class Key {
        Key(int id) {}
    }
    public static final Key KEY_INTERPOLATION = new Key(1);
    public static final Key KEY_ANTIALIASING = new Key(2);
    public static final Object VALUE_INTERPOLATION_NEAREST_NEIGHBOR = new Object();
    public static final Object VALUE_ANTIALIAS_OFF = new Object();
}
