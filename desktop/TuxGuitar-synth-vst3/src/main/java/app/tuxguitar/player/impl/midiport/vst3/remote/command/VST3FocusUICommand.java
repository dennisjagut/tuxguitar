package app.tuxguitar.player.impl.midiport.vst3.remote.command;

import java.io.IOException;

import app.tuxguitar.midi.synth.remote.TGConnection;

public class VST3FocusUICommand extends VST3AbstractCommand<Boolean> {

	public static final Integer COMMAND_ID = 7;

	public VST3FocusUICommand(TGConnection connection) {
		super(connection);
	}

	public Boolean process() throws IOException {
		this.writeInteger(COMMAND_ID);

		return this.readBoolean();
	}
}
