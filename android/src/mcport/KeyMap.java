package mcport;

import android.view.KeyEvent;

/** Android key codes -> LWJGL key codes, for physical keyboards. */
final class KeyMap {
    private KeyMap() {}

    private static final int[] LETTERS = { // A..Z
        30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44
    };

    static int toLwjgl(int c) {
        if (c >= KeyEvent.KEYCODE_A && c <= KeyEvent.KEYCODE_Z) return LETTERS[c - KeyEvent.KEYCODE_A];
        if (c >= KeyEvent.KEYCODE_1 && c <= KeyEvent.KEYCODE_9) return 2 + (c - KeyEvent.KEYCODE_1);
        if (c >= KeyEvent.KEYCODE_F1 && c <= KeyEvent.KEYCODE_F10) return 59 + (c - KeyEvent.KEYCODE_F1);
        switch (c) {
            case KeyEvent.KEYCODE_0: return 11;
            case KeyEvent.KEYCODE_ESCAPE: return 1;
            case KeyEvent.KEYCODE_SPACE: return 57;
            case KeyEvent.KEYCODE_ENTER: return 28;
            case KeyEvent.KEYCODE_DEL: return 14;
            case KeyEvent.KEYCODE_TAB: return 15;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return 42;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return 54;
            case KeyEvent.KEYCODE_CTRL_LEFT: return 29;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return 157;
            case KeyEvent.KEYCODE_ALT_LEFT: return 56;
            case KeyEvent.KEYCODE_MINUS: return 12;
            case KeyEvent.KEYCODE_EQUALS: return 13;
            case KeyEvent.KEYCODE_LEFT_BRACKET: return 26;
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return 27;
            case KeyEvent.KEYCODE_SEMICOLON: return 39;
            case KeyEvent.KEYCODE_APOSTROPHE: return 40;
            case KeyEvent.KEYCODE_GRAVE: return 41;
            case KeyEvent.KEYCODE_BACKSLASH: return 43;
            case KeyEvent.KEYCODE_COMMA: return 51;
            case KeyEvent.KEYCODE_PERIOD: return 52;
            case KeyEvent.KEYCODE_SLASH: return 53;
            case KeyEvent.KEYCODE_DPAD_UP: return 200;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 208;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 203;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 205;
            case KeyEvent.KEYCODE_F11: return 87;
            case KeyEvent.KEYCODE_F12: return 88;
        }
        return 0;
    }
}
