package mcdesk;

import java.io.FileOutputStream;
import java.io.IOException;

import mcshim.audio.AudioOut;

/** Test audio output: writes 44.1 kHz stereo s16le to a file, paced in real time. */
final class RawAudioOut implements AudioOut {
    private final FileOutputStream out;
    private final byte[] bytes = new byte[512 * 4];
    private long framesWritten, startNanos = System.nanoTime();
    private final int rate;

    RawAudioOut(String path, int rate) throws IOException {
        out = new FileOutputStream(path);
        this.rate = rate;
    }

    public void write(short[] pcm, int frames) {
        byte[] b = frames * 4 <= bytes.length ? bytes : new byte[frames * 4];
        for (int i = 0; i < frames * 2; i++) { b[i * 2] = (byte) pcm[i]; b[i * 2 + 1] = (byte) (pcm[i] >> 8); }
        try { out.write(b, 0, frames * 4); out.flush(); } catch (IOException ignored) {}
        framesWritten += frames;
        long due = startNanos + framesWritten * 1000000000L / rate;
        long wait = due - System.nanoTime();
        if (wait > 0) { try { Thread.sleep(wait / 1000000, (int) (wait % 1000000)); } catch (InterruptedException ignored) {} }
    }

    public void pause() {}

    public void resume() { startNanos = System.nanoTime() - framesWritten * 1000000000L / rate; }

    public void close() { try { out.close(); } catch (IOException ignored) {} }
}
