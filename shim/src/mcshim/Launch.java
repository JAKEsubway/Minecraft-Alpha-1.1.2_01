package mcshim;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Starts whichever Minecraft version the class loader holds: Alpha through
 * Minecraft.main, Indev (which only ever ran in a browser) through its applet.
 */
public final class Launch {
    private Launch() {}

    public static void start(ClassLoader cl, String user) throws Exception {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, cl);
            Method main = mc.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[] {user, "-"});
            return;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // no desktop entry point: start it the way the web page did
        }
        mcshim.applet.Applet.PARAMS.put("username", user);
        mcshim.applet.Applet.PARAMS.put("sessionid", "-");
        Class<?> ac = Class.forName("net.minecraft.client.MinecraftApplet", true, cl);
        final mcshim.applet.Applet applet = (mcshim.applet.Applet) ac.newInstance();
        Platform p = Platform.get();
        applet.setSize(p.width(), p.height());
        applet.init();
        applet.start();
        Runnable game = null;
        for (Field f : ac.getDeclaredFields()) {
            if (Runnable.class.isAssignableFrom(f.getType()) && !Thread.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Object o = f.get(applet);
                if (o != null) game = (Runnable) o;
            }
        }
        if (game == null) throw new IllegalStateException("applet made no game object");
        Thread t = new Thread(game, "Minecraft main thread");
        t.start();
    }
}
