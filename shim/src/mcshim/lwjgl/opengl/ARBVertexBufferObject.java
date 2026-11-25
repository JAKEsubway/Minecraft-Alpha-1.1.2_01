package mcshim.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Never used: GLContext reports the extension as missing. */
public final class ARBVertexBufferObject {
    private ARBVertexBufferObject() {}
    public static void glBindBufferARB(int target, int id) { GL11.gl.glBindBuffer(target, id); }
    public static void glBufferDataARB(int target, ByteBuffer data, int usage) { GL11.gl.glBufferData(target, data.remaining(), data, usage); }
    public static void glGenBuffersARB(IntBuffer out) {
        for (int i = out.position(); i < out.limit(); i++) out.put(i, GL11.gl.glGenBuffer());
    }
}
