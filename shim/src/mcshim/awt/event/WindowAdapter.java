package mcshim.awt.event;

public abstract class WindowAdapter implements WindowListener {
    public void windowClosing(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowOpened(WindowEvent e) {}
}
