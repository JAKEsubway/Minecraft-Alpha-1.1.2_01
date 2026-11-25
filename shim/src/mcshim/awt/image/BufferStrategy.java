package mcshim.awt.image;

import mcshim.awt.Graphics;
import mcshim.awt.Graphics2D;

public class BufferStrategy {
    public Graphics getDrawGraphics() { return new Graphics2D(null); }
    public void show() {}
    public boolean contentsLost() { return false; }
    public void dispose() {}
}
