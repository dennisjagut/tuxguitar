package app.tuxguitar.player.impl.midiport.vst3.remote.command;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import app.tuxguitar.midi.synth.remote.TGConnection;

public class VST3ProcessCommand extends VST3AbstractCommand<Boolean> {

	public static final Integer COMMAND_ID = 4;

	private List<VST3Event> events;
	private float[][] inputs;
	private float[][] outputs;

	public VST3ProcessCommand(TGConnection connection, List<VST3Event> events, float[][] inputs, float[][] outputs) {
		super(connection);

		this.events = events;
		this.inputs = inputs;
		this.outputs = outputs;
	}

	public Boolean process() throws IOException {
		int samples = 0;
		for(float[] channel : this.inputs) {
			samples += channel.length;
		}

		// the whole request is sent in a single write, this command runs once per audio block
		ByteBuffer request = ByteBuffer.allocate(8 + (this.events.size() * 12) + (samples * 4)).order(ByteOrder.LITTLE_ENDIAN);
		request.putInt(COMMAND_ID);
		request.putInt(this.events.size());
		for(VST3Event event : this.events) {
			request.putInt(event.getType());
			request.putInt(event.getData1());
			request.putInt(event.getData2());
		}
		for(float[] channel : this.inputs) {
			for(float value : channel) {
				request.putFloat(value);
			}
		}
		this.writeBytes(request.array());

		this.readFloatArrays(this.outputs);

		return this.readBoolean();
	}
}
