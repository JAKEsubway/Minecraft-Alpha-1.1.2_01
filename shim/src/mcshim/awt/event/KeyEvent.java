package mcshim.awt.event;

public class KeyEvent {
    private int code; private char ch;
    public KeyEvent() {}
    public int getKeyCode() { return code; }
    public char getKeyChar() { return ch; }
}
