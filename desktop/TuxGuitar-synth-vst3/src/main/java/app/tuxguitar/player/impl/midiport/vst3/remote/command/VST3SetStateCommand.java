package app.tuxguitar.player.impl.midiport.vst3.remote.command;

import java.io.IOException;

import app.tuxguitar.midi.synth.remote.TGConnection;

public class VST3SetStateCommand extends VST3AbstractCommand<Boolean> {

	public static final Integer COMMAND_ID = 2;

	private VST3State state;

	public VST3SetStateCommand(TGConnection connection, VST3State state) {
		super(connection);

		this.state = state;
	}

	public Boolean process() throws IOException {
		this.writeInteger(COMMAND_ID);
		this.writeByteArray(this.state.getComponentState());
		this.writeByteArray(this.state.getControllerState());

		return this.readBoolean();
	}
}
