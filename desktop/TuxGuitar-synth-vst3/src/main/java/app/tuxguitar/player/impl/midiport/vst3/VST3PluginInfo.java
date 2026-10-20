package app.tuxguitar.player.impl.midiport.vst3;

public class VST3PluginInfo {

	private static final String INSTRUMENT_CATEGORY = "Instrument";

	private String path;
	private String cid;
	private String name;
	private String vendor;
	private String subCategories;

	public VST3PluginInfo(String path, String cid, String name, String vendor, String subCategories) {
		this.path = path;
		this.cid = cid;
		this.name = name;
		this.vendor = vendor;
		this.subCategories = subCategories;
	}

	public String getPath() {
		return this.path;
	}

	public String getCid() {
		return this.cid;
	}

	public String getName() {
		return this.name;
	}

	public String getVendor() {
		return this.vendor;
	}

	public String getSubCategories() {
		return this.subCategories;
	}

	public boolean isInstrument() {
		if( this.subCategories != null ) {
			for(String subCategory : this.subCategories.split("\\|")) {
				if( INSTRUMENT_CATEGORY.equals(subCategory) ) {
					return true;
				}
			}
		}
		return false;
	}

	public String getLabel() {
		if( this.vendor != null && this.vendor.length() > 0 ) {
			return (this.name + " (" + this.vendor + ")");
		}
		return this.name;
	}
}
