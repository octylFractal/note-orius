package me.kenzierocks.noteorius.midi;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.charset.StandardCharsets;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;

import me.kenzierocks.noteorius.lowlevel.NtOrNote;
import me.kenzierocks.noteorius.lowlevel.NtOrPitch;
import me.kenzierocks.noteorius.lowlevel.NtOrPitchKey;

public class Midi {

    public interface JavaxMidiRunnable extends JavaxMidiCallable<Void> {

        @Override
        default Void call() throws InvalidMidiDataException {
            run();
            return null;
        }

        void run() throws InvalidMidiDataException;

    }

    public interface JavaxMidiCallable<V> {

        V call() throws InvalidMidiDataException;

    }

    public static void safeMidiRun(JavaxMidiRunnable runnable) {
        safeMidiRet(runnable);
    }

    public static <V> V safeMidiRet(JavaxMidiCallable<V> call) {
        try {
            return call.call();
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Converts a tempo between beats per minute and microseconds per beat.
     * 
     * @param tempo
     * @return
     */
    private static long convertTempo(int tempo) {
        return (long) (((double) 60000000l) / tempo);
    }

    /**
     * Generate a {@code set_tempo} MIDI message.
     * 
     * @param tempo
     *            - tempo, in BPM
     * @return the message
     */
    public static MidiMessage setTempo(int tempo) throws InvalidMidiDataException {
        long converted = convertTempo(tempo);
        if (converted > 0xFF_FF_FF) {
            throw new IllegalArgumentException("Tempo is too large for MIDI!");
        }
        int iConv = (int) converted;
        return new MetaMessage(0x51, new byte[] {
                (byte) ((iConv & 0xFF_00_00) >> 16),
                (byte) ((iConv & 0x00_FF_00) >> 8),
                (byte) ((iConv & 0x00_00_FF) >> 0),
        }, 3);
    }

    public static MidiMessage trackName(String name) throws InvalidMidiDataException {
        // assume UTF_8, I'm not sure MIDI ever expected anything but ASCII...
        byte[] text = name.getBytes(StandardCharsets.UTF_8);
        return new MetaMessage(0x03, text, text.length);
    }

    private static int getKey(NtOrPitch pitch) {
        // move pitch into C = 0, shifting lower values up
        int offset = (pitch.getPitchKey().ordinal() - NtOrPitchKey.C.ordinal()) % NtOrPitchKey.values().length;
        // add octave
        int midiNote = offset + pitch.getOctave() * (NtOrPitchKey.values().length);
        checkArgument(0 <= midiNote && midiNote <= 127, "Pitch %s is out of range", pitch);
        return midiNote;
    }

    public static MidiMessage noteOn(int channel, NtOrNote note) throws InvalidMidiDataException {
        int key = getKey(note.getPitch());
        return new ShortMessage(0x90, channel, key, (note.getVolume() * 127) / 100);
    }

    public static MidiMessage noteOff(int channel, NtOrNote note) throws InvalidMidiDataException {
        int key = getKey(note.getPitch());
        return new ShortMessage(0x90, channel, key, 0);
    }

    public static MidiMessage programChange(int channel, int instrument) throws InvalidMidiDataException {
        return new ShortMessage(0xC0, channel, instrument, 0);
    }

}
