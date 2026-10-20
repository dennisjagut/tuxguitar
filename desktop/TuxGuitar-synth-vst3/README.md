# TuxGuitar Synthesizer - VST3 extension

Hosts VST3 plugins in the TuxGuitar Synthesizer, on Windows x86_64.

- **VST3 effects** (type `VST3`) can be added to the effect chain of a channel, e.g. an amp simulator.
- **VST3 instruments** (type `VST3 Instrument`) can be used as the channel receiver, e.g. a sampled DI guitar.

Each plugin runs in its own `tuxguitar-synth-vst3.exe` process. It talks to TuxGuitar over the same local TCP protocol as the LV2 extension (`TGRemoteHost`), so a crashing plugin cannot take TuxGuitar down. The plugin state is saved in the song (`.tg` file) as channel parameters.

## Guitar amp simulators

Amp simulators like Neural DSP Archetype Nolly expect a clean (DI) guitar signal as input. Build the channel chain like this:

1. Set the receiver to one of these:
   - **Gervill** with *Clean Guitar* (program 27), or a DI guitar SoundFont
   - a **VST3 Instrument** that plays a DI guitar (amp disabled in the instrument)
2. Add the amp simulator as a **VST3** effect.

Avoid the GM *Overdriven* and *Distortion* guitar programs, because they already contain amp distortion.

## Usage

1. Select *Tools > Settings > Sound > TuxGuitar Synthesizer* (the default output port).
2. Open *View > Show Instruments* and click the setup button of the guitar channel.
3. Choose a receiver, then *Add* an effect of type `VST3`. The plugin chooser lists the installed plugins.
4. *Edit* opens the plugin's own editor window. Changes made in the editor are saved in the song.

Plugins are searched in these folders:
- `%CommonProgramFiles%\VST3`
- `%LOCALAPPDATA%\Programs\Common\VST3`
- any additional folder listed in `vst3.plugin.paths` in `tuxguitar-synth-vst3.cfg`, separated by `;`

Scan results are cached in `tuxguitar-synth-vst3-cache.cfg` in the user configuration folder.

The engine runs at 44.1 kHz with 512 frame blocks.

## Build

The native client is built with CMake and mingw-w64, the same way the other Windows native modules are cross compiled (see [INSTALL.md](../../INSTALL.md), *Build for Windows on Linux*). CMake downloads the MIT licensed [VST3 SDK](https://github.com/steinbergmedia/vst3sdk) at configure time.

Additional prerequisites:

```bash
sudo apt install cmake g++-mingw-w64-x86-64-posix
```

The module is part of the `native-modules` profile of the Windows build:

```bash
cd desktop/build-scripts/tuxguitar-windows-swt-x86_64
mvn -e clean verify -P native-modules -P -platform-linux -P platform-windows
```

The resulting application contains:
- `vst3-client/tuxguitar-synth-vst3.exe`
- `dist/tuxguitar-synth-vst3.cfg`
- `share/plugins/tuxguitar-synth-vst3.jar`

To build only the Java part, for example on a machine without cmake / mingw-w64, add `-Dvst3.native.skip=true`.

## Troubleshooting

List the plugin classes of a module the same way TuxGuitar scans it:

```bat
vst3-client\tuxguitar-synth-vst3.exe --scan "C:\Program Files\Common Files\VST3\Archetype Nolly.vst3"
```

Client messages are printed to the TuxGuitar console, prefixed with `[tuxguitar-synth-vst3]`.
