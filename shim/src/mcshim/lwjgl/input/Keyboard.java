package mcshim.lwjgl.input;

import java.util.ArrayDeque;

/** LWJGL 2 keyboard, fed by the platform's touch/key layer. */
public final class Keyboard {
    private Keyboard() {}

    public static final int KEY_NONE = 0, KEY_ESCAPE = 1, KEY_1 = 2, KEY_2 = 3, KEY_3 = 4, KEY_4 = 5, KEY_5 = 6,
            KEY_6 = 7, KEY_7 = 8, KEY_8 = 9, KEY_9 = 10, KEY_0 = 11, KEY_BACK = 14, KEY_TAB = 15, KEY_Q = 16,
            KEY_W = 17, KEY_E = 18, KEY_T = 20, KEY_I = 23, KEY_RETURN = 28, KEY_LCONTROL = 29, KEY_A = 30,
            KEY_S = 31, KEY_D = 32, KEY_F = 33, KEY_LSHIFT = 42, KEY_SPACE = 57, KEY_F1 = 59, KEY_F3 = 61,
            KEY_F5 = 63, KEY_UP = 200, KEY_LEFT = 203, KEY_RIGHT = 205, KEY_DOWN = 208;

    public static final int KEYBOARD_SIZE = 256;
    private static final String[] NAMES = new String[KEYBOARD_SIZE];

    static {
        String[] n = {
            null, "ESCAPE", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "MINUS", "EQUALS", "BACK", "TAB",
            "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "LBRACKET", "RBRACKET", "RETURN", "LCONTROL", "A", "S",
            "D", "F", "G", "H", "J", "K", "L", "SEMICOLON", "APOSTROPHE", "GRAVE", "LSHIFT", "BACKSLASH", "Z", "X", "C", "V",
            "B", "N", "M", "COMMA", "PERIOD", "SLASH", "RSHIFT", "MULTIPLY", "LMENU", "SPACE", "CAPITAL", "F1", "F2", "F3", "F4", "F5",
            "F6", "F7", "F8", "F9", "F10", "NUMLOCK", "SCROLL", "NUMPAD7", "NUMPAD8", "NUMPAD9", "SUBTRACT", "NUMPAD4", "NUMPAD5", "NUMPAD6", "ADD", "NUMPAD1",
            "NUMPAD2", "NUMPAD3", "NUMPAD0", "DECIMAL"
        };
        System.arraycopy(n, 0, NAMES, 0, n.length);
        NAMES[87] = "F11"; NAMES[88] = "F12"; NAMES[156] = "NUMPADENTER"; NAMES[157] = "RCONTROL";
        NAMES[184] = "RMENU"; NAMES[199] = "HOME"; NAMES[200] = "UP"; NAMES[201] = "PRIOR"; NAMES[203] = "LEFT";
        NAMES[205] = "RIGHT"; NAMES[207] = "END"; NAMES[208] = "DOWN"; NAMES[209] = "NEXT"; NAMES[210] = "INSERT";
        NAMES[211] = "DELETE";
    }

    private static final class Ev { int key; boolean down; char ch; }

    private static final ArrayDeque<Ev> pending = new ArrayDeque<Ev>();
    private static final ArrayDeque<Ev> ready = new ArrayDeque<Ev>();
    private static final boolean[] state = new boolean[KEYBOARD_SIZE];
    private static Ev cur;

    // ---- producer side (any thread) ----
    public static void inject(int key, boolean down, char ch) {
        Ev e = new Ev();
        e.key = key; e.down = down; e.ch = ch;
        synchronized (pending) { pending.add(e); }
    }

    public static void tap(int key, char ch) {
        inject(key, true, ch);
        inject(key, false, ch);
    }

    /** Peek at what the game thread currently believes (for the overlay). */
    public static boolean isDownRaw(int key) { return key >= 0 && key < KEYBOARD_SIZE && state[key]; }

    // ---- LWJGL API ----
    public static void poll() {
        synchronized (pending) {
            Ev e;
            while ((e = pending.poll()) != null) {
                if (e.key > 0 && e.key < KEYBOARD_SIZE) state[e.key] = e.down;
                ready.add(e);
            }
        }
    }

    public static void create() {}
    public static void destroy() {}
    public static boolean isCreated() { return true; }
    public static void enableRepeatEvents(boolean b) {}

    public static boolean next() {
        cur = ready.poll();
        return cur != null;
    }

    public static int getEventKey() { return cur == null ? 0 : cur.key; }
    public static boolean getEventKeyState() { return cur != null && cur.down; }
    public static char getEventCharacter() { return cur == null ? 0 : cur.ch; }
    public static boolean isKeyDown(int key) { return key >= 0 && key < KEYBOARD_SIZE && state[key]; }

    public static String getKeyName(int key) {
        String n = key >= 0 && key < KEYBOARD_SIZE ? NAMES[key] : null;
        return n == null ? "KEY" + key : n;
    }

    public static int getKeyIndex(String name) {
        for (int i = 0; i < KEYBOARD_SIZE; i++) if (name.equals(NAMES[i])) return i;
        return 0;
    }
}
