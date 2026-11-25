package mcshim.lwjgl.opengl;

import java.nio.IntBuffer;

/** Never used: GLContext reports the extension as missing. */
public final class ARBOcclusionQuery {
    private ARBOcclusionQuery() {}
    public static void glBeginQueryARB(int target, int id) {}
    public static void glEndQueryARB(int target) {}
    public static void glGenQueriesARB(IntBuffer out) {
        for (int i = out.position(); i < out.limit(); i++) out.put(i, i + 1);
    }
    public static void glGetQueryObjectuARB(int id, int pname, IntBuffer out) { out.put(out.position(), 1); }
}
