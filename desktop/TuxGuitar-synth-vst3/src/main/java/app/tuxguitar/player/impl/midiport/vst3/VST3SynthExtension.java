package app.tuxguitar.player.impl.midiport.vst3;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.midi.synth.TGAudioProcessorFactory;
import app.tuxguitar.midi.synth.TGMidiProcessorFactory;
import app.tuxguitar.midi.synth.TGSynthExtension;
import app.tuxguitar.midi.synth.TGSynthExtensionPlugin;
import app.tuxguitar.midi.synth.ui.TGAudioProcessorUIFactory;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGPluginException;

public class VST3SynthExtension extends TGSynthExtensionPlugin {

	public VST3SynthExtension() {
		super();
	}

	public String getModuleId() {
		return VST3Module.MODULE_ID;
	}

	@Override
	public List<TGSynthExtension<?>> createExtensions(TGContext context) throws TGPluginException {
		List<TGSynthExtension<?>> extensions = new ArrayList<TGSynthExtension<?>>();

		// plugins are scanned lazily, the first time the user opens the plugin chooser
		VST3PluginScanner scanner = new VST3PluginScanner(context);

		extensions.add(new TGSynthExtension<TGMidiProcessorFactory>(TGMidiProcessorFactory.class, new VST3MidiProcessorFactory(context)));
		extensions.add(new TGSynthExtension<TGAudioProcessorFactory>(TGAudioProcessorFactory.class, new VST3AudioProcessorFactory(context)));
		extensions.add(new TGSynthExtension<TGAudioProcessorUIFactory>(TGAudioProcessorUIFactory.class, new VST3AudioProcessorUIFactory(context, scanner, VST3Module.MIDI_TYPE, true)));
		extensions.add(new TGSynthExtension<TGAudioProcessorUIFactory>(TGAudioProcessorUIFactory.class, new VST3AudioProcessorUIFactory(context, scanner, VST3Module.AUDIO_TYPE, false)));

		return extensions;
	}
}
