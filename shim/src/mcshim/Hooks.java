package mcshim;

import java.util.ArrayList;

/**
 * Bridge between the game thread and the host UI.
 * Remap injects "Hooks.minecraft = this" into the Minecraft constructor.
 */
public final class Hooks {
    private Hooks() {}

    /** The running net.minecraft.client.Minecraft instance. */
    public static volatile Object minecraft;

    public interface FrameListener {
        /** Called on the game thread once per frame. */
        void onFrame();
    }

    private static final ArrayList<Runnable> queue = new ArrayList<Runnable>();
    private static final ArrayList<Runnable> running = new ArrayList<Runnable>();
    private static volatile FrameListener listener;

    public static void setFrameListener(FrameListener l) { listener = l; }

    /** Run r on the game thread at the next frame boundary. */
    public static void runOnGameThread(Runnable r) {
        synchronized (queue) { queue.add(r); }
    }

    /** Called from Display.update() on the game thread. */
    public static void frame() {
        synchronized (queue) {
            if (!queue.isEmpty()) { running.addAll(queue); queue.clear(); }
        }
        for (int i = 0; i < running.size(); i++) {
            try { running.get(i).run(); } catch (Throwable t) { t.printStackTrace(); }
        }
        running.clear();
        FrameListener l = listener;
        if (l != null) {
            try { l.onFrame(); } catch (Throwable t) { t.printStackTrace(); }
        }
    }
}
