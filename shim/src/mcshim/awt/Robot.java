package mcshim.awt;

/** Only pointer warping is used (Indev's mouse look): see Mouse.awtWarp. */
public class Robot {
    public Robot() throws AWTException {}
    public void mouseMove(int x, int y) { mcshim.lwjgl.input.Mouse.awtWarp(x, y); }
}
