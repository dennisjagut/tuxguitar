package app.tuxguitar.player.impl.midiport.vst3.remote.command;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import app.tuxguitar.midi.synth.remote.TGAbstractCommand;
import app.tuxguitar.midi.synth.remote.TGConnection;

public abstract class VST3AbstractCommand<T> extends TGAbstractCommand<T> {

	public VST3AbstractCommand(TGConnection connection) {
		super(connection);
	}

	@Override
	public void readBytes(byte[] buffer) throws IOException {
		// a single read may return less bytes than requested on a socket stream
		InputStream inputStream = this.getConnection().getInputStream();
		int offset = 0;
		while( offset < buffer.length ) {
			int count = inputStream.read(buffer, offset, buffer.length - offset);
			if( count < 0 ) {
				throw new EOFException();
			}
			offset += count;
		}
	}

	public void writeByteArray(byte[] value) throws IOException {
		this.writeInteger(value.length);
		if( value.length > 0 ) {
			this.writeBytes(value);
		}
	}

	public byte[] readByteArray() throws IOException {
		return this.readBytes(this.readInteger());
	}

	public void readFloatArrays(float[][] values) throws IOException {
		int length = 0;
		for(float[] channel : values) {
			length += channel.length;
		}
		if( length > 0 ) {
			ByteBuffer buffer = ByteBuffer.wrap(this.readBytes(length * 4)).order(ByteOrder.LITTLE_ENDIAN);
			for(float[] channel : values) {
				for(int i = 0; i < channel.length; i ++) {
					channel[i] = buffer.getFloat();
				}
			}
		}
	}
}
