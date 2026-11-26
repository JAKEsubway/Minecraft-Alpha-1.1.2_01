package mcshim.awt;

/** Pointer warping is meaningless on a touch screen. */
public class Robot {
    public Robot() throws AWTException {}
    public void mouseMove(int x, int y) {}
}
