package mcshim.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;

import mcshim.Hooks;

/**
 * Touch helpers for the game's own (original) screens. Game thread only.
 * Knows the obfuscated names of Minecraft Alpha 1.1.2_01 and Indev 20100223;
 * for any other version every helper falls back to plain clicks.
 */
public final class GuiTouch {
    private GuiTouch() {}

    public static final int NONE = 0, OTHER = 1, TEXT = 2, CONTAINER = 3;

    /** Obfuscated names for one game version. */
    private static final class Names {
        String mc, screen, world, aim, aimClass, aimEntity, player;
        String guiScreen, guiW, guiH, guiList, button, buttonId;
        String container, xSize, ySize, slots, slotAt; // slotAt null: find the slot by its box
        String slot, slotInv, slotX, slotY, slotXYClass; // where slotX/slotY live
        String cursorOwner, cursorField;                 // "inventory": player.inventory.cursor, "screen": container field
        String playerInv;
        String craftGrid, options;
        String multiplayer, multiplayerParent, setScreen; // the "connect to server" screen (or null), its parent field, Minecraft.displayGuiScreen
        String session, sessionName;                     // Minecraft.session and its user name field
        String[] textScreens;
    }

    private static final Names ALPHA = new Names(), INDEV = new Names();
    static {
        Names n = ALPHA;
        n.mc = "net.minecraft.client.Minecraft"; n.screen = "p"; n.world = "e"; n.aim = "x"; n.aimClass = "mf"; n.aimEntity = "g"; n.player = "g";
        n.guiScreen = "bh"; n.guiW = "c"; n.guiH = "d"; n.guiList = "e"; n.button = "fk"; n.buttonId = "f";
        n.container = "ee"; n.xSize = "a"; n.ySize = "h"; n.slots = "i"; n.slotAt = "a";
        n.slot = "dk"; n.slotInv = "b"; n.slotXYClass = "mm"; n.slotX = "e"; n.slotY = "f";
        n.cursorOwner = "inventory"; n.playerInv = "b"; n.cursorField = "e";
        n.craftGrid = "gu"; n.options = "ay";
        n.textScreens = new String[] {"de", "nv", "gc"}; // chat, sign, server address
        n.multiplayer = "gc"; n.multiplayerParent = "a"; n.setScreen = "a"; n.session = "i"; n.sessionName = "b";
        n = INDEV;
        n.mc = "net.minecraft.client.d"; n.screen = "o"; n.world = "d"; n.aim = "v"; n.aimClass = "net.minecraft.a.d.c"; n.aimEntity = "g"; n.player = "f";
        n.guiScreen = "net.minecraft.client.c.i"; n.guiW = "c"; n.guiH = "d"; n.guiList = "e"; n.button = "net.minecraft.client.c.r"; n.buttonId = "b";
        n.container = "net.minecraft.client.c.a.j"; n.xSize = "a"; n.ySize = "i"; n.slots = "j"; n.slotAt = null;
        n.slot = "net.minecraft.client.c.a.g"; n.slotInv = "d"; n.slotXYClass = "net.minecraft.client.c.a.g"; n.slotX = "b"; n.slotY = "c";
        n.cursorOwner = "screen"; n.cursorField = "l";
        n.craftGrid = "net.minecraft.client.c.a.a"; n.options = null;
        n.textScreens = new String[] {"net.minecraft.client.c.p"}; // level name
    }

    private static boolean tried, ok;
    private static Names v;
    private static Field fScreen, fWorld, fAim, fAimEntity, fPlayer, fInventory, fCursor;
    private static Field fList, fW, fH, fXSize, fYSize, fSlots, sInv, sX, sY, bId;
    private static Class<?> cContainer, cCraftGrid, cOptions, cMultiplayer;
    private static Class<?>[] cText;
    private static Method mSlotAt;

