package app.tuxguitar.player.impl.midiport.vst3.remote.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.midi.synth.remote.TGConnection;

/**
 * Checks the wire format against what the native client (VST3Client.cpp) reads and writes.
 */
public class VST3CommandTest {

	private Socket hostSocket;
	private Socket clientSocket;
	private TGConnection connection;
	private DataInputStream clientInput;
	private OutputStream clientOutput;

	@BeforeEach
	public void connect() throws IOException {
		try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			this.clientSocket = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
			this.hostSocket = serverSocket.accept();
		}
		this.connection = new TGConnection(this.hostSocket);
		this.clientInput = new DataInputStream(this.clientSocket.getInputStream());
		this.clientOutput = this.clientSocket.getOutputStream();
	}

	@AfterEach
	public void disconnect() throws IOException {
		this.hostSocket.close();
		this.clientSocket.close();
	}

	@Test
	public void process() throws Exception {
		float[][] inputs = new float[][] {{0.25f, -0.5f}, {1f, 0f}};
		float[][] outputs = new float[2][2];

		ByteBuffer response = littleEndian(17);
		response.putFloat(0.1f).putFloat(0.2f).putFloat(0.3f).putFloat(0.4f).put((byte) 1);
		this.respondInChunks(response.array());

		VST3ProcessCommand command = new VST3ProcessCommand(this.connection, Arrays.asList(new VST3Event(VST3Event.NOTE_ON, 64, 100)), inputs, outputs);
		assertTrue(command.process());

		ByteBuffer request = this.readRequest(8 + 12 + 16);
		assertEquals(VST3ProcessCommand.COMMAND_ID.intValue(), request.getInt());
		assertEquals(1, request.getInt());
		assertEquals(VST3Event.NOTE_ON, request.getInt());
		assertEquals(64, request.getInt());
		assertEquals(100, request.getInt());
		assertEquals(0.25f, request.getFloat());
		assertEquals(-0.5f, request.getFloat());
		assertEquals(1f, request.getFloat());
		assertEquals(0f, request.getFloat());

		assertArrayEquals(new float[] {0.1f, 0.2f}, outputs[0]);
		assertArrayEquals(new float[] {0.3f, 0.4f}, outputs[1]);
	}

	@Test
	public void getState() throws Exception {
		ByteBuffer response = littleEndian(4 + 3 + 4);
		response.putInt(3).put(new byte[] {1, 2, 3}).putInt(0);
		this.respondInChunks(response.array());

		VST3State state = new VST3GetStateCommand(this.connection).process();

		assertEquals(VST3GetStateCommand.COMMAND_ID.intValue(), this.readRequest(4).getInt());
		assertArrayEquals(new byte[] {1, 2, 3}, state.getComponentState());
		assertArrayEquals(new byte[0], state.getControllerState());
	}

	@Test
	public void setState() throws Exception {
		this.respondInChunks(new byte[] {1});

		boolean result = new VST3SetStateCommand(this.connection, new VST3State(new byte[] {5, 6}, new byte[] {7})).process();
		assertTrue(result);

		ByteBuffer request = this.readRequest(4 + 4 + 2 + 4 + 1);
		assertEquals(VST3SetStateCommand.COMMAND_ID.intValue(), request.getInt());
		assertEquals(2, request.getInt());
		assertEquals(5, request.get());
		assertEquals(6, request.get());
		assertEquals(1, request.getInt());
		assertEquals(7, request.get());
	}

	@Test
	public void getBusInfo() throws Exception {
		ByteBuffer response = littleEndian(8);
		response.putInt(1).putInt(2);
		this.respondInChunks(response.array());

		VST3BusInfo busInfo = new VST3GetBusInfoCommand(this.connection).process();

		assertEquals(VST3GetBusInfoCommand.COMMAND_ID.intValue(), this.readRequest(4).getInt());
		assertEquals(1, busInfo.getInputChannels());
		assertEquals(2, busInfo.getOutputChannels());
	}

	private static ByteBuffer littleEndian(int size) {
		return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
	}

	private ByteBuffer readRequest(int size) throws IOException {
		byte[] bytes = new byte[size];
		this.clientInput.readFully(bytes);
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
	}

	// the response arrives in small pieces, the commands must not rely on a single socket read
	private void respondInChunks(final byte[] response) {
		Thread responder = new Thread(new Runnable() {
			public void run() {
				try {
					for(int i = 0; i < response.length; i += 3) {
						VST3CommandTest.this.clientOutput.write(response, i, Math.min(3, response.length - i));
						VST3CommandTest.this.clientOutput.flush();
						Thread.sleep(5);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		responder.start();
	}
}
