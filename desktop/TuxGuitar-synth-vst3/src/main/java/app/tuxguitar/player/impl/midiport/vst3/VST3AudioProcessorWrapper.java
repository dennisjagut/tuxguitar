package app.tuxguitar.player.impl.midiport.vst3;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import app.tuxguitar.midi.synth.TGAudioBuffer;
import app.tuxguitar.midi.synth.TGAudioProcessor;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3Event;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3State;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.base64.Base64Decoder;
import app.tuxguitar.util.base64.Base64Encoder;

public class VST3AudioProcessorWrapper implements TGAudioProcessor {

	private static final Charset CHARSET = Charset.forName("UTF-8");

	public static final String PARAM_VST3_PATH = "vst3.path";
	public static final String PARAM_VST3_CID = "vst3.cid";
	public static final String PARAM_VST3_NAME = "vst3.name";
	public static final String PARAM_VST3_COMPONENT_STATE = "vst3.state.component";
	public static final String PARAM_VST3_CONTROLLER_STATE = "vst3.state.controller";

	private TGContext context;
	private VST3AudioProcessor target;
	private Map<String, String> appliedParameters;

	public VST3AudioProcessorWrapper(TGContext context) {
		this.context = context;
	}

	public VST3AudioProcessor getTarget() {
		return this.target;
	}

	@SuppressWarnings("deprecation")
	public void finalize() {
		this.close();
	}

	public void open(String path, String cid, String name) {
		try {
			if( this.target == null || !this.target.isOpen() ) {
				this.appliedParameters = null;
				this.target = new VST3AudioProcessor(this.context, path, cid, name);
				this.target.open();
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public void close() {
		if( this.target != null ) {
			this.target.close();
		}
	}

	public boolean isOpen() {
		if( this.target != null ) {
			return this.target.isOpen();
		}
		return false;
	}

	public void fillBuffer(TGAudioBuffer buffer) {
		if( this.target != null ) {
			this.target.fillBuffer(buffer);
		}
	}

	public void queueEvent(VST3Event event) {
		if( this.target != null ) {
			this.target.queueEvent(event);
		}
	}

	public void restoreParameters(Map<String, String> parameters) {
		if(!this.isOpen() && parameters.containsKey(PARAM_VST3_PATH) && parameters.containsKey(PARAM_VST3_CID)) {
			this.open(parameters.get(PARAM_VST3_PATH), parameters.get(PARAM_VST3_CID), parameters.get(PARAM_VST3_NAME));
		}
		if( this.isOpen() ) {
			if( this.appliedParameters == null || !this.appliedParameters.equals(parameters)) {
				this.appliedParameters = new HashMap<String, String>(parameters);

				VST3State state = decodeState(parameters);
				if( state != null ) {
					this.target.getInstance().setState(state);
				}
			}
		}
	}

	public void storeParameters(Map<String, String> parameters) {
		if( this.isOpen() ) {
			parameters.put(PARAM_VST3_PATH, this.target.getPath());
			parameters.put(PARAM_VST3_CID, this.target.getCid());
			if( this.target.getName() != null ) {
				parameters.put(PARAM_VST3_NAME, this.target.getName());
			}

			VST3State state = this.target.getInstance().getState();
			if( state != null ) {
				encodeState(state, parameters);
			}

			this.appliedParameters = new HashMap<String, String>(parameters);
		}
	}

	public boolean isBusy() {
		return false;
	}

	public static void encodeState(VST3State state, Map<String, String> parameters) {
		parameters.put(PARAM_VST3_COMPONENT_STATE, encode(state.getComponentState()));
		parameters.put(PARAM_VST3_CONTROLLER_STATE, encode(state.getControllerState()));
	}

	public static VST3State decodeState(Map<String, String> parameters) {
		String componentState = parameters.get(PARAM_VST3_COMPONENT_STATE);
		String controllerState = parameters.get(PARAM_VST3_CONTROLLER_STATE);
		if( componentState != null && componentState.length() > 0 ) {
			return new VST3State(decode(componentState), decode(controllerState));
		}
		return null;
	}

	private static String encode(byte[] value) {
		if( value == null || value.length == 0 ) {
			return "";
		}
		return new String(Base64Encoder.encode(value), CHARSET);
	}

	private static byte[] decode(String value) {
		if( value == null || value.length() == 0 ) {
			return new byte[0];
		}
		return Base64Decoder.decode(value.getBytes(CHARSET));
	}
}
