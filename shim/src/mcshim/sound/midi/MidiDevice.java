package mcshim.sound.midi;

public interface MidiDevice {
    class Info {
        public String getName() { return "none"; }
    }
    void open() throws MidiUnavailableException;
    void close();
    Receiver getReceiver() throws MidiUnavailableException;
}
