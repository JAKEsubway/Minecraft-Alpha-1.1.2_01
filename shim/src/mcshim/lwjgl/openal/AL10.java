package mcshim.lwjgl.openal;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class AL10 {
    private AL10() {}
    public static void alBufferData(int b, int fmt, ByteBuffer data, int freq) {}
    public static void alDeleteBuffers(IntBuffer b) {}
    public static void alDeleteSources(IntBuffer b) {}
    public static void alGenBuffers(IntBuffer b) { for (int i = b.position(); i < b.limit(); i++) b.put(i, i + 1); }
    public static void alGenSources(IntBuffer b) { for (int i = b.position(); i < b.limit(); i++) b.put(i, i + 1); }
    public static int alGetError() { return 0; }
    public static int alGetSourcei(int s, int p) { return 0; }
    public static void alListener(int p, FloatBuffer v) {}
    public static void alListenerf(int p, float v) {}
    public static void alSource(int s, int p, FloatBuffer v) {}
    public static void alSourcePause(int s) {}
    public static void alSourcePlay(int s) {}
    public static void alSourceQueueBuffers(int s, IntBuffer b) {}
    public static void alSourceRewind(int s) {}
    public static void alSourceStop(int s) {}
    public static void alSourceStop(IntBuffer s) {}
    public static void alSourceUnqueueBuffers(int s, IntBuffer b) {}
    public static void alSourcef(int s, int p, float v) {}
    public static void alSourcei(int s, int p, int v) {}
}
