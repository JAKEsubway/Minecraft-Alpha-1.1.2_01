package mcshim.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

import mcshim.Platform;

/**
 * Minimal software OpenAL: buffers, sources (static and streaming), listener,
 * inverse-distance-clamped attenuation, stereo panning, pitch. Everything the
 * game's sound library (paulscode LibraryLWJGLOpenAL) uses, nothing more.
 * All AL calls and the mixing thread share one lock.
 */
public final class Mixer {
    public static final int RATE = 44100;
    private static final int BLOCK = 512; // frames per mix pass (~12 ms)

    // AL enums
    static final int SOURCE_RELATIVE = 0x202, PITCH = 0x1003, POSITION = 0x1004, VELOCITY = 0x1006,
            LOOPING = 0x1007, BUFFER = 0x1009, GAIN = 0x100A, ORIENTATION = 0x100F, SOURCE_STATE = 0x1010,
            INITIAL = 0x1011, PLAYING = 0x1012, PAUSED = 0x1013, STOPPED = 0x1014,
            BUFFERS_QUEUED = 0x1015, BUFFERS_PROCESSED = 0x1016,
            REFERENCE_DISTANCE = 0x1020, ROLLOFF_FACTOR = 0x1021, MAX_DISTANCE = 0x1023,
            MONO8 = 0x1100, MONO16 = 0x1101, STEREO8 = 0x1102, STEREO16 = 0x1103;

    static final class Buf {
        short[] pcm = new short[0]; // interleaved
        int channels = 1, rate = RATE, frames;
    }

    static final class Src {
        int state = INITIAL;
        final ArrayList<Integer> queue = new ArrayList<Integer>();
        int cur;          // index into queue of the buffer being played
        double pos;       // frame position inside that buffer
        int processed;    // buffers fully played (streaming)
        boolean looping, relative;
        float gain = 1, pitch = 1, ref = 1, rolloff = 1, max = Float.MAX_VALUE;
        final float[] p = new float[3];
    }

    private static final Object LOCK = new Object();
    private static final HashMap<Integer, Buf> bufs = new HashMap<Integer, Buf>();
    private static final HashMap<Integer, Src> srcs = new HashMap<Integer, Src>();
    private static int nextId = 1;
    private static final float[] lPos = new float[3];
    private static final float[] lAt = {0, 0, -1}, lUp = {0, 1, 0};
    private static float lGain = 1;

    private static AudioOut out;
    private static Thread thread;
    private static volatile boolean running;

    private Mixer() {}

    // ------------------------------------------------------------------
    public static boolean start() {
        synchronized (LOCK) {
            if (running) return true;
            out = Platform.get().openAudio(RATE);
            if (out == null) return false;
            running = true;
        }
        thread = new Thread(new Runnable() {
            public void run() { loop(); }
        }, "mcshim audio");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
        return true;
    }

    public static void stop() {
        running = false;
        Thread t = thread;
        if (t != null) { try { t.join(500); } catch (InterruptedException ignored) {} }
        synchronized (LOCK) {
            if (out != null) out.close();
            out = null;
            bufs.clear();
            srcs.clear();
        }
    }

    public static boolean isRunning() { return running; }

    private static void loop() {
        short[] block = new short[BLOCK * 2];
        float[] acc = new float[BLOCK * 2];
        boolean paused = false;
        while (running) {
            AudioOut o = out;
            if (o == null) break;
            // fall silent while the app is in the background
            if (!Platform.get().isActive()) {
                if (!paused) { o.pause(); paused = true; }
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                continue;
            } else if (paused) {
                o.resume();
                paused = false;
            }
            java.util.Arrays.fill(acc, 0f);
            synchronized (LOCK) {
                for (Src s : srcs.values()) if (s.state == PLAYING) mix(s, acc);
            }
            for (int i = 0; i < acc.length; i++) {
                float v = acc[i];
                if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
                block[i] = (short) (v * 32767f);
            }
            o.write(block, BLOCK);
        }
    }

