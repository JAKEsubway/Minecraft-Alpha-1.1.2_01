package mcshim.sound.midi;

public interface MidiChannel {
    void controlChange(int c, int v);
}
