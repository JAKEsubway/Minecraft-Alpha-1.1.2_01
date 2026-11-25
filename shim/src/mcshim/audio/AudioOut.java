package mcshim.audio;

/** A platform audio output: interleaved 16-bit stereo PCM. */
public interface AudioOut {
    /** Write frames (blocking is fine: it paces the mixer). */
    void write(short[] interleaved, int frames);

    void pause();

    void resume();

    void close();
}
