package mcshim.awt;

import mcshim.awt.datatransfer.Clipboard;

public class Toolkit {
    private static final Toolkit T = new Toolkit();
    public static Toolkit getDefaultToolkit() { return T; }
    public Clipboard getSystemClipboard() { return new Clipboard(); }
    public Dimension getScreenSize() { return new Dimension(mcshim.Platform.get().width(), mcshim.Platform.get().height()); }
    public void sync() {}
}
