package mcshim.lwjgl.openal;

import mcshim.audio.Mixer;
import mcshim.lwjgl.LWJGLException;

/** "OpenAL" backed by mcshim's software mixer and the platform's audio output. */
public final class AL {
    private AL() {}

    public static void create() throws LWJGLException {
        if (!Mixer.start()) throw new LWJGLException("No audio output on this platform");
    }

    public static void destroy() { Mixer.stop(); }

    public static boolean isCreated() { return Mixer.isRunning(); }
}
