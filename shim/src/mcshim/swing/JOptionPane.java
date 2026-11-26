package mcshim.swing;

import mcshim.Platform;

/** Indev shows its start-up error in a message box: show it as the crash screen. */
public class JOptionPane {
    public static void showMessageDialog(mcshim.awt.Component parent, Object message, String title, int type) {
        Platform.get().crash(title + "\n\n" + message);
    }
}
