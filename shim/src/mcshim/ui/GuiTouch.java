package mcshim.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;

import mcshim.Hooks;

/**
 * Touch helpers for the game's own (original) screens. Game thread only.
 * Names are for Minecraft Alpha 1.1.2_01; if any is missing every helper
 * falls back to plain left clicks.
 */
public final class GuiTouch {
    private GuiTouch() {}

    public static final int NONE = 0, OTHER = 1, TEXT = 2, CONTAINER = 3;

    private static boolean tried, ok;
    private static Field fWorld, fAim, fAimEntity;
    private static Class<?> cDeath, cControls;
    private static Field fScreen, fPlayer, fInventory, fCursor, fList, fW, fH, fXSize, fYSize, fSlots;
    private static Field sInv, sX, sY, bId;
    private static Class<?> cContainer, cCraftGrid, cChat, cSign, cConnect, cOptions;
    private static Method mSlotAt;

    private static Field f(Class<?> c, String n) throws Exception {
        Field x = c.getDeclaredField(n);
        x.setAccessible(true);
        return x;
    }

    private static synchronized boolean init() {
        if (tried) return ok;
        tried = true;
        try {
            ClassLoader cl = GuiTouch.class.getClassLoader();
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, cl);
            Class<?> screen = Class.forName("bh", false, cl);
            Class<?> button = Class.forName("fk", false, cl);
            Class<?> player = Class.forName("dm", false, cl);
            Class<?> inv = Class.forName("eu", false, cl);
            Class<?> slot = Class.forName("dk", false, cl);
            Class<?> guiSlot = Class.forName("mm", false, cl);
            cContainer = Class.forName("ee", false, cl);
            cCraftGrid = Class.forName("gu", false, cl);
            cChat = Class.forName("de", false, cl);
            cSign = Class.forName("nv", false, cl);
            cConnect = Class.forName("gc", false, cl);
            cOptions = Class.forName("ay", false, cl);
            fScreen = f(mc, "p");
            fWorld = f(mc, "e");
            fAim = f(mc, "x");                                   // objectMouseOver
            fAimEntity = f(Class.forName("mf", false, cl), "g");  // ...its entityHit
            cDeath = Class.forName("au", false, cl);
            cControls = Class.forName("lw", false, cl);
            fPlayer = f(mc, "g");
            fInventory = f(player, "b");
            fCursor = f(inv, "e");
            fList = f(screen, "e");
            fW = f(screen, "c");
            fH = f(screen, "d");
            fXSize = f(cContainer, "a");
            fYSize = f(cContainer, "h");
            fSlots = f(cContainer, "i");
            sInv = f(slot, "b");
            sX = f(guiSlot, "e");
            sY = f(guiSlot, "f");
            bId = f(button, "f");
            mSlotAt = cContainer.getDeclaredMethod("a", int.class, int.class);
            mSlotAt.setAccessible(true);
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        return ok;
    }

    private static Object screen() throws Exception {
        Object mc = Hooks.minecraft;
        return mc == null ? null : fScreen.get(mc);
    }

    /** What kind of screen is open. */
    public static int kind() {
        if (!init()) return NONE;
        try {
            Object s = screen();
            if (s == null) return NONE;
            if (cContainer.isInstance(s)) return CONTAINER;
            if (cChat.isInstance(s) || cSign.isInstance(s) || cConnect.isInstance(s)) return TEXT;
            return OTHER;
        } catch (Exception e) {
            return NONE;
        }
    }

