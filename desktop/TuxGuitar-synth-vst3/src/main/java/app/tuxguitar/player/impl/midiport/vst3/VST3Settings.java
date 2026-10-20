package app.tuxguitar.player.impl.midiport.vst3;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;

public class VST3Settings {

	private static final String VST3_CLIENT_COMMAND = "vst3.client.command";
	private static final String VST3_CLIENT_WORKING_DIR = "vst3.client.working.dir";
	private static final String VST3_SCAN_COMMAND = "vst3.scan.command";
	private static final String VST3_SCAN_TIMEOUT = "vst3.scan.timeout";
	private static final String VST3_SOCKET_TIMEOUT = "vst3.socket.timeout";
	private static final String VST3_PLUGIN_PATHS = "vst3.plugin.paths";

	private static final String DEFAULT_CLIENT_COMMAND = "${tuxguitar.home.path}/vst3-client/tuxguitar-synth-vst3.exe,${vst3.sessionId},${vst3.serverPort},${vst3.bufferSize},${vst3.sampleRate},${vst3.path},${vst3.cid}";
	private static final String DEFAULT_CLIENT_WORKING_DIR = "${tuxguitar.home.path}/vst3-client";
	private static final String DEFAULT_SCAN_COMMAND = "${tuxguitar.home.path}/vst3-client/tuxguitar-synth-vst3.exe,--scan,${vst3.path}";
	private static final int DEFAULT_SCAN_TIMEOUT = 15000;
	private static final int DEFAULT_SOCKET_TIMEOUT = 10000;

	private TGContext context;
	private TGConfigManager config;

	public VST3Settings(TGContext context){
		this.context = context;
	}

	public TGConfigManager getConfig(){
		if( this.config == null ){
			this.config = new TGConfigManager(this.context, VST3Module.MODULE_ID);
		}
		return this.config;
	}

	public String getClientCommand() {
		return this.getConfig().getStringValue(VST3_CLIENT_COMMAND, DEFAULT_CLIENT_COMMAND);
	}

	public String getWorkingDir() {
		return this.getConfig().getStringValue(VST3_CLIENT_WORKING_DIR, DEFAULT_CLIENT_WORKING_DIR);
	}

	public String getScanCommand() {
		return this.getConfig().getStringValue(VST3_SCAN_COMMAND, DEFAULT_SCAN_COMMAND);
	}

	public int getScanTimeout() {
		return this.getConfig().getIntegerValue(VST3_SCAN_TIMEOUT, DEFAULT_SCAN_TIMEOUT);
	}

	public int getSocketTimeout() {
		return this.getConfig().getIntegerValue(VST3_SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT);
	}

	public List<File> getPluginFolders() {
		List<File> folders = new ArrayList<File>();
		String commonProgramFiles = System.getenv("CommonProgramFiles");
		if( commonProgramFiles != null ) {
			folders.add(new File(commonProgramFiles, "VST3"));
		}
		String localAppData = System.getenv("LOCALAPPDATA");
		if( localAppData != null ) {
			folders.add(new File(localAppData, "Programs" + File.separator + "Common" + File.separator + "VST3"));
		}
		String extraPaths = this.getConfig().getStringValue(VST3_PLUGIN_PATHS);
		if( extraPaths != null ) {
			for(String path : extraPaths.split(";")) {
				if( path.trim().length() > 0 ) {
					folders.add(new File(path.trim()));
				}
			}
		}
		return folders;
	}
}
