package app.tuxguitar.player.impl.midiport.vst3.remote.command;

public class VST3State {

	private byte[] componentState;
	private byte[] controllerState;

	public VST3State(byte[] componentState, byte[] controllerState) {
		this.componentState = componentState;
		this.controllerState = controllerState;
	}

	public byte[] getComponentState() {
		return this.componentState;
	}

	public byte[] getControllerState() {
		return this.controllerState;
	}
}
