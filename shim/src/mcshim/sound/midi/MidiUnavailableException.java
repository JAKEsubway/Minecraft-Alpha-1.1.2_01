package mcshim.sound.midi;

public class MidiUnavailableException extends Exception {
    public MidiUnavailableException() { super("MIDI not supported"); }
}
