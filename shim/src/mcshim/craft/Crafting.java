package mcshim.craft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mcshim.Hooks;

/**
 * Pocket-Edition style crafting for Minecraft Alpha 1.1.2_01: lists the game's
 * own recipes and crafts straight from the player's inventory.
 *
 * Everything here touches live game objects, so every public method except
 * isAvailable() must run on the game thread (see Hooks.runOnGameThread).
 * Obfuscated names are specific to a1.1.2_01; if any is missing the feature
 * just reports itself unavailable.
 */
public final class Crafting {
    private Crafting() {}

    public static final int SCREEN_NONE = 0, SCREEN_INVENTORY = 1, SCREEN_WORKBENCH = 2;

    /** One recipe: a w*h grid of item ids (0 = empty) giving out* items. */
    public static final class Recipe {
        public int index, w, h;
        public int[] ids;
        public int outId, outCount, outDamage;
        /** ingredient id -> count needed for one craft */
        public final Map<Integer, Integer> needs = new HashMap<Integer, Integer>();
        public boolean fits2x2() { return w <= 2 && h <= 2; }
    }

    /** How to draw an item id. */
    public static final class Icon {
        public int id;
        public boolean block3d;      // draw as an isometric cube from terrain.png
        public int top, side, front; // terrain.png indices for cubes
        public int index;            // flat icon index (terrain.png if id < 256, else gui/items.png)
        public boolean terrain;      // flat icon comes from terrain.png
    }

    // ---- reflection (a1.1.2_01 names) ----
    private static boolean tried, ok;
    private static String error;
    private static Field fPlayer, fScreen, fInventory, fMain, fArmor, fCurrent, fArmorType;
    private static Class<?> cArmor;
    private static Field fStackCount, fStackId, fStackDamage;
    private static Field fRecipeW, fRecipeH, fRecipeIds, fRecipeOut, fItemMaxStack;
    private static Method mManager, mStackIcon, mBlockTex, mRenderType, mRender3d;
    private static Field fRecipes, fItems, fBlocks;
    private static Class<?> cStack, cInvGui, cBenchGui;
    private static java.lang.reflect.Constructor<?> newStack;
    private static List<Recipe> recipes;
    private static final HashMap<Integer, Icon> icons = new HashMap<Integer, Icon>();

    private static Field field(Class<?> c, String n) throws Exception {
        Field f = c.getDeclaredField(n);
        f.setAccessible(true);
        return f;
    }

    private static Method method(Class<?> c, String n, Class<?>... args) throws Exception {
        Method m = c.getDeclaredMethod(n, args);
        m.setAccessible(true);
        return m;
    }

