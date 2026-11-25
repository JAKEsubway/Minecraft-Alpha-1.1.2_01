package mcdesk;

import mcshim.Platform;
import mcshim.lwjgl.input.Keyboard;
import mcshim.lwjgl.input.Mouse;

/** Boots the remapped game on the shim and drives it with scripted input. */
public class TestMain {
    static DesktopPlatform p;

    public static void main(String[] args) throws Exception {
        p = new DesktopPlatform(854, 480);
        Platform.INSTANCE = p;
        System.setProperty("user.home", args.length > 0 ? args[0] : "/tmp/mchome");
        new java.io.File(System.getProperty("user.home")).mkdirs();
        mcshim.Hooks.setFrameListener(new mcshim.Hooks.FrameListener() {   // same per-frame hook as the Android app
            public void onFrame() { mcshim.ui.GuiTouch.dedupeButtons(); mcshim.ui.GuiTouch.nudgeFirstMusic(); }
        });
        Class.forName("net.minecraft.client.Minecraft").getMethod("main", String[].class)
                .invoke(null, (Object) new String[] {"Tester", "-"});
        Thread.sleep(500);
        String script = args.length > 1 ? args[1] : "";
        for (String step : script.split(";")) {
            step = step.trim();
            if (step.isEmpty()) continue;
            String[] t = step.split(" ");
            switch (t[0]) {
                case "wait": waitFrames(Integer.parseInt(t[1])); break;
                case "sleep": Thread.sleep(Long.parseLong(t[1])); break;
                case "shot": p.screenshotPath = t[1]; waitFrames(2); break;
                case "click": click(Integer.parseInt(t[1]), Integer.parseInt(t[2])); break;
                case "move": Mouse.injectMove(Integer.parseInt(t[1]), p.h - 1 - Integer.parseInt(t[2])); waitFrames(2); break;
                case "key": Keyboard.tap(Integer.parseInt(t[1]), (char) 0); break;
                case "hold": Keyboard.inject(Integer.parseInt(t[1]), true, (char) 0); break;
                case "release": Keyboard.inject(Integer.parseInt(t[1]), false, (char) 0); break;
                case "look": Mouse.injectDelta(Float.parseFloat(t[1]), Float.parseFloat(t[2])); break;
                case "mdown": Mouse.injectButton(Integer.parseInt(t[1]), true); break;
                case "mup": Mouse.injectButton(Integer.parseInt(t[1]), false); break;
                case "give": case "craft": case "dump": case "counts": case "screen": case "kind": case "tapgui": case "closebtn": case "music": case "aim": case "pig": case "pigstat": case "taplook": case "inv": case "tap": case "equip": case "unequip": game(t); break;
            }
            if (p.crashText != null) break;
        }
        System.out.println("frames=" + p.frames + " crashed=" + (p.crashText != null));
        Runtime.getRuntime().halt(p.crashText != null ? 2 : 0);
    }

