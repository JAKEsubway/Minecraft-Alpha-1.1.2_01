package mcport;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import mcshim.Hooks;
import mcshim.lwjgl.input.Keyboard;
import mcshim.ui.GuiTouch;
import mcshim.lwjgl.input.Mouse;

/**
 * Touch controls in the style of Pocket Edition 0.12:
 *  - D-pad bottom-left; centre button = sneak (tap to toggle);
 *    two diagonal buttons appear while walking forward; fingers can slide between buttons
 *  - jump button bottom-right (hold to keep jumping)
 *  - the rest of the screen looks around; tap = use/place, hold = mine/attack
 *  - tap a hotbar slot to select it, hold it to drop the item
 *  - "..." button next to the hotbar opens the inventory, pause button top-right
 * Menus: the finger is the mouse; a second finger is a right click.
 * All art is drawn as flat pixel sprites on the game's own GUI pixel grid.
 */
final class TouchOverlay extends View {
    // LWJGL key codes
    private static final int K_ESC = 1, K_W = 17, K_A = 30, K_S = 31, K_D = 32, K_Q = 16, K_I = 23,
            K_SPACE = 57, K_LSHIFT = 42;

    private static final long LONG_PRESS_MS = 350, DROP_HOLD_MS = 500, CONTAINER_HOLD_MS = 450;

    // ---- D-pad cells (3x3 grid, row-major) ----
    private static final int C_NONE = -1, C_UL = 0, C_UP = 1, C_UR = 2, C_LEFT = 3, C_SNEAK = 4, C_RIGHT = 5, C_DOWN = 7;

    // ---- sprites (16x16) ----
    private static final String[] ARROW_UP = {
        "................",
        "................",
        "................",
        ".......##.......",
        "......####......",
        ".....######.....",
        "....########....",
        "...##########...",
        "......####......",
        "......####......",
        "......####......",
        "......####......",
        "......####......",
        "................",
        "................",
        "................",
    };
    private static final String[] ARROW_UL = {
        "................",
        "................",
        "................",
        "...#######......",
        "...######.......",
        "...#####........",
        "...######.......",
        "...#######......",
        "...###.####.....",
        "...##...####....",
        "...#.....####...",
        "..........###...",
        "...........#....",
        "................",
        "................",
        "................",
    };
    private static final String[] JUMP = {
        "................",
        "................",
        "................",
        "................",
        "................",
        ".......##.......",
        "......####......",
        ".....######.....",
        "....###..###....",
        "...###....###...",
        "...##......##...",
        "................",
        "................",
        "................",
        "................",
        "................",
    };
    private static final String[] SNEAK = {
        "................",
        "................",
        "................",
        "................",
        "...##......##...",
        "...###....###...",
        "....###..###....",
        ".....######.....",
        "......####......",
        ".......##.......",
        "................",
        "...##########...",
        "...##########...",
        "................",
        "................",
        "................",
    };
    private static final String[] PAUSE = {
        "................",
        "................",
        "................",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "................",
        "................",
        "................",
    };
    private static final String[] DOTS = {
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..##...##...##..",
        "..##...##...##..",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    };

    private final Paint p = new Paint();
    /** Kind of game screen open (GuiTouch.NONE / OTHER / TEXT / CONTAINER). */
    private volatile int screenKind;

    private volatile boolean grabbed;
    private boolean sneakOn;

    // geometry (pixels)
    private float cell;                       // D-pad button size
    private float padX, padY;                 // top-left of the 3x3 D-pad grid
    private final RectF pauseR = new RectF(), invR = new RectF(), jumpR = new RectF();
    private boolean pausePressed, invPressed;

    // ---- pointers ----
    private static final int R_PAD = 1, R_LOOK = 2, R_PAUSE = 3, R_INV = 4, R_HOTBAR = 5, R_MENU = 6, R_JUMP = 7, R_CLOSE = 8;

    private static final class Ptr {
        int role, cell = C_NONE, slot;
        float x0, y0, lx, ly;
        long t0;
        boolean moved, mining, dropped;
    }

    private final SparseArray<Ptr> ptrs = new SparseArray<Ptr>();
    private int menuPrimary = -1;
    private final boolean[] held = new boolean[4]; // W A S D
    private boolean jumpHeld;

    TouchOverlay(Context c) {
        super(c);
        p.setAntiAlias(false);
    }

    void setScreenKind(int k) { screenKind = k; }

