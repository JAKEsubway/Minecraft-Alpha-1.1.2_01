package mcshim.sound.midi;

public interface Sequencer extends MidiDevice {
    boolean addMetaEventListener(MetaEventListener l);
    void removeMetaEventListener(MetaEventListener l);
    Transmitter getTransmitter() throws MidiUnavailableException;
    void setMicrosecondPosition(long p);
    void setSequence(Sequence s) throws InvalidMidiDataException;
    void start();
    void stop();
}
