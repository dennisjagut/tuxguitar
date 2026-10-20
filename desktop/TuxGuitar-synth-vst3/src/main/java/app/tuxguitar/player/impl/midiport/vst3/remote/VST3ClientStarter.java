package app.tuxguitar.player.impl.midiport.vst3.remote;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import app.tuxguitar.midi.synth.remote.TGClientStarter;
import app.tuxguitar.player.impl.midiport.vst3.VST3Settings;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGExpressionResolver;

public class VST3ClientStarter implements TGClientStarter {

	private static final String VARIABLE_VST3_SESSION_ID = "vst3.sessionId";
	private static final String VARIABLE_VST3_SERVER_PORT = "vst3.serverPort";
	private static final String VARIABLE_VST3_BUFFER_SIZE = "vst3.bufferSize";
	private static final String VARIABLE_VST3_SAMPLE_RATE = "vst3.sampleRate";
	private static final String VARIABLE_VST3_PATH = "vst3.path";
	private static final String VARIABLE_VST3_CID = "vst3.cid";

	private TGContext context;
	private VST3Settings settings;
	private String path;
	private String cid;
	private Integer bufferSize;
	private Integer sampleRate;

	public VST3ClientStarter(TGContext context, String path, String cid, Integer bufferSize, Integer sampleRate) {
		this.context = context;
		this.path = path;
		this.cid = cid;
		this.bufferSize = bufferSize;
		this.sampleRate = sampleRate;
		this.settings = new VST3Settings(this.context);
	}

	@Override
	public String[] createClientCommand(Integer sessionId, Integer serverPort) {
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put(VARIABLE_VST3_SESSION_ID, sessionId);
		variables.put(VARIABLE_VST3_SERVER_PORT, serverPort);
		variables.put(VARIABLE_VST3_BUFFER_SIZE, this.bufferSize);
		variables.put(VARIABLE_VST3_SAMPLE_RATE, this.sampleRate);
		variables.put(VARIABLE_VST3_PATH, this.path);
		variables.put(VARIABLE_VST3_CID, this.cid);

		return resolveCommand(this.context, this.settings.getClientCommand(), variables);
	}

	public String getWorkingDir() {
		return resolveWorkingDir(this.context, this.settings.getWorkingDir());
	}

	public static String[] resolveCommand(TGContext context, String command, Map<String, Object> variables) {
		if( command != null ) {
			TGExpressionResolver expressionResolver = TGExpressionResolver.getInstance(context);

			String[] cmdarray = command.split(",");
			for(int i = 0 ; i < cmdarray.length; i ++) {
				cmdarray[i] = expressionResolver.resolve(cmdarray[i], new TGExpressionResolver.MapPropertyResolver(variables));
			}
			// tuxguitar.home.path is relative on Windows, the executable must not be resolved against the working dir
			if( cmdarray.length > 0 ) {
				cmdarray[0] = new File(cmdarray[0]).getAbsolutePath();
			}
			return cmdarray;
		}
		return null;
	}

	public static String resolveWorkingDir(TGContext context, String workingDir) {
		if( workingDir != null ) {
			return new File(TGExpressionResolver.getInstance(context).resolve(workingDir)).getAbsolutePath();
		}
		return null;
	}

	public static Map<String, Object> createScanVariables(String path) {
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put(VARIABLE_VST3_PATH, path);
		return variables;
	}
}
