package app.tuxguitar.player.impl.midiport.vst3.remote.command;

import java.io.IOException;

import app.tuxguitar.midi.synth.remote.TGConnection;

public class VST3GetBusInfoCommand extends VST3AbstractCommand<VST3BusInfo> {

	public static final Integer COMMAND_ID = 3;

	public VST3GetBusInfoCommand(TGConnection connection) {
		super(connection);
	}

	public VST3BusInfo process() throws IOException {
		this.writeInteger(COMMAND_ID);

		int inputChannels = this.readInteger();
		int outputChannels = this.readInteger();

		return new VST3BusInfo(inputChannels, outputChannels);
	}
}
