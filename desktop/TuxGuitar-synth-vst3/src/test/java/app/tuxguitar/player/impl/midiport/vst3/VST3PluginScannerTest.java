package app.tuxguitar.player.impl.midiport.vst3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class VST3PluginScannerTest {

	private static final String MODULE_PATH = "C:\\Program Files\\Common Files\\VST3\\Amp.vst3";

	@Test
	public void parseScanOutput() {
		String output = "some plugin logging\r\n"
				+ "CLASS\t0123456789ABCDEF0123456789ABCDEF\tAmp Simulator\tAcme\tFx|Distortion\r\n"
				+ "[tuxguitar-synth-vst3] a warning\n"
				+ "CLASS\tFEDCBA9876543210FEDCBA9876543210\tGuitar\tAcme\tInstrument|Sampler\n";

		List<VST3PluginInfo> plugins = VST3PluginScanner.parseScanOutput(MODULE_PATH, output);

		assertEquals(2, plugins.size());

		VST3PluginInfo effect = plugins.get(0);
		assertEquals(MODULE_PATH, effect.getPath());
		assertEquals("0123456789ABCDEF0123456789ABCDEF", effect.getCid());
		assertEquals("Amp Simulator", effect.getName());
		assertEquals("Acme", effect.getVendor());
		assertEquals("Fx|Distortion", effect.getSubCategories());
		assertEquals("Amp Simulator (Acme)", effect.getLabel());
		assertFalse(effect.isInstrument());

		assertTrue(plugins.get(1).isInstrument());
	}

	@Test
	public void parseScanOutputWithEmptyFields() {
		List<VST3PluginInfo> plugins = VST3PluginScanner.parseScanOutput(MODULE_PATH, "CLASS\tABCD\tGain\t\t\n");

		assertEquals(1, plugins.size());
		assertEquals("Gain", plugins.get(0).getLabel());
		assertFalse(plugins.get(0).isInstrument());
	}

	@Test
	public void parseScanOutputIgnoresInvalidLines() {
		String output = "CLASS\t\tNo cid\tAcme\tFx\nCLASS\tABCD\ttoo few fields\n\n";

		assertTrue(VST3PluginScanner.parseScanOutput(MODULE_PATH, output).isEmpty());
	}

	@Test
	public void findModules(@TempDir Path folder) throws IOException {
		Path bundle = Files.createDirectories(folder.resolve("Vendor/Amp.vst3/Contents/x86_64-win"));
		Files.createFile(bundle.resolve("Amp.vst3"));
		Files.createFile(folder.resolve("Single.VST3"));
		Files.createFile(folder.resolve("readme.txt"));

		List<String> names = new ArrayList<String>();
		for(File module : VST3PluginScanner.findModules(Arrays.asList(folder.toFile(), folder.resolve("missing").toFile()))) {
			names.add(module.getName());
		}
		names.sort(null);

		// the module inside the bundle folder is not listed a second time
		assertEquals(Arrays.asList("Amp.vst3", "Single.VST3"), names);
	}
}
