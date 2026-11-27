package mcshim;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Indev 20100223's level slots and title screen, made to work offline.
 *
 * In this version the five slots on "Save level" / "Load level" only closed the
 * screen (the online level storage they were meant for came later and is gone).
 * Here slot N is the file .minecraft/saves/slotN.mclevel, with the name typed
 * when saving kept in slotN.txt. Indev's pause menu also had no way back to the
 * title screen; a "Quit to title" button is added.
 *
 * The game calls these through bytecode patches made by tools/Remap.java.
 * Everything runs on the game thread. Obfuscated names are Indev 20100223's.
 */
public final class IndevLevels {
    private IndevLevels() {}

    static final int SLOTS = 5;
    static final int QUIT_ID = 77;

    static File savesDir() {
        File d = new File(System.getProperty("user.home"), ".minecraft/saves");
        d.mkdirs();
        return d;
    }

    static File slotFile(int i) { return new File(savesDir(), "slot" + (i + 1) + ".mclevel"); }

    private static File nameFile(int i) { return new File(savesDir(), "slot" + (i + 1) + ".txt"); }

    /** "name;name;-;-;-" as the online slot list used to answer. */
    static String slotList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) {
            if (i > 0) sb.append(';');
            String n = "-";
            if (slotFile(i).isFile()) {
                n = "Level " + (i + 1);
                try {
                    FileInputStream in = new FileInputStream(nameFile(i));
                    byte[] b = new byte[256];
                    int len = in.read(b);
                    in.close();
                    if (len > 0) n = new String(b, 0, len, "UTF-8").trim();
                } catch (Exception ignored) {}
                n = n.replace(';', ',');
                if (n.length() == 0 || n.equals("-")) n = "Level " + (i + 1);
            }
            sb.append(n);
        }
        return sb.toString();
    }

    // ---- reflection helpers ----

    private static Field field(Class<?> c, String n) throws Exception {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(n);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException next) {}
        }
        throw new NoSuchFieldException(n);
    }

    /** The screen's own a(File): load on the Load screen, save on the Save screen. */
    private static void fileAction(Object screen, File f) throws Exception {
        for (Class<?> k = screen.getClass(); k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals("a") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == File.class) {
                    m.setAccessible(true);
                    m.invoke(screen, f);
                    return;
                }
            }
        }
        throw new NoSuchMethodException("a(File) on " + screen.getClass());
    }

    // ---- called by the game ----

    /** Load screen, slot clicked (the game then closes the screen). */
    public static void loadSlot(Object screen, int slot) {
        try {
            File f = slotFile(slot);
            if (f.isFile()) fileAction(screen, f);
        } catch (Throwable t) {
            Platform.get().log("load slot " + slot + ": " + t);
        }
    }

    /** Level name screen (after picking a save slot): "Save" saves into that slot. */
    public static void nameScreenButton(Object screen, Object button) {
        try {
            Class<?> c = screen.getClass();
            boolean enabled = field(button.getClass(), "c").getBoolean(button);
            int id = field(button.getClass(), "b").getInt(button);
            String name = (String) field(c, "k").get(screen);
            if (!enabled || id != 0 || name == null || name.trim().length() <= 1) return; // same test as the game
            int slot = field(c, "j").getInt(screen);
            Object saveScreen = field(c, "a").get(screen);          // the "Save level" screen it came from
            fileAction(saveScreen, slotFile(slot));
            FileOutputStream o = new FileOutputStream(nameFile(slot));
            o.write(name.trim().getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) {
            Platform.get().log("save slot: " + t);
        }
    }

    /** Pause menu built: add "Quit to title" in the gap above "Back to game". */
    @SuppressWarnings("unchecked")
    public static void pauseMenuButtons(Object menu) {
        try {
            Class<?> screen = menu.getClass().getSuperclass();
            int w = field(screen, "c").getInt(menu), h = field(screen, "d").getInt(menu);
            List<Object> buttons = (List<Object>) field(screen, "e").get(menu);
            Class<?> buttonC = buttons.get(0).getClass();
            Object quit = buttonC.getConstructor(int.class, int.class, int.class, String.class)
                    .newInstance(QUIT_ID, w / 2 - 100, h / 4 + 96, "Quit to title");
            buttons.add(quit);
        } catch (Throwable t) {
            Platform.get().log("quit button: " + t);
        }
    }

    /** Pause menu click: our button leaves the level for the title screen. */
    public static void pauseMenuAction(Object menu, Object button) {
        try {
            if (field(button.getClass(), "b").getInt(button) != QUIT_ID) return;
            Object mc = field(menu.getClass().getSuperclass(), "b").get(menu);
            Class<?> mcC = mc.getClass();
            Class<?> levelC = field(mcC, "d").getType();
            Class<?> screenC = menu.getClass().getSuperclass();
            Method setLevel = mcC.getMethod("a", levelC);
            Method setScreen = mcC.getMethod("a", screenC);
            setLevel.invoke(mc, (Object) null);
            setScreen.invoke(mc, (Object) null);    // no level: the game shows its title screen
        } catch (Throwable t) {
            Platform.get().log("quit to title: " + t);
        }
    }
}
