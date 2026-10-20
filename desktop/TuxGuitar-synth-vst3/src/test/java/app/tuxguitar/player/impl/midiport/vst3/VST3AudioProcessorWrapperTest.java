package app.tuxguitar.player.impl.midiport.vst3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.player.impl.midiport.vst3.remote.command.VST3State;

public class VST3AudioProcessorWrapperTest {

	@Test
	public void encodeAndDecodeState() {
		byte[] componentState = new byte[] {0, 1, 2, (byte) 0xFF, (byte) 0x80, 42};
		byte[] controllerState = new byte[] {7, 8, 9};

		Map<String, String> parameters = new HashMap<String, String>();
		VST3AudioProcessorWrapper.encodeState(new VST3State(componentState, controllerState), parameters);

		VST3State state = VST3AudioProcessorWrapper.decodeState(parameters);
		assertArrayEquals(componentState, state.getComponentState());
		assertArrayEquals(controllerState, state.getControllerState());
	}

	@Test
	public void encodeAndDecodeStateWithoutControllerState() {
		Map<String, String> parameters = new HashMap<String, String>();
		VST3AudioProcessorWrapper.encodeState(new VST3State(new byte[] {1, 2, 3}, new byte[0]), parameters);

		assertEquals("", parameters.get(VST3AudioProcessorWrapper.PARAM_VST3_CONTROLLER_STATE));

		VST3State state = VST3AudioProcessorWrapper.decodeState(parameters);
		assertArrayEquals(new byte[] {1, 2, 3}, state.getComponentState());
		assertArrayEquals(new byte[0], state.getControllerState());
	}

	@Test
	public void decodeMissingState() {
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put(VST3AudioProcessorWrapper.PARAM_VST3_PATH, "Amp.vst3");

		assertNull(VST3AudioProcessorWrapper.decodeState(parameters));

		parameters.put(VST3AudioProcessorWrapper.PARAM_VST3_COMPONENT_STATE, "");
		assertNull(VST3AudioProcessorWrapper.decodeState(parameters));
	}

	@Test
	public void clampLimitsSamples() {
		float[][] samples = new float[][] {{-3f, -1f, 0f, 0.5f, 1f, 2.5f}};

		VST3AudioProcessor.clamp(samples);

		assertArrayEquals(new float[] {-1f, -1f, 0f, 0.5f, 1f, 1f}, samples[0]);
	}
}
