package app.tuxguitar.player.impl.midiport.vst3;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

import app.tuxguitar.player.impl.midiport.vst3.remote.VST3ClientStarter;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.base64.Base64Decoder;
import app.tuxguitar.util.base64.Base64Encoder;
import app.tuxguitar.util.configuration.TGConfigManager;

/**
 * Lists the VST3 plugins installed on this system.
 *
 * Every module is inspected by running the client executable in "--scan" mode, so a crashing or hanging
 * plugin never affects TuxGuitar. Scan results are cached per module path and modification time.
 */
public class VST3PluginScanner {

	private static final Charset CHARSET = Charset.forName("UTF-8");

	private static final String MODULE_EXTENSION = ".vst3";
	private static final String CACHE_MODULE = "tuxguitar-synth-vst3-cache";
	private static final String CACHE_SEPARATOR = ":";

	private static final String SCAN_LINE_CLASS = "CLASS";
	private static final String SCAN_FIELD_SEPARATOR = "\t";

	private TGContext context;
	private VST3Settings settings;
	private List<VST3PluginInfo> plugins;

	public VST3PluginScanner(TGContext context) {
		this.context = context;
		this.settings = new VST3Settings(context);
	}

	public synchronized List<VST3PluginInfo> getPlugins() {
		if( this.plugins == null ) {
			this.plugins = this.scan();
		}
		return new ArrayList<VST3PluginInfo>(this.plugins);
	}

	public synchronized void reset() {
		this.plugins = null;
	}

	public List<VST3PluginInfo> scan() {
		List<VST3PluginInfo> plugins = new ArrayList<VST3PluginInfo>();
		TGConfigManager cache = new TGConfigManager(this.context, CACHE_MODULE);
		for(File module : findModules(this.settings.getPluginFolders())) {
			String path = module.getAbsolutePath();
			String output = this.readCache(cache, module);
			if( output == null ) {
				output = this.scanModule(path);
				if( output != null ) {
					this.writeCache(cache, module, output);
				}
			}
			if( output != null ) {
				plugins.addAll(parseScanOutput(path, output));
			}
		}
		cache.save();

		return plugins;
	}

	public String scanModule(String path) {
		try {
			String[] command = VST3ClientStarter.resolveCommand(this.context, this.settings.getScanCommand(), VST3ClientStarter.createScanVariables(path));
			String workingDir = VST3ClientStarter.resolveWorkingDir(this.context, this.settings.getWorkingDir());

			ProcessBuilder processBuilder = new ProcessBuilder(command);
			processBuilder.redirectErrorStream(true);
			if( workingDir != null ) {
				processBuilder.directory(new File(workingDir));
			}

			final Process process = processBuilder.start();
			final ByteArrayOutputStream output = new ByteArrayOutputStream();
			Thread reader = new Thread(new Runnable() {
				public void run() {
					try {
						InputStream inputStream = process.getInputStream();
						byte[] buffer = new byte[4096];
						int count = 0;
						while((count = inputStream.read(buffer)) != -1) {
							synchronized (output) {
								output.write(buffer, 0, count);
							}
						}
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			});
			reader.start();

			if(!process.waitFor(this.settings.getScanTimeout(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				System.err.println("VST3 scan timed out: " + path);
				return null;
			}
			reader.join(1000);

			synchronized (output) {
				return new String(output.toByteArray(), CHARSET);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	private String readCache(TGConfigManager cache, File module) {
		String value = cache.getStringValue(module.getAbsolutePath());
		if( value != null ) {
			int separator = value.indexOf(CACHE_SEPARATOR);
			if( separator > 0 && value.substring(0, separator).equals(Long.toString(module.lastModified())) ) {
				return new String(Base64Decoder.decode(value.substring(separator + 1).getBytes(CHARSET)), CHARSET);
			}
		}
		return null;
	}

	private void writeCache(TGConfigManager cache, File module, String output) {
		String encoded = new String(Base64Encoder.encode(output.getBytes(CHARSET)), CHARSET);

		cache.setValue(module.getAbsolutePath(), Long.toString(module.lastModified()) + CACHE_SEPARATOR + encoded);
	}

	public static List<File> findModules(List<File> folders) {
		List<File> modules = new ArrayList<File>();
		Deque<File> pending = new ArrayDeque<File>(folders);
		while(!pending.isEmpty()) {
			File file = pending.pop();
			if( file.getName().toLowerCase().endsWith(MODULE_EXTENSION) ) {
				// either a bundle folder or a single file module, never look inside
				modules.add(file);
			}
			else if( file.isDirectory() ) {
				File[] children = file.listFiles();
				if( children != null ) {
					for(File child : children) {
						pending.push(child);
					}
				}
			}
		}
		return modules;
	}

	/**
	 * Parses the output of "tuxguitar-synth-vst3 --scan", one line per audio module class:
	 * CLASS[TAB]cid[TAB]name[TAB]vendor[TAB]subCategories
	 * Any other line (plugin logging, errors) is ignored.
	 */
	public static List<VST3PluginInfo> parseScanOutput(String path, String output) {
		List<VST3PluginInfo> plugins = new ArrayList<VST3PluginInfo>();
		for(String line : output.split("\r?\n")) {
			String[] fields = line.split(SCAN_FIELD_SEPARATOR, -1);
			if( fields.length >= 5 && SCAN_LINE_CLASS.equals(fields[0]) && fields[1].length() > 0 ) {
				plugins.add(new VST3PluginInfo(path, fields[1], fields[2], fields[3], fields[4]));
			}
		}
		return plugins;
	}
}
