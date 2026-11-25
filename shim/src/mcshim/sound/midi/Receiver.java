package mcshim.sound.midi;

public interface Receiver {
    void send(MidiMessage m, long t);
    void close();
}
