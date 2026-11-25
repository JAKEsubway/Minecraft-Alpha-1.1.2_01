package mcshim.lwjgl.opengl;

import mcshim.Platform;
import mcshim.awt.Canvas;
import mcshim.lwjgl.LWJGLException;
import mcshim.lwjgl.input.Keyboard;
import mcshim.lwjgl.input.Mouse;

public final class Display {
    private Display() {}

    private static boolean created;
    private static String title = "";

    public static void create() throws LWJGLException {
        if (created) return;
        try {
            Platform.get().createDisplay();
        } catch (Exception e) {
            throw new LWJGLException("Could not create GL context: " + e);
        }
        GL11.init();
        created = true;
    }

    public static boolean isCreated() { return created; }

    public static void destroy() {
        if (created) Platform.get().destroyDisplay();
        created = false;
    }

    public static void update() {
        Platform.get().swapBuffers();
        Keyboard.poll();
        Mouse.poll();
        mcshim.Hooks.frame();
    }

    public static void swapBuffers() { Platform.get().swapBuffers(); }

    public static boolean isCloseRequested() { return Platform.get().closeRequested(); }

    public static boolean isActive() { return Platform.get().isActive(); }

    public static boolean isVisible() { return true; }

    public static DisplayMode getDisplayMode() { return new DisplayMode(Platform.get().width(), Platform.get().height()); }

    public static DisplayMode getDesktopDisplayMode() { return getDisplayMode(); }

    public static void setDisplayMode(DisplayMode m) {}

    public static void setFullscreen(boolean f) {}

    public static void setParent(Canvas c) {}

    public static void setTitle(String t) { title = t; }

    public static String getTitle() { return title; }

    public static void setVSyncEnabled(boolean b) {}
}
