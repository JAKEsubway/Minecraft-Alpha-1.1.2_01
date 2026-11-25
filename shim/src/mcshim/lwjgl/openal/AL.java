package mcshim.lwjgl.openal;

import mcshim.lwjgl.LWJGLException;

/** No audio backend yet: creation fails, so the game's sound system stays silent. */
public final class AL {
    private AL() {}
    public static void create() throws LWJGLException { throw new LWJGLException("OpenAL not available on this port"); }
    public static void destroy() {}
    public static boolean isCreated() { return false; }
}
