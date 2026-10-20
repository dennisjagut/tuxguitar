package app.tuxguitar.player.impl.midiport.vst3.remote;

import java.net.SocketException;
import java.util.List;

import app.tuxguitar.midi.synth.remote.TGConnection;
import app.tuxguitar.midi.synth.remote.TGRemoteException;
import app.tuxguitar.midi.synth.remote.TGRemoteHost;
import app.tuxguitar.midi.synth.remote.TGSession;
import app.tuxguitar.player.impl.midiport.vst3.VST3Settings;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3BusInfo;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3CloseUICommand;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3Event;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3FocusUICommand;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3GetBusInfoCommand;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3GetStateCommand;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3IsUIAvailableCommand;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3IsUIOpenCommand;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3OpenUICommand;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3ProcessCommand;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3SetStateCommand;
import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3State;
import app.tuxguitar.util.TGContext;

public class VST3RemoteInstance {

	private TGSession session;

	public VST3RemoteInstance(TGContext context, String path, String cid, int bufferSize, int sampleRate) throws TGRemoteException {
		this.session = TGRemoteHost.getInstance(context).createSession(new VST3ClientStarter(context, path, cid, bufferSize, sampleRate));
		try {
			// heavy plugins may need more time than the default timeout to save or restore their state
			this.session.getConnection().getSocket().setSoTimeout(new VST3Settings(context).getSocketTimeout());
		} catch (SocketException e) {
			throw new TGRemoteException(e);
		}
	}

	public boolean isClosed(){
		return this.session.isClosed();
	}

	public TGConnection getConnection() {
		return this.session.getConnection();
	}

	public void close(){
		if(!this.isClosed() ){
			this.session.close();
		}
	}

	public VST3State getState() {
		if(!this.isClosed() ){
			return new VST3GetStateCommand(this.getConnection()).safeProcess();
		}
		return null;
	}

	public boolean setState(VST3State state) {
		if(!this.isClosed() ){
			return new VST3SetStateCommand(this.getConnection(), state).safeProcess(false);
		}
		return false;
	}

	public VST3BusInfo getBusInfo() {
		if(!this.isClosed() ){
			return new VST3GetBusInfoCommand(this.getConnection()).safeProcess();
		}
		return null;
	}

	public boolean process(List<VST3Event> events, float[][] inputs, float[][] outputs) {
		if(!this.isClosed() ){
			return new VST3ProcessCommand(this.getConnection(), events, inputs, outputs).safeProcess(false);
		}
		return false;
	}

	public boolean openUI(){
		if(!this.isClosed() ){
			return new VST3OpenUICommand(this.getConnection()).safeProcess(false);
		}
		return false;
	}

	public boolean closeUI(){
		if(!this.isClosed() ){
			return new VST3CloseUICommand(this.getConnection()).safeProcess(false);
		}
		return false;
	}

	public boolean focusUI(){
		if(!this.isClosed() ){
			return new VST3FocusUICommand(this.getConnection()).safeProcess(false);
		}
		return false;
	}

	public boolean isUIOpen(){
		if(!this.isClosed() ){
			return new VST3IsUIOpenCommand(this.getConnection()).safeProcess(false);
		}
		return false;
	}

	public boolean isUIAvailable(){
		if(!this.isClosed() ){
			return new VST3IsUIAvailableCommand(this.getConnection()).safeProcess(false);
		}
		return false;
	}
}