    // close (Esc) button for in-game screens
    private final RectF closeR = new RectF();
    private boolean closeShown, closePressed;

    void setCloseButton(int[] r) {
        closeShown = r != null;
        if (closeShown) closeR.set(r[0], r[1], r[2], r[3]);
        closePressed = false;
        invalidate();
    }

    private boolean inContainer() { return screenKind == GuiTouch.CONTAINER; }

    /**
     * Click in an inventory-type screen. The game thread decides the button:
     * holding an item over the crafting grid places one (right click), gaps
     * between slots snap to the nearest slot, near-misses never drop a stack.
     */
    private void containerClick(final int x, final int y, final boolean longPress) {
        final int w = getWidth(), h = getHeight();
        Hooks.runOnGameThread(new Runnable() {
            public void run() {
                int[] plan = GuiTouch.planTap(x, y, w, h, longPress);
                if (plan == null) return;
                Mouse.injectMove(plan[1], h - 1 - plan[2]);
                Mouse.injectButton(plan[0], true);
                Mouse.injectButton(plan[0], false);
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layout();
    }

    /** GUI scale exactly as the game computes it. */
    private int guiScale() {
        int w = getWidth(), h = getHeight(), s = 1;
        while (w / (s + 1) >= 320 && h / (s + 1) >= 240) s++;
        return s;
    }

    private void layout() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        int s = guiScale();
        // a D-pad button is a whole number of game pixels, about 1/7 of the screen height
        int units = Math.max(16, Math.round(h * 0.145f / s));
        cell = units * s;
        float margin = 6 * s;
        padX = margin;
        padY = h - margin - cell * 3;
        float pb = Math.round(cell * 0.75f / s) * s;
        pauseR.set(w - margin - pb, margin, w - margin, margin + pb);
        // jump: bottom-right, level with the middle row of the D-pad
        // a little in from the right edge so the thumb reaches it comfortably
        float jx = w - margin - cell - Math.round(cell * 0.6f / s) * s;
        jumpR.set(jx, padY + cell, jx + cell, padY + cell * 2);
        float hbW = 182 * s, hbH = 22 * s;
        float hx = (w - hbW) / 2 + hbW + 2 * s;
        invR.set(hx, h - hbH, hx + hbH, h);
    }

    // ------------------------------------------------------------------
    void setGrabbed(boolean g) {
        if (g == grabbed) return;
        releaseAll();
        grabbed = g;
        postInvalidate();
    }

    private void releaseAll() {
        // A menu tap presses the mouse button on touch-down; if that press closes the
        // menu (e.g. "Back to game"), the finger's release never reaches the game and
        // the button stays held -> the player keeps mining. Always let go of both.
        Mouse.injectButton(0, false);
        Mouse.injectButton(1, false);
        for (int i = 0; i < ptrs.size(); i++) {
            if (ptrs.valueAt(i).mining) Mouse.injectButton(0, false);
        }
        ptrs.clear();
        applyPad();
        if (sneakOn) { sneakOn = false; Keyboard.inject(K_LSHIFT, false, (char) 0); }
        pausePressed = invPressed = false;
        if (jumpHeld) { jumpHeld = false; Keyboard.inject(K_SPACE, false, ' '); }
        menuPrimary = -1;
    }

    // ------------------------------------------------------------------
    private boolean forwardActive() {
        for (int i = 0; i < ptrs.size(); i++) {
            Ptr q = ptrs.valueAt(i);
            if (q.role == R_PAD && (q.cell == C_UP || q.cell == C_UL || q.cell == C_UR)) return true;
        }
        return false;
    }

    private boolean inPad(float x, float y) {
        return x >= padX && x < padX + cell * 3 && y >= padY && y < padY + cell * 3;
    }

    /** Which D-pad button is under (x, y); diagonals only exist while walking forward. */
    private int cellAt(float x, float y, boolean allowDiag) {
        if (!inPad(x, y)) return C_NONE;
        int cx = (int) ((x - padX) / cell), cy = (int) ((y - padY) / cell);
        int c = cy * 3 + cx;
        if ((c == C_UL || c == C_UR) && !allowDiag) return C_NONE;
        if (c == 6 || c == 8) return C_NONE;
        return c;
    }

    /** Recompute W/A/S/D from every finger on the pad. */
    private void applyPad() {
        boolean w = false, a = false, s = false, d = false;
        for (int i = 0; i < ptrs.size(); i++) {
            Ptr q = ptrs.valueAt(i);
            if (q.role != R_PAD) continue;
            switch (q.cell) {
                case C_UP: w = true; break;
                case C_UL: w = true; a = true; break;
                case C_UR: w = true; d = true; break;
                case C_LEFT: a = true; break;
                case C_RIGHT: d = true; break;
                case C_DOWN: s = true; break;
            }
        }
        boolean[] want = {w, a, s, d};
        int[] keys = {K_W, K_A, K_S, K_D};
        for (int i = 0; i < 4; i++) {
            if (want[i] != held[i]) { held[i] = want[i]; Keyboard.inject(keys[i], want[i], (char) 0); }
        }
    }

    private void setJump(boolean on) {
        if (on != jumpHeld) { jumpHeld = on; Keyboard.inject(K_SPACE, on, ' '); }
    }

    private int hotbarSlot(float x, float y) {
        int s = guiScale();
        float w = 182 * s, h = 22 * s;
        float x0 = (getWidth() - w) / 2, y0 = getHeight() - h;
        if (x < x0 || x >= x0 + w || y < y0) return -1;
        return Math.max(0, Math.min(8, (int) ((x - x0 - s) / (20 * s))));
    }

    private static boolean hit(RectF r, float x, float y, float pad) {
        return x >= r.left - pad && x <= r.right + pad && y >= r.top - pad && y <= r.bottom + pad;
    }

    private float lookFactor() { return 1200f / Math.max(1, getWidth()); }

    private int my(float y) { return getHeight() - 1 - (int) y; }

    // ------------------------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked(), idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(e.getPointerId(idx), e.getX(idx), e.getY(idx), false);
                break;
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < e.getPointerCount(); i++) up(e.getPointerId(i), e.getX(i), e.getY(i), true);
                break;
        }
        tick();
        invalidate();
        return true;
    }

    private void down(int id, float x, float y) {
        Ptr q = new Ptr();
        q.x0 = q.lx = x; q.y0 = q.ly = y;
        q.t0 = SystemClock.uptimeMillis();
        ptrs.put(id, q);
        if (!grabbed && closeShown && hit(closeR, x, y, 3 * guiScale())) {
            q.role = R_CLOSE;
            closePressed = true;
            return;
        }
        if (!grabbed) {
            q.role = R_MENU;
            if (menuPrimary < 0) {
                menuPrimary = id;
                Mouse.injectMove((int) x, my(y));          // pointer follows the finger: hover highlight + held item
                if (!inContainer()) Mouse.injectButton(0, true); // buttons / sliders: press now, drag, release
            } else {
                Ptr first = ptrs.get(menuPrimary);
                if (inContainer() && first != null) {
                    first.dropped = true;                   // the first finger no longer clicks on release
                    containerClick((int) first.lx, (int) first.ly, true);
                } else {
                    Mouse.injectButton(1, true);
                    Mouse.injectButton(1, false);
                }
            }
            return;
        }
        float pad = 4 * guiScale();
        int slot;
        if (hit(jumpR, x, y, pad)) {
            q.role = R_JUMP;
            setJump(true);
        } else if (hit(pauseR, x, y, pad)) {
            q.role = R_PAUSE; pausePressed = true;
        } else if (hit(invR, x, y, pad)) {
            q.role = R_INV; invPressed = true;
        } else if (inPad(x, y)) {
            q.role = R_PAD;
            q.cell = cellAt(x, y, forwardActive());
            if (q.cell == C_SNEAK) {
                sneakOn = !sneakOn;
                Keyboard.inject(K_LSHIFT, sneakOn, (char) 0);
            }
            applyPad();
        } else if ((slot = hotbarSlot(x, y)) >= 0) {
            q.role = R_HOTBAR; q.slot = slot;
            Keyboard.tap(2 + slot, (char) ('1' + slot));
        } else {
            q.role = R_LOOK;
        }
    }

    private void move(int id, float x, float y) {
        Ptr q = ptrs.get(id);
        if (q == null) return;
        float slop = 3 * guiScale();
        if (Math.abs(x - q.x0) > slop || Math.abs(y - q.y0) > slop) q.moved = true;
        switch (q.role) {
            case R_MENU:
                if (id == menuPrimary) Mouse.injectMove((int) x, my(y));
                break;
            case R_PAD: {
                int c = cellAt(x, y, forwardActive());
                if (c != q.cell) { q.cell = c; applyPad(); }
                break;
            }
            case R_LOOK: {
                float f = lookFactor();
                Mouse.injectDelta((x - q.lx) * f, -(y - q.ly) * f);
                break;
            }
            case R_PAUSE: pausePressed = hit(pauseR, x, y, slop); break;
            case R_CLOSE: closePressed = hit(closeR, x, y, 3 * guiScale()); break;
            case R_INV: invPressed = hit(invR, x, y, slop); break;
        }
        q.lx = x; q.ly = y;
    }

    private void up(int id, float x, float y, boolean cancel) {
        Ptr q = ptrs.get(id);
        if (q == null) return;
        ptrs.remove(id);
        switch (q.role) {
            case R_MENU:
                if (id == menuPrimary) {
                    if (inContainer()) {
                        // tap = left click on release (slide first to aim); long-press already right-clicked
                        if (!cancel && !q.mining && !q.dropped) containerClick((int) x, (int) y, false);
                        else Mouse.injectMove((int) x, my(y));
                    } else {
                        Mouse.injectMove((int) x, my(y));
                        Mouse.injectButton(0, false);
                    }
                    menuPrimary = -1;
                }
                break;
            case R_PAD:
                applyPad();
                break;
            case R_CLOSE:
                if (!cancel && closePressed) Keyboard.tap(K_ESC, (char) 27);
                closePressed = false;
                break;
            case R_JUMP:
                setJump(false);
                break;
            case R_PAUSE:
                if (!cancel && pausePressed) Keyboard.tap(K_ESC, (char) 27);
                pausePressed = false;
                break;
            case R_INV:
                if (!cancel && invPressed) Keyboard.tap(K_I, 'i');
                invPressed = false;
                break;
            case R_LOOK:
                if (q.mining) {
                    Mouse.injectButton(0, false);
                } else if (!cancel && !q.moved && SystemClock.uptimeMillis() - q.t0 < LONG_PRESS_MS) {
                    // tap: like Pocket Edition, the creature under the finger gets hit;
                    // a tap anywhere else uses / places at the crosshair
                    final int tx = (int) q.x0, ty = (int) q.y0, vw = getWidth(), vh = getHeight();
                    Hooks.runOnGameThread(new Runnable() {
                        public void run() {
                            if (!GuiTouch.attackAtScreen(tx, ty, vw, vh)) {
                                Mouse.injectButton(1, true);
                                Mouse.injectButton(1, false);
                            }
                        }
                    });
                }
                break;
        }
    }

    private final Runnable tickRunnable = new Runnable() {
        public void run() { tick(); }
    };

    /** Timed gestures: hold still on the world = mine; hold a hotbar slot = drop. */
    private void tick() {
        boolean pending = false;
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < ptrs.size(); i++) {
            Ptr q = ptrs.valueAt(i);
            if (q.role == R_MENU && inContainer() && !q.mining && !q.moved && !q.dropped) {
                // hold still on a slot = right click (take half / place one)
                if (now - q.t0 >= CONTAINER_HOLD_MS) { q.mining = true; containerClick((int) q.lx, (int) q.ly, true); }
                else pending = true;
            } else if (q.role == R_LOOK && !q.mining && !q.moved) {
                if (now - q.t0 >= LONG_PRESS_MS) { q.mining = true; Mouse.injectButton(0, true); invalidate(); }
                else pending = true;
            } else if (q.role == R_HOTBAR && !q.dropped) {
                if (now - q.t0 >= DROP_HOLD_MS) { q.dropped = true; Keyboard.tap(K_Q, 'q'); }
                else pending = true;
            }
        }
        removeCallbacks(tickRunnable);
        if (pending) postDelayed(tickRunnable, 30);
    }

    // ------------------------------------------------------------------
    private boolean cellPressed(int c) {
        for (int i = 0; i < ptrs.size(); i++) {
            Ptr q = ptrs.valueAt(i);
            if (q.role == R_PAD && q.cell == c) return true;
        }
        return false;
    }

    /** Draw a 16x16 sprite into rect, rotated by quarter turns, with a 1-pixel drop shadow. */
    private void sprite(Canvas c, String[] art, RectF r, int quarter, boolean flipX) {
        float u = r.width() / 16f;
        for (int pass = 0; pass < 2; pass++) {
            p.setColor(pass == 0 ? 0x66000000 : 0xE6FFFFFF);
            float off = pass == 0 ? u : 0;
            for (int y = 0; y < 16; y++) {
                String row = art[y];
                for (int x = 0; x < 16; x++) {
                    if (row.charAt(x) != '#') continue;
                    int sx = flipX ? 15 - x : x, sy = y, tx = sx, ty = sy;
                    for (int k = 0; k < quarter; k++) { int t = tx; tx = 15 - ty; ty = t; }
                    float px = r.left + tx * u + off, py = r.top + ty * u + off;
                    c.drawRect(px, py, px + u, py + u, p);
                }
            }
        }
    }

    /** Flat translucent square button with a darker 1-pixel frame, like the old PE controls. */
    private void button(Canvas c, RectF r, boolean pressed) {
        float u = r.width() / 16f;
        p.setColor(pressed ? 0x99D8D8D8 : 0x55303030);
        c.drawRect(r.left + u, r.top + u, r.right - u, r.bottom - u, p);
        p.setColor(pressed ? 0xCCFFFFFF : 0x88000000);
        c.drawRect(r.left, r.top, r.right, r.top + u, p);
        c.drawRect(r.left, r.bottom - u, r.right, r.bottom, p);
        c.drawRect(r.left, r.top + u, r.left + u, r.bottom - u, p);
        c.drawRect(r.right - u, r.top + u, r.right, r.bottom - u, p);
    }

    /** Game-style button (black frame, grey face, light top, dark bottom) with a pixel X. */
    private void drawClose(Canvas c) {
        RectF r = closeR;
        float u = r.width() / 20f;
        p.setColor(0xFF000000);
        c.drawRect(r, p);
        p.setColor(closePressed ? 0xFF7E86B8 : 0xFF6F6F6F);
        c.drawRect(r.left + u, r.top + u, r.right - u, r.bottom - u, p);
        p.setColor(closePressed ? 0xFFBCC3EE : 0xFFA9A9A9);
        c.drawRect(r.left + u, r.top + u, r.right - u, r.top + 2 * u, p);
        c.drawRect(r.left + u, r.top + u, r.left + 2 * u, r.bottom - u, p);
        p.setColor(0xFF4A4A4A);
        c.drawRect(r.left + u, r.bottom - 3 * u, r.right - u, r.bottom - u, p);
        // 8x8 pixel X with the game's text shadow
        float ox = r.left + 6 * u, oy = r.top + 5.5f * u;
        for (int pass = 0; pass < 2; pass++) {
            p.setColor(pass == 0 ? 0xFF3F3F3F : (closePressed ? 0xFFFFFFA0 : 0xFFE0E0E0));
            float o = pass == 0 ? u : 0;
            for (int i = 0; i < 8; i++) {
                c.drawRect(ox + o + i * u, oy + o + i * u, ox + o + (i + 1) * u, oy + o + (i + 1) * u, p);
                c.drawRect(ox + o + (7 - i) * u, oy + o + i * u, ox + o + (8 - i) * u, oy + o + (i + 1) * u, p);
            }
        }
    }

    private final RectF tmp = new RectF();

    private RectF cellRect(int c) {
        float gap = Math.round(cell / 16f);
        int cx = c % 3, cy = c / 3;
        float x = padX + cx * cell, y = padY + cy * cell;
        tmp.set(x + gap, y + gap, x + cell - gap, y + cell - gap);
        return tmp;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!grabbed) {
            if (closeShown) drawClose(c);
            return;
        }
        boolean fwd = forwardActive();
        // D-pad
        int[] cells = {C_UP, C_LEFT, C_RIGHT, C_DOWN, C_SNEAK};
        int[] quarters = {0, 3, 1, 2, 0};
        for (int i = 0; i < cells.length; i++) {
            RectF r = cellRect(cells[i]);
            boolean on = cells[i] == C_SNEAK ? sneakOn : cellPressed(cells[i]);
            button(c, r, on);
            if (cells[i] == C_SNEAK) sprite(c, SNEAK, r, 0, false);
            else sprite(c, ARROW_UP, r, quarters[i], false);
        }
        if (fwd) {
            RectF r = cellRect(C_UL);
            button(c, r, cellPressed(C_UL));
            sprite(c, ARROW_UL, r, 0, false);
            r = cellRect(C_UR);
            button(c, r, cellPressed(C_UR));
            sprite(c, ARROW_UL, r, 0, true);
        }
        // jump
        button(c, jumpR, jumpHeld);
        sprite(c, JUMP, jumpR, 0, false);
        // pause + inventory
        button(c, pauseR, pausePressed);
        sprite(c, PAUSE, pauseR, 0, false);
        button(c, invR, invPressed);
        sprite(c, DOTS, invR, 0, false);
    }
}
