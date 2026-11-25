package mcshim.lwjgl.openal;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import mcshim.audio.Mixer;

/** The OpenAL 1.0 calls the game's sound library makes, routed to the software mixer. */
public final class AL10 {
    private AL10() {}

    public static final int AL_NO_ERROR = 0;

    private static float[] floats(FloatBuffer b, int n) {
        float[] v = new float[n];
        int p = b.position();
        for (int i = 0; i < n && p + i < b.limit(); i++) v[i] = b.get(p + i);
        return v;
    }

    public static void alGenBuffers(IntBuffer b) { for (int i = b.position(); i < b.limit(); i++) b.put(i, Mixer.genBuffer()); }
    public static void alGenSources(IntBuffer b) { for (int i = b.position(); i < b.limit(); i++) b.put(i, Mixer.genSource()); }
    public static void alDeleteBuffers(IntBuffer b) { for (int i = b.position(); i < b.limit(); i++) Mixer.deleteBuffer(b.get(i)); }
    public static void alDeleteSources(IntBuffer b) { for (int i = b.position(); i < b.limit(); i++) Mixer.deleteSource(b.get(i)); }
    public static void alBufferData(int buf, int format, ByteBuffer data, int freq) { Mixer.bufferData(buf, format, data, freq); }
    public static int alGetError() { return AL_NO_ERROR; }
    public static int alGetSourcei(int src, int param) { return Mixer.getSourcei(src, param); }
    public static void alListener(int param, FloatBuffer v) { Mixer.listenerfv(param, floats(v, 6)); }
    public static void alListenerf(int param, float v) { Mixer.listenerf(param, v); }
    public static void alSource(int src, int param, FloatBuffer v) { Mixer.sourcefv(src, param, floats(v, 3)); }
    public static void alSourcef(int src, int param, float v) { Mixer.sourcef(src, param, v); }
    public static void alSourcei(int src, int param, int v) { Mixer.sourcei(src, param, v); }
    public static void alSourcePlay(int src) { Mixer.play(src); }
    public static void alSourcePause(int src) { Mixer.pause(src); }
    public static void alSourceStop(int src) { Mixer.stopSource(src); }
    public static void alSourceStop(IntBuffer srcs) { for (int i = srcs.position(); i < srcs.limit(); i++) Mixer.stopSource(srcs.get(i)); }
    public static void alSourceRewind(int src) { Mixer.rewind(src); }

    public static void alSourceQueueBuffers(int src, IntBuffer b) {
        int[] ids = new int[b.remaining()];
        for (int i = 0; i < ids.length; i++) ids[i] = b.get(b.position() + i);
        Mixer.queue(src, ids);
    }

    public static void alSourceUnqueueBuffers(int src, IntBuffer b) {
        int[] ids = Mixer.unqueue(src, b.remaining());
        for (int i = 0; i < ids.length; i++) b.put(b.position() + i, ids[i]);
    }
}