    /**
     * Where to put a close (Esc) button for the open in-game screen, in screen
     * pixels {left, top, right, bottom}, or null for none. Only screens with no
     * button of their own to leave get one: inventory-type screens and chat.
     * Inventory-type screens get it just outside the window's top-right corner.
     */
    public static int[] closeButton(int dispW, int dispH) {
        if (!init()) return null;
        try {
            Object mc = Hooks.minecraft;
            Object s = screen();
            if (s == null || fWorld.get(mc) == null) return null;
            // only screens with no way back of their own: inventory-type screens and chat
            // (options, controls and the sign editor already have a "Done" button)
            boolean wanted = cContainer.isInstance(s) || cChat.isInstance(s);
            if (!wanted) return null;
            int gw = fW.getInt(s), gh = fH.getInt(s);
            if (gw <= 0 || gh <= 0) return null;
            int size = 20, l, t;
            if (cContainer.isInstance(s)) {
                int right = (gw + fXSize.getInt(s)) / 2, top = (gh - fYSize.getInt(s)) / 2;
                l = right + 2;
                t = top;
                if (l + size > gw) l = right - size - 2; // no room outside: tuck it inside the corner
                if (t < 0) t = 0;
            } else {
                l = gw - size - 4;
                t = 4;
            }
            float kx = (float) dispW / gw, ky = (float) dispH / gh;
            return new int[] {(int) (l * kx), (int) (t * ky), (int) ((l + size) * kx), (int) ((t + size) * ky)};
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean musicNudged;

    /**
     * Alpha waits a random 0-10 minutes before the first music track. Once per
     * run, as soon as a world is loaded, bring that down to 30-90 seconds;
     * later tracks keep the game's own timing. Game thread, call every frame.
     */
    public static void nudgeFirstMusic() {
        if (musicNudged || !init()) return;
        try {
            Object mc = Hooks.minecraft;
            if (mc == null || fWorld.get(mc) == null) return;
            Class<?> mcC = Class.forName("net.minecraft.client.Minecraft", false, GuiTouch.class.getClassLoader());
            Object snd = f(mcC, "A").get(mc);
            if (snd == null) return;
            Field ticks = f(snd.getClass(), "i");
            int t = ticks.getInt(snd);
            int want = 600 + (int) (Math.random() * 1200); // 20 ticks per second
            if (t > want) ticks.setInt(snd, want);
            musicNudged = true;
        } catch (Throwable e) {
            musicNudged = true;
        }
    }

    // ---- attack assist ----
    private static boolean aTried, aOk;
    private static Field fEntities, eX, eY, eZ, eYaw, ePitch, eW, eH, eYOff, eHealth;
    private static Class<?> cLiving;
    private static Method mVec, mRay, mSwing, mAttack;

    private static synchronized boolean initAssist() {
        if (aTried) return aOk;
        aTried = true;
        try {
            ClassLoader cl = GuiTouch.class.getClassLoader();
            Class<?> world = Class.forName("cn", false, cl), ent = Class.forName("kh", false, cl);
            Class<?> vec = Class.forName("aj", false, cl), aim = Class.forName("mf", false, cl);
            cLiving = Class.forName("ge", false, cl);
            fEntities = f(world, "a");          // loaded entities
            eX = f(ent, "ak"); eY = f(ent, "al"); eZ = f(ent, "am");
            eYaw = f(ent, "aq"); ePitch = f(ent, "ar");
            eW = f(ent, "aC"); eH = f(ent, "aD"); eYOff = f(ent, "aB");
            eHealth = f(cLiving, "E");
            Class<?> player = Class.forName("bi", false, cl); // the local player
            mSwing = player.getMethod("w");          // swing the arm
            mAttack = player.getMethod("a", ent);    // attack with the held item, as a click on the creature does
            mVec = vec.getDeclaredMethod("b", double.class, double.class, double.class);
            mRay = world.getDeclaredMethod("a", vec, vec);
            aOk = init();
        } catch (Throwable t) {
            aOk = false;
        }
        return aOk;
    }

    /** How far a creature can be hit from (the game's own reach for mobs). */
    private static final double REACH = 4.0;
    /** Extra margin around a creature's body that still counts as "on it", in blocks. */
    private static final double MARGIN = 0.75;

    /**
     * Phones can't aim as precisely as a mouse. Find the living creature closest
     * to the crosshair within reach, treating each body as MARGIN blocks wider,
     * with no block in between, and attack it exactly the way the game's own
     * click does (swing + attack with the held item). Returns false if there
     * is none. Game thread only.
     */
    public static boolean attackNearCrosshair() {
        if (!initAssist()) return false;
        try {
            Object mc = Hooks.minecraft;
            Object player = fPlayer.get(mc), world = fWorld.get(mc);
            if (player == null || world == null) return false;
            double px = eX.getDouble(player), py = eY.getDouble(player) + 0.12, pz = eZ.getDouble(player);
            double yaw = Math.toRadians(eYaw.getFloat(player)), pitch = Math.toRadians(ePitch.getFloat(player));
            double lx = -Math.sin(yaw) * Math.cos(pitch), ly = -Math.sin(pitch), lz = Math.cos(yaw) * Math.cos(pitch);
            Object best = null;
            double bestScore = Double.MAX_VALUE;
            Object[] list = ((List<?>) fEntities.get(world)).toArray();
            for (Object e : list) {
                if (e == player || !cLiving.isInstance(e) || eHealth.getInt(e) <= 0) continue;
                float w = eW.getFloat(e), h = eH.getFloat(e);
                double cx = eX.getDouble(e), cz = eZ.getDouble(e);
                double cy = eY.getDouble(e) - eYOff.getFloat(e) + h / 2.0;
                double dx = cx - px, dy = cy - py, dz = cz - pz;
                double along = dx * lx + dy * ly + dz * lz;            // distance along the view
                if (along <= 0 || along > REACH + w / 2.0) continue;
                // perpendicular distance from the view ray to the body's centre
                double ox = dx - along * lx, oy = dy - along * ly, oz = dz - along * lz;
                double off = Math.sqrt(ox * ox + oy * oy + oz * oz);
                double radius = Math.max(w, h) / 2.0 + MARGIN;
                if (off > radius) continue;
                // nothing solid between the eyes and the creature
                Object from = mVec.invoke(null, px, py, pz), to = mVec.invoke(null, cx, cy, cz);
                if (mRay.invoke(world, from, to) != null) continue;
                double score = off / radius + along * 0.05; // prefer centred, then nearer
                if (score < bestScore) { bestScore = score; best = e; }
            }
            if (best == null) return false;
            mSwing.invoke(player);
            mAttack.invoke(player, best);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when the crosshair is on a mob/animal/player (game thread). */
    public static boolean aimingAtEntity() {
        if (!init()) return false;
        try {
            Object aim = fAim.get(Hooks.minecraft);
            return aim != null && fAimEntity.get(aim) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Alpha bug: coming back to Options from Controls adds every button again
     * without removing the old ones, so one tap toggles a setting twice.
     * Drop the older copies (game thread, call once per frame).
     */
    public static void dedupeButtons() {
        if (!init()) return;
        try {
            Object s = screen();
            if (s == null || !cOptions.isInstance(s)) return;
            List<?> list = (List<?>) fList.get(s);
            HashSet<Integer> seen = new HashSet<Integer>();
            for (int i = list.size() - 1; i >= 0; i--) {
                if (!seen.add(bId.getInt(list.get(i)))) list.remove(i);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Decide what a tap at screen pixel (px, py from the top) should do in a
     * container screen. Returns {button, px, py} (the point may be snapped to a
     * slot centre) or null to ignore the tap.
     *  - holding something + crafting-grid slot -> right click (place one)
     *  - tap in a gap between slots -> snapped to the nearest slot
     *  - holding something just outside the window -> ignored (no accidental drop)
     */
    public static int[] planTap(int px, int py, int dispW, int dispH, boolean longPress) {
        int button = longPress ? 1 : 0;
        if (!init()) return new int[] {button, px, py};
        try {
            Object s = screen();
            if (s == null || !cContainer.isInstance(s)) return new int[] {button, px, py};
            int gw = fW.getInt(s), gh = fH.getInt(s);
            float kx = (float) gw / dispW, ky = (float) gh / dispH;
            // exactly how the game maps the mouse: y comes from the bottom, minus one
            int gx = px * gw / dispW;
            int gy = gh - (dispH - 1 - py) * gh / dispH - 1;
            Object slot = mSlotAt.invoke(s, gx, gy);
            if (slot == null) {
                // snap to a slot within 4 GUI pixels of its 16x16 box
                int left = (gw - fXSize.getInt(s)) / 2, top = (gh - fYSize.getInt(s)) / 2;
                Object best = null;
                int bestD = Integer.MAX_VALUE;
                for (Object o : (List<?>) fSlots.get(s)) {
                    int cx = left + sX.getInt(o) + 8, cy = top + sY.getInt(o) + 8;
                    int dx = Math.max(0, Math.abs(gx - cx) - 8), dy = Math.max(0, Math.abs(gy - cy) - 8);
                    int d = dx * dx + dy * dy;
                    if (d < bestD) { bestD = d; best = o; }
                }
                if (best != null && bestD <= 16) {
                    slot = best;
                    gx = left + sX.getInt(best) + 8;
                    gy = top + sY.getInt(best) + 8;
                    px = (int) ((gx + 0.5f) / kx);
                    py = dispH - 1 - (int) ((gh - 1 - gy + 0.5f) / ky);
                }
            }
            Object cursor = fCursor.get(fInventory.get(fPlayer.get(Hooks.minecraft)));
            if (slot == null) {
                if (cursor != null) {
                    int left = (gw - fXSize.getInt(s)) / 2, top = (gh - fYSize.getInt(s)) / 2;
                    int right = left + fXSize.getInt(s), bottom = top + fYSize.getInt(s);
                    int margin = 12;
                    boolean nearWindow = gx > left - margin && gx < right + margin && gy > top - margin && gy < bottom + margin;
                    if (nearWindow) return null; // would drop the whole stack on the ground
                }
                return new int[] {button, px, py};
            }
            if (cursor != null && cCraftGrid.isInstance(sInv.get(slot))) button = 1;
            return new int[] {button, px, py};
        } catch (Throwable t) {
            return new int[] {button, px, py};
        }
    }
}