    /** Mix one block of source s into acc (lock held). */
    private static void mix(Src s, float[] acc) {
        if (s.queue.isEmpty()) { s.state = STOPPED; return; }
        // --- spatial parameters for this block ---
        float px = s.p[0], py = s.p[1], pz = s.p[2];
        if (!s.relative) { px -= lPos[0]; py -= lPos[1]; pz -= lPos[2]; }
        float dist = (float) Math.sqrt(px * px + py * py + pz * pz);
        float g = s.gain * lGain;
        if (s.rolloff > 0) {
            float d = Math.max(s.ref, Math.min(dist, s.max));
            g *= s.ref / (s.ref + s.rolloff * (d - s.ref));
        }
        // right vector = at x up
        float rx = lAt[1] * lUp[2] - lAt[2] * lUp[1];
        float ry = lAt[2] * lUp[0] - lAt[0] * lUp[2];
        float rz = lAt[0] * lUp[1] - lAt[1] * lUp[0];
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        float pan = 0;
        if (dist > 0.01f && rl > 0) pan = (px * rx + py * ry + pz * rz) / (dist * rl);
        // keep ~20% in the far ear so hard-left/right sounds stay natural on headphones
        float gl = g * Math.min(1f, 1f - 0.8f * pan), gr = g * Math.min(1f, 1f + 0.8f * pan);
        if (g <= 0.0001f) gl = gr = 0;

        for (int f = 0; f < BLOCK; f++) {
            Buf b = bufs.get(s.queue.get(s.cur));
            if (b == null || b.frames == 0) { if (!advance(s)) return; continue; }
            if (s.pos >= b.frames) { if (!advance(s)) return; f--; continue; }
            int i = (int) s.pos;
            float t = (float) (s.pos - i);
            int j = Math.min(i + 1, b.frames - 1);
            if (b.channels == 1) {
                float v = (b.pcm[i] + (b.pcm[j] - b.pcm[i]) * t) / 32768f;
                acc[f * 2] += v * gl;
                acc[f * 2 + 1] += v * gr;
            } else { // stereo (music): no positioning
                float l = (b.pcm[i * 2] + (b.pcm[j * 2] - b.pcm[i * 2]) * t) / 32768f;
                float r = (b.pcm[i * 2 + 1] + (b.pcm[j * 2 + 1] - b.pcm[i * 2 + 1]) * t) / 32768f;
                acc[f * 2] += l * g;
                acc[f * 2 + 1] += r * g;
            }
            s.pos += s.pitch * b.rate / (double) RATE;
        }
    }

    /** Move to the next queued buffer; false when the source has stopped. */
    private static boolean advance(Src s) {
        s.pos = 0;
        if (s.cur + 1 < s.queue.size()) {
            s.cur++;
            s.processed++;
            return true;
        }
        if (s.looping) { s.cur = 0; s.processed = 0; return true; }
        s.processed = s.queue.size(); // everything played
        s.cur = Math.max(0, s.queue.size() - 1);
        s.pos = Double.MAX_VALUE;     // stays "finished" if more buffers get queued later
        s.state = STOPPED;
        return false;
    }

    // ------------------------------------------------------------------
    // AL entry points (called by mcshim.lwjgl.openal.AL10)
    public static int genBuffer() { synchronized (LOCK) { int id = nextId++; bufs.put(id, new Buf()); return id; } }

    public static int genSource() { synchronized (LOCK) { int id = nextId++; srcs.put(id, new Src()); return id; } }

    public static void deleteBuffer(int id) { synchronized (LOCK) { bufs.remove(id); } }

    public static void deleteSource(int id) { synchronized (LOCK) { srcs.remove(id); } }

    public static void bufferData(int id, int format, ByteBuffer data, int freq) {
        int ch = (format == STEREO8 || format == STEREO16) ? 2 : 1;
        boolean eight = format == MONO8 || format == STEREO8;
        ByteBuffer d = data.duplicate().order(ByteOrder.nativeOrder());
        int bytes = d.remaining();
        int samples = eight ? bytes : bytes / 2;
        short[] pcm = new short[samples];
        int p0 = d.position();
        for (int i = 0; i < samples; i++) {
            pcm[i] = eight ? (short) (((d.get(p0 + i) & 0xFF) - 128) << 8) : d.getShort(p0 + i * 2);
        }
        synchronized (LOCK) {
            Buf b = bufs.get(id);
            if (b == null) { b = new Buf(); bufs.put(id, b); }
            b.pcm = pcm;
            b.channels = ch;
            b.rate = freq > 0 ? freq : RATE;
            b.frames = samples / ch;
        }
    }

    public static void sourcei(int id, int param, int v) {
        synchronized (LOCK) {
            Src s = srcs.get(id);
            if (s == null) return;
            switch (param) {
                case BUFFER:
                    s.queue.clear();
                    if (v != 0) s.queue.add(v);
                    s.cur = 0; s.pos = 0; s.processed = 0;
                    break;
                case LOOPING: s.looping = v != 0; break;
                case SOURCE_RELATIVE: s.relative = v != 0; break;
                default: sourcef(s, param, v);
            }
        }
    }

