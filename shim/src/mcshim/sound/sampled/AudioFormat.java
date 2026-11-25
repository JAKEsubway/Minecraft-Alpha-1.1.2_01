package mcshim.sound.sampled;

public class AudioFormat {
    private final float rate; private final int bits, channels; private final boolean signed, bigEndian;
    public AudioFormat(float rate, int bits, int channels, boolean signed, boolean bigEndian) {
        this.rate = rate; this.bits = bits; this.channels = channels; this.signed = signed; this.bigEndian = bigEndian;
    }
    public int getChannels() { return channels; }
    public float getSampleRate() { return rate; }
    public int getSampleSizeInBits() { return bits; }
    public boolean isBigEndian() { return bigEndian; }
}
