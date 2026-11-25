package mcshim.awt.event;

public class MouseEvent {
    private int x, y, clicks, button;
    public MouseEvent() {}
    public int getX() { return x; }
    public int getY() { return y; }
    public int getClickCount() { return clicks; }
    public int getButton() { return button; }
}
