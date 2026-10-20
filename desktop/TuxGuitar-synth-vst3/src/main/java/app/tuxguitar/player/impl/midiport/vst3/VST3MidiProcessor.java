package app.tuxguitar.player.impl.midiport.vst3;

import app.tuxguitar.midi.synth.TGMidiProcessor;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3Event;
import app.tuxguitar.util.TGContext;

public class VST3MidiProcessor extends VST3AudioProcessorWrapper implements TGMidiProcessor {

	public VST3MidiProcessor(TGContext context) {
		super(context);
	}

	@Override
	public void sendNoteOn(int key, int velocity, int voice, boolean bendMode) {
		this.queueEvent(new VST3Event(VST3Event.NOTE_ON, key, velocity));
	}

	@Override
	public void sendNoteOff(int key, int velocity, int voice, boolean bendMode) {
		this.queueEvent(new VST3Event(VST3Event.NOTE_OFF, key, velocity));
	}

	@Override
	public void sendPitchBend(int value, int voice, boolean bendMode) {
		// TuxGuitar bends are 7 bit values (64 = center), VST3 hosts map them to the 14 bit pitch bend controller
		this.queueEvent(new VST3Event(VST3Event.PITCH_BEND, value * 128, 0));
	}

	@Override
	public void sendControlChange(int controller, int value) {
		this.queueEvent(new VST3Event(VST3Event.CONTROL_CHANGE, controller, value));
	}
}
