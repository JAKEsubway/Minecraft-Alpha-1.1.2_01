package mcshim.awt;

public class PointerInfo {
    public Point getLocation() {
        return new Point(mcshim.lwjgl.input.Mouse.awtPointerX(), mcshim.lwjgl.input.Mouse.awtPointerY());
    }
}
