package mcport;

import android.content.Context;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import mcshim.lwjgl.input.Keyboard;

/**
 * Invisible view that owns the soft keyboard while the game wants text
 * (chat, signs, server address). Typed characters become key events.
 */
final class TextInput extends View {
    private static final int K_BACK = 14, K_RETURN = 28;
    private boolean shown;

    TextInput(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public boolean onCheckIsTextEditor() { return true; }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo out) {
        // plain ASCII-ish keyboard, no suggestions, no fullscreen editor covering the game
        out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        out.imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new Conn();
    }

    void setShown(boolean show) {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null || show == shown) return;
        shown = show;
        if (show) {
            requestFocus();
            imm.restartInput(this);
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
            clearFocus();
        }
    }

    boolean isShown2() { return shown; }

    private static void typeChars(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n') Keyboard.tap(K_RETURN, '\r');
            else Keyboard.tap(0, ch);
        }
    }

    private final class Conn extends BaseInputConnection {
        private String composing = "";

        Conn() { super(TextInput.this, false); }

        private void dropComposing() {
            for (int i = 0; i < composing.length(); i++) Keyboard.tap(K_BACK, '\b');
            composing = "";
        }

        @Override
        public boolean commitText(CharSequence text, int pos) {
            dropComposing();
            typeChars(text);
            return true;
        }

        @Override
        public boolean setComposingText(CharSequence text, int pos) {
            String t = text.toString();
            int same = 0;
            while (same < t.length() && same < composing.length() && t.charAt(same) == composing.charAt(same)) same++;
            for (int i = same; i < composing.length(); i++) Keyboard.tap(K_BACK, '\b');
            typeChars(t.substring(same));
            composing = t;
            return true;
        }

        @Override
        public boolean finishComposingText() {
            composing = "";
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int before, int after) {
            for (int i = 0; i < before; i++) Keyboard.tap(K_BACK, '\b');
            return true;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent e) {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_DEL) Keyboard.tap(K_BACK, '\b');
            else if (code == KeyEvent.KEYCODE_ENTER) Keyboard.tap(K_RETURN, '\r');
            else {
                int ch = e.getUnicodeChar();
                int k = KeyMap.toLwjgl(code);
                if (ch != 0) Keyboard.tap(k, (char) ch);
                else if (k != 0) Keyboard.tap(k, (char) 0);
            }
            return true;
        }

        @Override
        public boolean performEditorAction(int action) {
            Keyboard.tap(K_RETURN, '\r');
            return true;
        }
    }
}
