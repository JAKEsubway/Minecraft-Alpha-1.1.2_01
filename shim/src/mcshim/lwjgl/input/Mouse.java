package mcshim.lwjgl.input;

import java.util.ArrayDeque;

import mcshim.Platform;
import mcshim.lwjgl.LWJGLException;

/** LWJGL 2 mouse, fed by the platform's touch layer. Coordinates: origin bottom-left. */
public final class Mouse {
    private Mouse() {}

    private static final class Ev { int button = -1; boolean down; int x, y, wheel; }

    private static final ArrayDeque<Ev> pending = new ArrayDeque<Ev>();
    private static final ArrayDeque<Ev> ready = new ArrayDeque<Ev>();
    private static final boolean[] buttons = new boolean[8];
    private static int x, y, px, py;
    private static float pdx, pdy;
    private static int dx, dy;
    private static volatile boolean grabbed;
    private static Ev cur;

    // ---- producer side (any thread) ----
    public static void injectMove(int nx, int ny) {
        Ev e = new Ev();
        e.x = nx; e.y = ny;
        synchronized (pending) { pending.add(e); px = nx; py = ny; }
    }

    public static void injectButton(int button, boolean down) {
        Ev e = new Ev();
        e.button = button; e.down = down;
        synchronized (pending) { e.x = px; e.y = py; pending.add(e); }
    }

    public static void injectWheel(int amount) {
        Ev e = new Ev();
        e.wheel = amount;
        synchronized (pending) { e.x = px; e.y = py; pending.add(e); }
    }

    /** Relative motion while grabbed (look around). */
    public static void injectDelta(float ddx, float ddy) {
        synchronized (pending) { pdx += ddx; pdy += ddy; }
    }

    public static boolean isGrabbed() { return grabbed; }

    // ---- AWT pointer for Indev, which reads mouse motion as "pointer position
    // minus window centre" and warps the pointer back with java.awt.Robot ----
    private static int baseX, baseY, ptrX, ptrY;

    /** Where java.awt.MouseInfo says the pointer is: last warp target plus motion since. */
    public static int awtPointerX() { return baseX + ptrX; }
    public static int awtPointerY() { return baseY + ptrY; }

    /** java.awt.Robot.mouseMove: put the pointer there. */
    public static void awtWarp(int x, int y) { baseX = x; baseY = y; ptrX = 0; ptrY = 0; }

    // ---- LWJGL API ----
    public static void poll() {
        synchronized (pending) {
            int ix = (int) pdx, iy = (int) pdy;
            pdx -= ix; pdy -= iy;
            dx += ix; dy += iy;
            ptrX += ix; ptrY -= iy;   // AWT pointer: y grows downwards
            Ev e;
            while ((e = pending.poll()) != null) {
                x = e.x; y = e.y;
                if (e.button >= 0 && e.button < buttons.length) buttons[e.button] = e.down;
                ready.add(e);
            }
        }
    }

    public static void create() throws LWJGLException {}
    public static void destroy() {}
    public static boolean isCreated() { return true; }

    public static boolean next() {
        cur = ready.poll();
        return cur != null;
    }

    public static int getEventButton() { return cur == null ? -1 : cur.button; }
    public static boolean getEventButtonState() { return cur != null && cur.down; }
    public static int getEventDWheel() { return cur == null ? 0 : cur.wheel; }
    public static int getEventX() { return cur == null ? x : cur.x; }
    public static int getEventY() { return cur == null ? y : cur.y; }
    public static int getX() { return x; }
    public static int getY() { return y; }

    public static int getDX() { int r = dx; dx = 0; return r; }
    public static int getDY() { int r = dy; dy = 0; return r; }
    public static int getDWheel() { return 0; }

    public static boolean isButtonDown(int b) { return b >= 0 && b < buttons.length && buttons[b]; }
    public static int getButtonCount() { return 3; }

    public static void setCursorPosition(int nx, int ny) {
        x = nx; y = ny;
        synchronized (pending) { px = nx; py = ny; }
    }

    public static void setGrabbed(boolean g) {
        if (g != grabbed) {
            grabbed = g;
            if (g) { synchronized (pending) { pdx = pdy = 0; } dx = dy = 0; }
            Platform.get().mouseGrabbed(g);
        }
    }

    /** Indev hides the pointer with a blank cursor instead of grabbing: same thing here. */
    public static Cursor setNativeCursor(Cursor c) { setGrabbed(c != null); return null; }
}
