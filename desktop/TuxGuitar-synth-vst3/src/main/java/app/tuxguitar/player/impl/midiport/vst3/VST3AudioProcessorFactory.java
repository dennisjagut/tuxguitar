package app.tuxguitar.player.impl.midiport.vst3;

import app.tuxguitar.midi.synth.TGAudioProcessor;
import app.tuxguitar.midi.synth.TGAudioProcessorFactory;
import app.tuxguitar.util.TGContext;

public class VST3AudioProcessorFactory implements TGAudioProcessorFactory {

	private TGContext context;

	public VST3AudioProcessorFactory(TGContext context) {
		this.context = context;
	}

	public String getType() {
		return VST3Module.AUDIO_TYPE;
	}

	public TGAudioProcessor createProcessor() {
		return new VST3AudioProcessorWrapper(this.context);
	}
}