    /** Run a crafting command on the game thread and wait for it. */
    static void game(final String[] t) throws Exception {
        final Object done = new Object();
        final boolean[] fin = {false};
        mcshim.Hooks.runOnGameThread(new Runnable() {
            public void run() {
                try {
                    if (t[0].equals("give")) {
                        System.out.println("give " + t[1] + " x" + t[2] + " -> " + mcshim.craft.Crafting.give(Integer.parseInt(t[1]), Integer.parseInt(t[2]), 0));
                    } else if (t[0].equals("craft")) {
                        int out = Integer.parseInt(t[1]);
                        for (mcshim.craft.Crafting.Recipe r : mcshim.craft.Crafting.recipes()) {
                            if (r.outId == out) { System.out.println("craft " + out + " -> " + mcshim.craft.Crafting.craft(r)); break; }
                        }
                    } else if (t[0].equals("counts")) {
                        System.out.println("counts " + mcshim.craft.Crafting.counts());
                    } else if (t[0].equals("pig")) {
                        // spawn a pig 2.5 blocks in front of the player and look at it
                        Object mc = mcshim.Hooks.minecraft;
                        Class<?> mcC = mc.getClass().getSuperclass();
                        Object world = field(mcC, "e").get(mc), player = field(mcC, "g").get(mc);
                        Class<?> ent = Class.forName("kh");
                        double px = field(ent, "ak").getDouble(player), py = field(ent, "al").getDouble(player), pz = field(ent, "am").getDouble(player);
                        float yaw = field(ent, "aq").getFloat(player);
                        double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
                        pig = Class.forName("mv").getConstructor(Class.forName("cn")).newInstance(world);
                        ent.getMethod("c", double.class, double.class, double.class, float.class, float.class)
                           .invoke(pig, px + dx * 2.5, py - 1.62, pz + dz * 2.5, yaw, 0f);
                        System.out.println("spawned pig: " + world.getClass().getMethod("a", ent).invoke(world, pig));
                        field(ent, "ar").setFloat(player, 22f);
                        field(ent, "aq").setFloat(player, yaw);
                    } else if (t[0].equals("pigstat")) {
                        StringBuilder b = new StringBuilder("pig ints:");
                        for (Class<?> c = pig.getClass(); c != Object.class; c = c.getSuperclass())
                            for (java.lang.reflect.Field f : c.getDeclaredFields())
                                if (f.getType() == int.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); b.append(' ').append(c.getName()).append('.').append(f.getName()).append('=').append(f.getInt(pig)); }
                        System.out.println(b);
                    } else if (t[0].equals("taplook")) {
                        // same decision the phone makes on a quick tap in the world
                        int btn = mcshim.ui.GuiTouch.aimingAtEntity() ? 0 : 1;
                        Mouse.injectButton(btn, true); Mouse.injectButton(btn, false);
                        System.out.println("taplook -> " + (btn == 0 ? "attack" : "use/place"));
                    } else if (t[0].equals("aim")) {
                        System.out.println("aim entity=" + mcshim.ui.GuiTouch.aimingAtEntity());
                    } else if (t[0].equals("music")) {
                        // start background music now instead of after the game's long random delay
                        java.lang.reflect.Field fa = mcshim.Hooks.minecraft.getClass().getSuperclass().getDeclaredField("A");
                        fa.setAccessible(true);
                        Object snd = fa.get(mcshim.Hooks.minecraft);
                        java.lang.reflect.Field fi = snd.getClass().getDeclaredField("i");
                        fi.setAccessible(true);
                        fi.setInt(snd, 0);
                        System.out.println("music countdown set to 0");
                    } else if (t[0].equals("closebtn")) {
                        int[] r = mcshim.ui.GuiTouch.closeButton(p.w, p.h);
                        System.out.println("closebtn " + (r == null ? "none" : r[0] + "," + r[1] + " - " + r[2] + "," + r[3]));
                    } else if (t[0].equals("kind")) {
                        System.out.println("kind " + mcshim.ui.GuiTouch.kind());
                    } else if (t[0].equals("tapgui")) {
                        // like a phone tap in an inventory screen: x y (window pixels, from top) [long]
                        int x = Integer.parseInt(t[1]), y = Integer.parseInt(t[2]);
                        boolean lng = t.length > 3;
                        int[] plan = mcshim.ui.GuiTouch.planTap(x, y, p.w, p.h, lng);
                        System.out.println("tapgui " + x + "," + y + (lng ? " long" : "") + " -> " + (plan == null ? "ignored" : "button " + plan[0] + " at " + plan[1] + "," + plan[2]));
                        if (plan != null) { Mouse.injectMove(plan[1], p.h - 1 - plan[2]); Mouse.injectButton(plan[0], true); Mouse.injectButton(plan[0], false); }
                    } else if (t[0].equals("inv") || t[0].equals("tap") || t[0].equals("equip") || t[0].equals("unequip")) {
                        if (t[0].equals("tap")) mcshim.craft.Crafting.tapSlot(Integer.parseInt(t[1]));
                        if (t[0].equals("equip")) System.out.println("equip -> " + mcshim.craft.Crafting.equip(Integer.parseInt(t[1])));
                        if (t[0].equals("unequip")) System.out.println("unequip -> " + mcshim.craft.Crafting.unequip(Integer.parseInt(t[1])));
                        mcshim.craft.Crafting.InvSnap s = mcshim.craft.Crafting.inventory();
                        StringBuilder b = new StringBuilder("inv cur=" + s.current + " armor=");
                        for (int i = 0; i < 4; i++) b.append(s.aId[i]).append(',');
                        b.append(" slots=");
                        for (int i = 0; i < 36; i++) if (s.id[i] > 0) b.append(i).append(':').append(s.id[i]).append('x').append(s.count[i]).append(s.armorType[i] >= 0 ? "(A" + s.armorType[i] + ")" : "").append(' ');
                        System.out.println(b);
                    } else if (t[0].equals("screen")) {
                        System.out.println("screen " + mcshim.craft.Crafting.currentScreen());
                    } else {
                        System.out.println("available=" + mcshim.craft.Crafting.isAvailable() + " err=" + mcshim.craft.Crafting.error());
                        for (mcshim.craft.Crafting.Recipe r : mcshim.craft.Crafting.recipes()) {
                            mcshim.craft.Crafting.Icon ic = mcshim.craft.Crafting.icon(r.outId, r.outDamage);
                            System.out.println("R " + r.outId + " x" + r.outCount + " " + r.w + "x" + r.h + " needs=" + r.needs
                                + " icon=" + ic.index + (ic.terrain ? "T" : "I") + (ic.block3d ? " cube " + ic.top + "/" + ic.side + "/" + ic.front : ""));
                        }
                    }
                } catch (Throwable e) { e.printStackTrace(); }
                synchronized (done) { fin[0] = true; done.notifyAll(); }
            }
        });
        synchronized (done) { long end = System.currentTimeMillis() + 30000; while (!fin[0] && System.currentTimeMillis() < end) done.wait(500); }
    }

    static Object pig;

    static java.lang.reflect.Field field(Class<?> c, String n) throws Exception {
        java.lang.reflect.Field f = c.getDeclaredField(n);
        f.setAccessible(true);
        return f;
    }

    /** Click at window pixel (x from left, y from top). */
    static void click(int x, int y) throws Exception {
        Mouse.injectMove(x, p.h - 1 - y);
        waitFrames(2);
        Mouse.injectButton(0, true);
        waitFrames(2);
        Mouse.injectButton(0, false);
        waitFrames(2);
    }

    static void waitFrames(int n) throws Exception {
        int target = p.frames + n;
        long dead = System.currentTimeMillis() + 120000;
        while (p.frames < target && System.currentTimeMillis() < dead && p.crashText == null) Thread.sleep(10);
    }
}