    /** Field n of c or of the nearest super class that declares it. */
    private static Field f(Class<?> c, String n) throws Exception {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field x = k.getDeclaredField(n);
                x.setAccessible(true);
                return x;
            } catch (NoSuchFieldException next) {}
        }
        throw new NoSuchFieldException(c.getName() + "." + n);
    }

    private static synchronized boolean init() {
        if (tried) return ok;
        Object game = Hooks.minecraft;
        if (game == null) return false;      // try again once the game object exists
        tried = true;
        try {
            ClassLoader cl = game.getClass().getClassLoader();
            Class<?> mc = game.getClass();   // Alpha's is an anonymous subclass of Minecraft
            while (mc != null && !mc.getName().equals(ALPHA.mc) && !mc.getName().equals(INDEV.mc)) mc = mc.getSuperclass();
            if (mc == null) return ok = false;
            v = mc.getName().equals(ALPHA.mc) ? ALPHA : INDEV;
            Class<?> screen = Class.forName(v.guiScreen, false, cl);
            cContainer = Class.forName(v.container, false, cl);
            cCraftGrid = Class.forName(v.craftGrid, false, cl);
            cOptions = v.options == null ? null : Class.forName(v.options, false, cl);
            cMultiplayer = v.multiplayer == null ? null : Class.forName(v.multiplayer, false, cl);
            cText = new Class<?>[v.textScreens.length];
            for (int i = 0; i < cText.length; i++) cText[i] = Class.forName(v.textScreens[i], false, cl);
            fScreen = f(mc, v.screen);
            fWorld = f(mc, v.world);
            fAim = f(mc, v.aim);                                            // what the crosshair is on
            fAimEntity = f(Class.forName(v.aimClass, false, cl), v.aimEntity); // ...the creature, if any
            fPlayer = f(mc, v.player);
            if (v.cursorOwner.equals("inventory")) {
                fInventory = f(fPlayer.getType(), v.playerInv);
                fCursor = f(fInventory.getType(), v.cursorField);
            } else {
                fCursor = f(cContainer, v.cursorField);
            }
            fList = f(screen, v.guiList);
            fW = f(screen, v.guiW);
            fH = f(screen, v.guiH);
            fXSize = f(cContainer, v.xSize);
            fYSize = f(cContainer, v.ySize);
            fSlots = f(cContainer, v.slots);
            Class<?> slot = Class.forName(v.slot, false, cl), slotXY = Class.forName(v.slotXYClass, false, cl);
            sInv = f(slot, v.slotInv);
            sX = f(slotXY, v.slotX);
            sY = f(slotXY, v.slotY);
            bId = f(Class.forName(v.button, false, cl), v.buttonId);
            if (v.slotAt != null) {
                mSlotAt = cContainer.getDeclaredMethod(v.slotAt, int.class, int.class);
                mSlotAt.setAccessible(true);
            }
            ok = true;
        } catch (Throwable t) {
            mcshim.Platform.get().log("touch helpers unavailable: " + t);
            ok = false;
        }
        return ok;
    }

    /** True while the "connect to server" screen is open (game thread). */
    public static boolean multiplayerScreen() {
        if (!init() || cMultiplayer == null) return false;
        try {
            return cMultiplayer.isInstance(screen());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Close the "connect to server" screen, back to the screen it came from. Game thread. */
    public static void leaveMultiplayer() {
        if (!multiplayerScreen()) return;
        try {
            Object mc = Hooks.minecraft, s = screen();
            Object parent = f(cMultiplayer, v.multiplayerParent).get(s);
            Class<?> k = mc.getClass();
            while (!k.getName().equals(v.mc)) k = k.getSuperclass();
            Method set = k.getDeclaredMethod(v.setScreen, Class.forName(v.guiScreen, false, k.getClassLoader()));
            set.setAccessible(true);
            set.invoke(mc, parent);
        } catch (Throwable ignored) {}
    }

    /** Change the player's name in the running game (used for multiplayer). Game thread. */
    public static void setPlayerName(String player) {
        if (!init() || v.session == null || player == null) return;
        try {
            Object mc = Hooks.minecraft;
            Object session = f(mc.getClass(), v.session).get(mc);
            if (session != null) f(session.getClass(), v.sessionName).set(session, player);
        } catch (Throwable ignored) {}
    }

    /** The item held on the pointer in a container screen, or null. */
    private static Object cursorItem(Object screen) throws Exception {
        if (fInventory != null) return fCursor.get(fInventory.get(fPlayer.get(Hooks.minecraft)));
        return fCursor.get(screen);
    }

    /** The slot under GUI point (gx, gy): the game's own method, or the same box test. */
    private static Object slotAt(Object s, int gx, int gy) throws Exception {
        if (mSlotAt != null) return mSlotAt.invoke(s, gx, gy);
        int left = (fW.getInt(s) - fXSize.getInt(s)) / 2, top = (fH.getInt(s) - fYSize.getInt(s)) / 2;
        for (Object o : (List<?>) fSlots.get(s)) {
            int x = gx - left, y = gy - top, sx = sX.getInt(o), sy = sY.getInt(o);
            if (x >= sx - 1 && x < sx + 16 + 1 && y >= sy - 1 && y < sy + 16 + 1) return o;
        }
        return null;
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
            for (Class<?> c : cText) if (c.isInstance(s)) return TEXT;
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
            boolean wanted = cContainer.isInstance(s) || (v == ALPHA && cText[0].isInstance(s));
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
        if (musicNudged || !init() || v != ALPHA) return;
        try {
            Object mc = Hooks.minecraft;
            if (mc == null || fWorld.get(mc) == null) return;
            Class<?> mcC = mc.getClass();
            while (!mcC.getName().equals(ALPHA.mc)) mcC = mcC.getSuperclass();
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
            if (s == null || cOptions == null || !cOptions.isInstance(s)) return;
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
            Object slot = slotAt(s, gx, gy);
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
            Object cursor = cursorItem(s);
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
