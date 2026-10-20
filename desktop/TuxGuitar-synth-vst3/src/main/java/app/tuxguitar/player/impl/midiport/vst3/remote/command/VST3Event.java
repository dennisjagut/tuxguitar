package app.tuxguitar.player.impl.midiport.vst3.remote.command;

public class VST3Event {

	public static final int NOTE_ON = 1;
	public static final int NOTE_OFF = 2;
	public static final int PITCH_BEND = 3;
	public static final int CONTROL_CHANGE = 4;

	private int type;
	private int data1;
	private int data2;

	public VST3Event(int type, int data1, int data2) {
		this.type = type;
		this.data1 = data1;
		this.data2 = data2;
	}

	public int getType() {
		return this.type;
	}

	public int getData1() {
		return this.data1;
	}

	public int getData2() {
		return this.data2;
	}
}
