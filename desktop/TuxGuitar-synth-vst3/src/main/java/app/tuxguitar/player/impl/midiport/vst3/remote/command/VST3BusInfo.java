package app.tuxguitar.player.impl.midiport.vst3.remote.command;

public class VST3BusInfo {

	private int inputChannels;
	private int outputChannels;

	public VST3BusInfo(int inputChannels, int outputChannels) {
		this.inputChannels = inputChannels;
		this.outputChannels = outputChannels;
	}

	public int getInputChannels() {
		return this.inputChannels;
	}

	public int getOutputChannels() {
		return this.outputChannels;
	}
}
