package mcshim.sound.sampled;

public class AudioInputStream extends java.io.InputStream {
    private final java.io.InputStream in; private final AudioFormat fmt; private final long frames;
    public AudioInputStream(java.io.InputStream in, AudioFormat fmt, long frames) { this.in = in; this.fmt = fmt; this.frames = frames; }
    public AudioFormat getFormat() { return fmt; }
    public long getFrameLength() { return frames; }
    public int read() throws java.io.IOException { return in.read(); }
    public int read(byte[] b, int o, int l) throws java.io.IOException { return in.read(b, o, l); }
    public void close() throws java.io.IOException { in.close(); }
}
