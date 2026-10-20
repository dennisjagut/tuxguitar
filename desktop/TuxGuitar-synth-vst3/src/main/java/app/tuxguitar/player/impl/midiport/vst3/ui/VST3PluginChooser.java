package app.tuxguitar.player.impl.midiport.vst3.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.system.icons.TGIconManager;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.player.impl.midiport.vst3.VST3PluginInfo;
import app.tuxguitar.player.impl.midiport.vst3.VST3PluginScanner;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIMouseDoubleClickListener;
import app.tuxguitar.ui.event.UIMouseEvent;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UILegendPanel;
import app.tuxguitar.ui.widget.UIListBoxSelect;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

public class VST3PluginChooser {

	private TGContext context;
	private VST3PluginScanner scanner;

	public VST3PluginChooser(TGContext context, VST3PluginScanner scanner) {
		this.context = context;
		this.scanner = scanner;
	}

	public void choose(final UIWindow parent, final boolean instrument, final VST3PluginChooserHandler handler) {
		// scanning starts external processes, keep it out of the UI thread
		TGThreadManager.getInstance(this.context).start(new Runnable() {
			public void run() {
				final List<VST3PluginInfo> plugins = VST3PluginChooser.this.findPlugins(instrument);

				TGSynchronizer.getInstance(VST3PluginChooser.this.context).executeLater(new Runnable() {
					public void run() {
						VST3PluginChooser.this.chooseInCurrentThread(parent, plugins, handler);
					}
				});
			}
		});
	}

	public void chooseInCurrentThread(final UIWindow parent, final List<VST3PluginInfo> plugins, final VST3PluginChooserHandler handler) {
		final UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		final UITableLayout dialogLayout = new UITableLayout();

		final UIWindow dialog = uiFactory.createWindow(parent, true, false);
		dialog.setLayout(dialogLayout);
		dialog.setText(TuxGuitar.getProperty("tuxguitar-synth-vst3.chooser.dialog.title"));
		dialog.setImage(TGIconManager.getInstance(this.context).getAppIcon());

		// ----------------------------------------------------------------------
		UITableLayout pluginGroupLayout = new UITableLayout();
		UILegendPanel pluginGroup = uiFactory.createLegendPanel(dialog);
		pluginGroup.setLayout(pluginGroupLayout);
		pluginGroup.setText(TuxGuitar.getProperty(plugins.isEmpty() ? "tuxguitar-synth-vst3.chooser.empty" : "tuxguitar-synth-vst3.chooser.tip"));
		dialogLayout.set(pluginGroup, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);

		final UIListBoxSelect<VST3PluginInfo> pluginList = uiFactory.createListBoxSelect(pluginGroup);
		pluginGroupLayout.set(pluginList, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
		pluginGroupLayout.set(pluginList, UITableLayout.MINIMUM_PACKED_WIDTH, 250f);
		pluginGroupLayout.set(pluginList, UITableLayout.PACKED_HEIGHT, 200f);
		for(VST3PluginInfo plugin : plugins) {
			pluginList.addItem(new UISelectItem<VST3PluginInfo>(plugin.getLabel(), plugin));
		}
		if( plugins.size() > 0 ){
			pluginList.setSelectedValue(plugins.get(0));
		}
		pluginList.addMouseDoubleClickListener(new UIMouseDoubleClickListener() {
			public void onMouseDoubleClick(UIMouseEvent event) {
				onSelectPlugin(handler, pluginList.getSelectedValue());
				dialog.dispose();
			}
		});

		//------------------BUTTONS----------------------------------------------
		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = uiFactory.createPanel(dialog, false);
		buttons.setLayout(buttonsLayout);
		dialogLayout.set(buttons, 2, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, true);

		UIButton buttonOK = uiFactory.createButton(buttons);
		buttonOK.setText(TuxGuitar.getProperty("ok"));
		buttonOK.setDefaultButton();
		buttonOK.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				onSelectPlugin(handler, pluginList.getSelectedValue());
				dialog.dispose();
			}
		});
		buttonsLayout.set(buttonOK, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 80f, 25f, null);

		UIButton buttonCancel = uiFactory.createButton(buttons);
		buttonCancel.setText(TuxGuitar.getProperty("cancel"));
		buttonCancel.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				dialog.dispose();
			}
		});
		buttonsLayout.set(buttonCancel, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 80f, 25f, null);
		buttonsLayout.set(buttonCancel, UITableLayout.MARGIN_RIGHT, 0f);
		// ----------------------------------------------------------------------

		TGDialogUtil.openDialog(dialog, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
	}

	public List<VST3PluginInfo> findPlugins(boolean instrument) {
		List<VST3PluginInfo> plugins = new ArrayList<VST3PluginInfo>();
		for(VST3PluginInfo plugin : this.scanner.getPlugins()) {
			if( plugin.isInstrument() == instrument ) {
				plugins.add(plugin);
			}
		}
		plugins.sort(new Comparator<VST3PluginInfo>() {
			public int compare(VST3PluginInfo p1, VST3PluginInfo p2) {
				return p1.getLabel().compareToIgnoreCase(p2.getLabel());
			}
		});
		return plugins;
	}

	public void onSelectPlugin(final VST3PluginChooserHandler handler, final VST3PluginInfo plugin) {
		TGThreadManager.getInstance(this.context).start(new Runnable() {
			public void run() {
				handler.onSelectPlugin(plugin);
			}
		});
	}

	public static interface VST3PluginChooserHandler {
		void onSelectPlugin(VST3PluginInfo plugin);
	}
}
