package app.tuxguitar.player.impl.midiport.vst3.ui;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.midi.synth.ui.TGAudioProcessorUI;
import app.tuxguitar.midi.synth.ui.TGAudioProcessorUICallback;
import app.tuxguitar.player.impl.midiport.vst3.VST3AudioProcessor;
import app.tuxguitar.player.impl.midiport.vst3.VST3AudioProcessor.VST3AudioProcessorUpdateCallback;
import app.tuxguitar.player.impl.midiport.vst3.VST3AudioProcessorWrapper;
import app.tuxguitar.player.impl.midiport.vst3.VST3PluginInfo;
import app.tuxguitar.player.impl.midiport.vst3.VST3PluginScanner;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;

public class VST3AudioProcessorUI implements VST3AudioProcessorUpdateCallback, TGAudioProcessorUI {

	private TGContext context;
	private VST3PluginScanner scanner;
	private VST3AudioProcessorWrapper processor;
	private TGAudioProcessorUICallback callback;
	private boolean instrument;

	public VST3AudioProcessorUI(TGContext context, VST3AudioProcessorWrapper processor, VST3PluginScanner scanner, boolean instrument, TGAudioProcessorUICallback callback) {
		this.context = context;
		this.processor = processor;
		this.scanner = scanner;
		this.instrument = instrument;
		this.callback = callback;
	}

	public String getLabel() {
		String vst3Plugin = null;
		if( this.isTargetOpen() && this.getTarget().getName() != null ) {
			vst3Plugin = this.getTarget().getName();
		} else {
			vst3Plugin = TuxGuitar.getProperty("tuxguitar-synth-vst3.ui.label.empty");
		}
		return TuxGuitar.getProperty("tuxguitar-synth-vst3.ui.label", new String[] {vst3Plugin});
	}

	public boolean isOpen() {
		if( this.isTargetOpen() ) {
			return this.getTarget().getInstance().isUIOpen();
		}
		return false;
	}

	public void open(UIWindow parent) {
		if( this.isTargetOpen() ) {
			if( this.getTarget().getInstance().isUIAvailable() ) {
				this.getTarget().setUpdateCallback(this);
				this.getTarget().getInstance().openUI();
			}
		} else {
			this.choosePlugin(parent);
		}
	}

	public void close() {
		if( this.isTargetOpen() ) {
			this.getTarget().setUpdateCallback(null);
			this.getTarget().getInstance().closeUI();
		}
	}

	public void focus() {
		if( this.isTargetOpen() ) {
			this.getTarget().getInstance().focusUI();
		}
	}

	public void choosePlugin(final UIWindow parent) {
		VST3PluginChooser chooser = new VST3PluginChooser(this.context, this.scanner);
		chooser.choose(parent, this.instrument, new VST3PluginChooser.VST3PluginChooserHandler() {
			public void onSelectPlugin(VST3PluginInfo plugin) {
				if( plugin != null ) {
					VST3AudioProcessorUI.this.processor.open(plugin.getPath(), plugin.getCid(), plugin.getName());
					VST3AudioProcessorUI.this.open(parent);
					VST3AudioProcessorUI.this.callback.onChange(true);
				}
			}
		});
	}

	public void onUpdate() {
		TGThreadManager.getInstance(this.context).start(new Runnable() {
			public void run() {
				VST3AudioProcessorUI.this.callback.onChange(false);
			}
		});
	}

	private VST3AudioProcessor getTarget() {
		return this.processor.getTarget();
	}

	private boolean isTargetOpen() {
		return (this.getTarget() != null && this.getTarget().isOpen());
	}
}