    public static void sourcef(int id, int param, float v) {
        synchronized (LOCK) {
            Src s = srcs.get(id);
            if (s != null) sourcef(s, param, v);
        }
    }

    private static void sourcef(Src s, int param, float v) {
        switch (param) {
            case GAIN: s.gain = Math.max(0, v); break;
            case PITCH: s.pitch = Math.max(0.01f, v); break;
            case REFERENCE_DISTANCE: s.ref = Math.max(0.0001f, v); break;
            case ROLLOFF_FACTOR: s.rolloff = Math.max(0, v); break;
            case MAX_DISTANCE: s.max = v; break;
            case LOOPING: s.looping = v != 0; break;
            case SOURCE_RELATIVE: s.relative = v != 0; break;
        }
    }

    public static void sourcefv(int id, int param, float[] v) {
        synchronized (LOCK) {
            Src s = srcs.get(id);
            if (s != null && param == POSITION) { s.p[0] = v[0]; s.p[1] = v[1]; s.p[2] = v[2]; }
        }
    }

    public static void listenerf(int param, float v) {
        synchronized (LOCK) { if (param == GAIN) lGain = Math.max(0, v); }
    }

    public static void listenerfv(int param, float[] v) {
        synchronized (LOCK) {
            if (param == POSITION) { lPos[0] = v[0]; lPos[1] = v[1]; lPos[2] = v[2]; }
            else if (param == ORIENTATION) {
                lAt[0] = v[0]; lAt[1] = v[1]; lAt[2] = v[2];
                lUp[0] = v[3]; lUp[1] = v[4]; lUp[2] = v[5];
            }
        }
    }

    public static int getSourcei(int id, int param) {
        synchronized (LOCK) {
            Src s = srcs.get(id);
            if (s == null) return 0;
            switch (param) {
                case SOURCE_STATE: return s.state;
                case BUFFERS_QUEUED: return s.queue.size();
                case BUFFERS_PROCESSED: return s.state == STOPPED ? s.queue.size() : Math.min(s.processed, s.queue.size());
                case BUFFER: return s.queue.isEmpty() ? 0 : s.queue.get(s.cur);
                case LOOPING: return s.looping ? 1 : 0;
                case SOURCE_RELATIVE: return s.relative ? 1 : 0;
            }
            return 0;
        }
    }

    /** OpenAL rules: resume if paused, otherwise (re)start from the head of the queue. */
    public static void play(int id) {
        synchronized (LOCK) {
            Src s = srcs.get(id);
            if (s == null) return;
            if (s.state == PAUSED) { s.state = PLAYING; return; }
            s.cur = 0;
            s.pos = 0;
            s.processed = 0;
            s.state = s.queue.isEmpty() ? STOPPED : PLAYING;
        }
    }

    public static void pause(int id) {
        synchronized (LOCK) { Src s = srcs.get(id); if (s != null && s.state == PLAYING) s.state = PAUSED; }
    }

    public static void stopSource(int id) {
        synchronized (LOCK) {
            Src s = srcs.get(id);
            if (s != null && s.state != INITIAL) { s.state = STOPPED; s.processed = s.queue.size(); }
        }
    }

    public static void rewind(int id) {
        synchronized (LOCK) {
            Src s = srcs.get(id);
            if (s != null) { s.state = INITIAL; s.cur = 0; s.pos = 0; s.processed = 0; }
        }
    }

    public static void queue(int id, int[] buffers) {
        synchronized (LOCK) {
            Src s = srcs.get(id);
            if (s == null) return;
            for (int b : buffers) s.queue.add(b);
        }
    }

    /** Remove up to n processed buffers from the front of the queue; returns their ids. */
    public static int[] unqueue(int id, int n) {
        synchronized (LOCK) {
            Src s = srcs.get(id);
            if (s == null) return new int[0];
            int done = s.state == STOPPED ? s.queue.size() : Math.min(s.processed, s.queue.size());
            n = Math.min(n, done);
            int[] ids = new int[n];
            for (int i = 0; i < n; i++) ids[i] = s.queue.remove(0);
            s.processed -= n;
            if (s.processed < 0) s.processed = 0;
            s.cur = Math.max(0, s.cur - n);
            if (s.state == STOPPED && s.queue.isEmpty()) { s.pos = 0; s.cur = 0; }
            return ids;
        }
    }
}
