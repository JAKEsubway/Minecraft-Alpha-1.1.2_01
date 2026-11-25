package mcshim.sound.midi;

/** No MIDI on Android: every lookup fails, which the sound library handles. */
public final class MidiSystem {
    private MidiSystem() {}
    public static MidiDevice getMidiDevice(MidiDevice.Info i) throws MidiUnavailableException { throw new MidiUnavailableException(); }
    public static MidiDevice.Info[] getMidiDeviceInfo() { return new MidiDevice.Info[0]; }
    public static Receiver getReceiver() throws MidiUnavailableException { throw new MidiUnavailableException(); }
    public static Sequence getSequence(java.net.URL u) throws InvalidMidiDataException, java.io.IOException { throw new InvalidMidiDataException(); }
    public static Sequencer getSequencer() throws MidiUnavailableException { throw new MidiUnavailableException(); }
    public static Synthesizer getSynthesizer() throws MidiUnavailableException { throw new MidiUnavailableException(); }
}
