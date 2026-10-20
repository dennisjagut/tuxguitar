package app.tuxguitar.player.impl.midiport.vst3;

import app.tuxguitar.midi.synth.TGAudioProcessor;
import app.tuxguitar.midi.synth.ui.TGAudioProcessorUI;
import app.tuxguitar.midi.synth.ui.TGAudioProcessorUICallback;
import app.tuxguitar.midi.synth.ui.TGAudioProcessorUIFactory;
import app.tuxguitar.player.impl.midiport.vst3.ui.VST3AudioProcessorUI;
import app.tuxguitar.util.TGContext;

public class VST3AudioProcessorUIFactory implements TGAudioProcessorUIFactory {

	private TGContext context;
	private VST3PluginScanner scanner;
	private String type;
	private boolean instrument;

	public VST3AudioProcessorUIFactory(TGContext context, VST3PluginScanner scanner, String type, boolean instrument) {
		this.context = context;
		this.scanner = scanner;
		this.type = type;
		this.instrument = instrument;
	}

	public String getType() {
		return this.type;
	}

	public TGAudioProcessorUI create(TGAudioProcessor processor, TGAudioProcessorUICallback callback) {
		return new VST3AudioProcessorUI(this.context, (VST3AudioProcessorWrapper) processor, this.scanner, this.instrument, callback);
	}
}
