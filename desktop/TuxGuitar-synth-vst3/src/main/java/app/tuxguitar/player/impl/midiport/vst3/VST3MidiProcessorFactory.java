package app.tuxguitar.player.impl.midiport.vst3;

import app.tuxguitar.midi.synth.TGMidiProcessor;
import app.tuxguitar.midi.synth.TGMidiProcessorFactory;
import app.tuxguitar.util.TGContext;

public class VST3MidiProcessorFactory implements TGMidiProcessorFactory {

	private TGContext context;

	public VST3MidiProcessorFactory(TGContext context) {
		this.context = context;
	}

	public String getType() {
		return VST3Module.MIDI_TYPE;
	}

	public TGMidiProcessor createProcessor() {
		return new VST3MidiProcessor(this.context);
	}
}