    private static synchronized boolean init() {
        if (tried) return ok;
        tried = true;
        try {
            ClassLoader cl = Crafting.class.getClassLoader();
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, cl);
            Class<?> player = Class.forName("dm", false, cl);
            Class<?> inv = Class.forName("eu", false, cl);
            Class<?> item = Class.forName("di", false, cl);
            Class<?> block = Class.forName("ly", false, cl);
            Class<?> manager = Class.forName("dw", false, cl);
            Class<?> recipe = Class.forName("bv", false, cl);
            Class<?> renderBlocks = Class.forName("bc", false, cl);
            cStack = Class.forName("ev", false, cl);
            cInvGui = Class.forName("lo", false, cl);
            cBenchGui = Class.forName("hx", false, cl);

            fPlayer = field(mc, "g");
            fScreen = field(mc, "p");
            fInventory = field(player, "b");
            fMain = field(inv, "a");
            fArmor = field(inv, "b");
            fCurrent = field(inv, "d");
            cArmor = Class.forName("mr", false, cl);
            fArmorType = field(cArmor, "aX");
            fStackCount = field(cStack, "a");
            fStackId = field(cStack, "c");
            fStackDamage = field(cStack, "d");
            newStack = cStack.getDeclaredConstructor(int.class, int.class, int.class);
            mStackIcon = method(cStack, "b");
            fRecipeW = field(recipe, "b");
            fRecipeH = field(recipe, "c");
            fRecipeIds = field(recipe, "d");
            fRecipeOut = field(recipe, "e");
            mManager = method(manager, "a");
            fRecipes = field(manager, "b");
            fItems = field(item, "c");
            fItemMaxStack = field(item, "aT");
            fBlocks = field(block, "n");
            mBlockTex = method(block, "a", int.class);
            mRenderType = method(block, "f");
            mRender3d = method(renderBlocks, "a", int.class);
            ok = true;
        } catch (Throwable t) {
            error = t.toString();
            ok = false;
        }
        return ok;
    }

    public static boolean isAvailable() { return init(); }

    public static String error() { return error; }

    // ------------------------------------------------------------------
    /** Which crafting-capable screen is open (game thread). */
    public static int currentScreen() {
        Object mc = Hooks.minecraft;
        if (mc == null || !init()) return SCREEN_NONE;
        try {
            Object s = fScreen.get(mc);
            if (s == null) return SCREEN_NONE;
            if (cBenchGui.isInstance(s)) return SCREEN_WORKBENCH;
            if (cInvGui.isInstance(s)) return SCREEN_INVENTORY;
        } catch (Exception ignored) {}
        return SCREEN_NONE;
    }

    /** All recipes in the game's own order (game thread; cached). */
    public static List<Recipe> recipes() throws Exception {
        if (recipes != null) return recipes;
        init();
        List<?> raw = (List<?>) fRecipes.get(mManager.invoke(null));
        ArrayList<Recipe> out = new ArrayList<Recipe>();
        for (int i = 0; i < raw.size(); i++) {
            Object r = raw.get(i);
            if (r == null || !fRecipeIds.getDeclaringClass().isInstance(r)) continue;
            Recipe rc = new Recipe();
            rc.index = i;
            rc.w = fRecipeW.getInt(r);
            rc.h = fRecipeH.getInt(r);
            rc.ids = ((int[]) fRecipeIds.get(r)).clone();
            Object st = fRecipeOut.get(r);
            rc.outId = fStackId.getInt(st);
            rc.outCount = fStackCount.getInt(st);
            rc.outDamage = fStackDamage.getInt(st);
            for (int id : rc.ids) {
                if (id <= 0) continue;
                Integer n = rc.needs.get(id);
                rc.needs.put(id, n == null ? 1 : n + 1);
            }
            if (!rc.needs.isEmpty()) out.add(rc);
        }
        recipes = out;
        return out;
    }

    private static Object[] mainInventory() throws Exception {
        Object mc = Hooks.minecraft;
        if (mc == null || !init()) return null;
        Object player = fPlayer.get(mc);
        if (player == null) return null;
        return (Object[]) fMain.get(fInventory.get(player));
    }

    /** id -> total count in the main inventory (game thread). */
    public static Map<Integer, Integer> counts() throws Exception {
        HashMap<Integer, Integer> m = new HashMap<Integer, Integer>();
        Object[] inv = mainInventory();
        if (inv == null) return m;
        for (int i = 0; i < Math.min(36, inv.length); i++) {
            Object s = inv[i];
            if (s == null) continue;
            int id = fStackId.getInt(s), n = fStackCount.getInt(s);
            if (n <= 0) continue;
            Integer old = m.get(id);
            m.put(id, old == null ? n : old + n);
        }
        return m;
    }

    /** How many times r can be crafted with what's in counts. */
    public static int maxCrafts(Recipe r, Map<Integer, Integer> counts) {
        int best = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Integer> e : r.needs.entrySet()) {
            Integer have = counts.get(e.getKey());
            best = Math.min(best, (have == null ? 0 : have) / e.getValue());
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private static int maxStack(int id) {
        if (id < 256) return 64;
        try {
            Object[] items = (Object[]) fItems.get(null);
            Object it = id < items.length ? items[id] : null;
            return it == null ? 64 : fItemMaxStack.getInt(it);
        } catch (Exception e) {
            return 64;
        }
    }

    /** Room left for (id, damage) in slots 0..35. */
    private static int room(Object[] inv, int id, int dmg) throws Exception {
        int max = maxStack(id), room = 0;
        for (int i = 0; i < 36 && i < inv.length; i++) {
            Object s = inv[i];
            if (s == null) room += max;
            else if (fStackId.getInt(s) == id && fStackDamage.getInt(s) == dmg && max > 1)
                room += Math.max(0, max - fStackCount.getInt(s));
        }
        return room;
    }

    /**
     * Craft r once (game thread). Returns null on success or a short reason.
     * Takes ingredients from the backpack first and the hotbar last.
     */
    public static String craft(Recipe r) throws Exception {
        Object[] inv = mainInventory();
        if (inv == null) return "No player";
        if (maxCrafts(r, counts()) < 1) return "Not enough materials";
        // ingredients leave before the result arrives, but be conservative about space
        if (room(inv, r.outId, r.outDamage) < r.outCount) {
            // maybe consuming ingredients frees a slot: check by simulation
            int freed = 0;
            Map<Integer, Integer> c = counts();
            for (Map.Entry<Integer, Integer> e : r.needs.entrySet()) {
                if (c.get(e.getKey()).intValue() == e.getValue().intValue()) freed++;
            }
            if (freed == 0) return "Inventory full";
        }
        for (Map.Entry<Integer, Integer> e : r.needs.entrySet()) {
            int id = e.getKey(), left = e.getValue();
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                int from = pass == 0 ? 9 : 0, to = pass == 0 ? 36 : 9;
                for (int i = to - 1; i >= from && left > 0; i--) {
                    Object s = inv[i];
                    if (s == null || fStackId.getInt(s) != id) continue;
                    int n = fStackCount.getInt(s), take = Math.min(n, left);
                    left -= take;
                    if (n - take <= 0) inv[i] = null;
                    else fStackCount.setInt(s, n - take);
                }
            }
        }
        int left = r.outCount, max = maxStack(r.outId);
        for (int i = 0; i < 36 && left > 0 && max > 1; i++) {
            Object s = inv[i];
            if (s == null || fStackId.getInt(s) != r.outId || fStackDamage.getInt(s) != r.outDamage) continue;
            int n = fStackCount.getInt(s), add = Math.min(max - n, left);
            if (add <= 0) continue;
            fStackCount.setInt(s, n + add);
            left -= add;
        }
        for (int i = 0; i < 36 && left > 0; i++) {
            if (inv[i] != null) continue;
            int add = Math.min(max, left);
            inv[i] = newStack.newInstance(r.outId, add, r.outDamage);
            left -= add;
        }
        return left > 0 ? "Inventory full, some items lost" : null;
    }

    // ------------------------------------------------------------------
    // PE-style inventory screen support
    // ------------------------------------------------------------------

    /** Copy of the player's inventory. Slots 0-8 are the hotbar. armor[0] = helmet ... armor[3] = boots. */
    public static final class InvSnap {
        public final int[] id = new int[36], count = new int[36], dmg = new int[36], armorType = new int[36];
        public final int[] aId = new int[4], aCount = new int[4], aDmg = new int[4];
        public int current;
        public final HashMap<Integer, Icon> icons = new HashMap<Integer, Icon>();
    }

    private static Object inventoryObj() throws Exception {
        Object mc = Hooks.minecraft;
        if (mc == null || !init()) return null;
        Object player = fPlayer.get(mc);
        return player == null ? null : fInventory.get(player);
    }

    private static int armorTypeOf(Object stack) throws Exception {
        if (stack == null) return -1;
        Object[] items = (Object[]) fItems.get(null);
        int id = fStackId.getInt(stack);
        Object it = id >= 0 && id < items.length ? items[id] : null;
        return it != null && cArmor.isInstance(it) ? fArmorType.getInt(it) : -1;
    }

    public static InvSnap inventory() throws Exception {
        Object inv = inventoryObj();
        if (inv == null) return null;
        InvSnap s = new InvSnap();
        Object[] main = (Object[]) fMain.get(inv), armor = (Object[]) fArmor.get(inv);
        for (int i = 0; i < 36 && i < main.length; i++) {
            Object st = main[i];
            s.armorType[i] = -1;
            if (st == null || fStackCount.getInt(st) <= 0) continue;
            s.id[i] = fStackId.getInt(st);
            s.count[i] = fStackCount.getInt(st);
            s.dmg[i] = fStackDamage.getInt(st);
            s.armorType[i] = armorTypeOf(st);
            if (!s.icons.containsKey(s.id[i])) s.icons.put(s.id[i], icon(s.id[i], s.dmg[i]));
        }
        for (int t = 0; t < 4; t++) {
            Object st = armor[3 - t];
            if (st == null) continue;
            s.aId[t] = fStackId.getInt(st);
            s.aCount[t] = fStackCount.getInt(st);
            s.aDmg[t] = fStackDamage.getInt(st);
            if (!s.icons.containsKey(s.aId[t])) s.icons.put(s.aId[t], icon(s.aId[t], s.aDmg[t]));
        }
        s.current = fCurrent.getInt(inv);
        return s;
    }

    /** Hotbar slot tapped: select it. Backpack slot tapped: swap it into the selected hotbar slot. */
    public static void tapSlot(int slot) throws Exception {
        Object inv = inventoryObj();
        if (inv == null || slot < 0 || slot >= 36) return;
        if (slot < 9) { fCurrent.setInt(inv, slot); return; }
        Object[] main = (Object[]) fMain.get(inv);
        int cur = fCurrent.getInt(inv);
        Object t = main[cur];
        main[cur] = main[slot];
        main[slot] = t;
    }

    /** Put on the armor in this slot (swapping with what is worn). */
    public static boolean equip(int slot) throws Exception {
        Object inv = inventoryObj();
        if (inv == null || slot < 0 || slot >= 36) return false;
        Object[] main = (Object[]) fMain.get(inv), armor = (Object[]) fArmor.get(inv);
        int type = armorTypeOf(main[slot]);
        if (type < 0 || type > 3) return false;
        Object t = armor[3 - type];
        armor[3 - type] = main[slot];
        main[slot] = t;
        return true;
    }

    /** Take off armor piece (0 helmet .. 3 boots) into a free slot, backpack first. */
    public static boolean unequip(int type) throws Exception {
        Object inv = inventoryObj();
        if (inv == null || type < 0 || type > 3) return false;
        Object[] main = (Object[]) fMain.get(inv), armor = (Object[]) fArmor.get(inv);
        if (armor[3 - type] == null) return false;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = pass == 0 ? 9 : 0; i < (pass == 0 ? 36 : 9); i++) {
                if (main[i] == null) { main[i] = armor[3 - type]; armor[3 - type] = null; return true; }
            }
        }
        return false;
    }

    /** Put items straight into the inventory (testing / debugging). */
    public static boolean give(int id, int count, int dmg) throws Exception {
        Object[] inv = mainInventory();
        if (inv == null) return false;
        for (int i = 0; i < 36; i++) {
            if (inv[i] == null) { inv[i] = newStack.newInstance(id, count, dmg); return true; }
        }
        return false;
    }

    /** Icon info for an item id (game thread; cached). */
    public static Icon icon(int id, int dmg) {
        Icon ic = icons.get(id);
        if (ic != null) return ic;
        ic = new Icon();
        ic.id = id;
        try {
            Object st = newStack.newInstance(id, 1, dmg);
            ic.index = (Integer) mStackIcon.invoke(st);
            ic.terrain = id < 256;
            if (id < 256) {
                Object[] blocks = (Object[]) fBlocks.get(null);
                Object b = blocks[id];
                if (b != null) {
                    int type = (Integer) mRenderType.invoke(b);
                    ic.block3d = (Boolean) mRender3d.invoke(null, type);
                    ic.top = (Integer) mBlockTex.invoke(b, 1);
                    ic.side = (Integer) mBlockTex.invoke(b, 2);
                    ic.front = (Integer) mBlockTex.invoke(b, 3);
                }
            }
        } catch (Throwable t) {
            ic.index = 0;
        }
        icons.put(id, ic);
        return ic;
    }
}
