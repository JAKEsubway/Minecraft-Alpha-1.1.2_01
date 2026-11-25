package mcshim.lwjgl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class BufferUtils {
    private BufferUtils() {}
    public static ByteBuffer createByteBuffer(int n) { return ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder()); }
    public static FloatBuffer createFloatBuffer(int n) { return createByteBuffer(n * 4).asFloatBuffer(); }
    public static IntBuffer createIntBuffer(int n) { return createByteBuffer(n * 4).asIntBuffer(); }
}
