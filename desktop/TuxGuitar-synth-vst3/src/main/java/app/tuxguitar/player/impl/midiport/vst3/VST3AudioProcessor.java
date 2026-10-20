package app.tuxguitar.player.impl.midiport.vst3;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.midi.synth.TGAudioBuffer;
import app.tuxguitar.midi.synth.remote.TGRemoteException;
import app.tuxguitar.player.impl.midiport.vst3.remote.VST3RemoteInstance;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3BusInfo;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3Event;
import app.tuxguitar.util.TGContext;

public class VST3AudioProcessor {

	public static final int BUFFER_SIZE = ( TGAudioBuffer.BUFFER_SIZE / 2 ) ;
	public static final int SAMPLE_RATE = Math.round( TGAudioBuffer.SAMPLE_RATE );

	private Object lock = new Object();

	private TGContext context;
	private String path;
	private String cid;
	private String name;
	private VST3RemoteInstance instance;
	private VST3AudioProcessorUpdateCallback updateCallback;
	private List<VST3Event> events;
	private float[][] inputs;
	private float[][] outputs;

	public VST3AudioProcessor(TGContext context, String path, String cid, String name) throws TGRemoteException {
		this.context = context;
		this.path = path;
		this.cid = cid;
		this.name = name;
	}

	public void open() throws TGRemoteException {
		synchronized (this.lock) {
			if(!this.isOpen()){
				this.instance = new VST3RemoteInstance(this.context, this.path, this.cid, BUFFER_SIZE, SAMPLE_RATE);

				VST3BusInfo busInfo = this.instance.getBusInfo();
				if( busInfo == null ) {
					this.instance.close();
					throw new TGRemoteException("Could not read VST3 bus info: " + this.path);
				}
				this.events = new ArrayList<VST3Event>();
				this.inputs = new float[busInfo.getInputChannels()][BUFFER_SIZE];
				this.outputs = new float[busInfo.getOutputChannels()][BUFFER_SIZE];
			}
		}
	}

	public void close(){
		synchronized (this.lock) {
			if( this.isOpen()){
				this.instance.closeUI();
				this.instance.close();
				this.instance = null;
			}
		}
	}

	public boolean isOpen(){
		return (this.instance != null && !this.instance.isClosed());
	}

	public void queueEvent(VST3Event event){
		synchronized (this.lock) {
			if( this.isOpen()){
				this.events.add(event);
			}
		}
	}

	public void fillBuffer(TGAudioBuffer buffer) {
		synchronized (this.lock) {
			if( this.isOpen() ) {
				if( this.inputs.length > 0 ) {
					buffer.read(this.inputs);
				}

				boolean updated = this.instance.process(this.events, this.inputs, this.outputs);
				this.events.clear();

				if( this.outputs.length > 0 ) {
					clamp(this.outputs);
					buffer.write(this.outputs);
				}

				if( this.updateCallback != null && updated ) {
					this.updateCallback.onUpdate();
				}
			}
		}
	}

	public void setUpdateCallback(VST3AudioProcessorUpdateCallback updateCallback) {
		synchronized (this.lock) {
			this.updateCallback = updateCallback;
		}
	}

	public String getPath() {
		return this.path;
	}

	public String getCid() {
		return this.cid;
	}

	public String getName() {
		return this.name;
	}

	public VST3RemoteInstance getInstance() {
		return this.instance;
	}

	/**
	 * TGAudioBuffer converts samples to 16 bit without clipping,
	 * so samples out of range (common with amp simulators) must be limited here.
	 */
	public static void clamp(float[][] samples) {
		for(float[] channel : samples) {
			for(int i = 0; i < channel.length; i ++) {
				channel[i] = Math.max(-1f, Math.min(1f, channel[i]));
			}
		}
	}

	public interface VST3AudioProcessorUpdateCallback {

		void onUpdate();
	}
}
