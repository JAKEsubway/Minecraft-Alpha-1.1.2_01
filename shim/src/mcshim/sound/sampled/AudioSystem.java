package mcshim.sound.sampled;

public final class AudioSystem {
    private AudioSystem() {}
    public static AudioInputStream getAudioInputStream(java.io.InputStream in) throws UnsupportedAudioFileException, java.io.IOException {
        throw new UnsupportedAudioFileException("WAV decoding not available");
    }
}
