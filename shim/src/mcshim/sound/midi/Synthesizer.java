package mcshim.sound.midi;

public interface Synthesizer extends MidiDevice {
    MidiChannel[] getChannels();
    Soundbank getDefaultSoundbank();
}
