package app.tuxguitar.player.impl.midiport.vst3.remote.command;

import java.io.IOException;

import app.tuxguitar.midi.synth.remote.TGConnection;

public class VST3GetStateCommand extends VST3AbstractCommand<VST3State> {

	public static final Integer COMMAND_ID = 1;

	public VST3GetStateCommand(TGConnection connection) {
		super(connection);
	}

	public VST3State process() throws IOException {
		this.writeInteger(COMMAND_ID);

		byte[] componentState = this.readByteArray();
		byte[] controllerState = this.readByteArray();

		return new VST3State(componentState, controllerState);
	}
}
