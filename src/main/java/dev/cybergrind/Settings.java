package dev.cybergrind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** The menu's settings, kept in config/cybergrind.json. Read by the client menu, used by the server tick. */
public final class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cybergrind.json");

	public static final int MAX_WAVE = 999;

	/** Arena mobs drop their loot. XP drops either way (Progression Mode turns it into P). */
	public volatile boolean drops = true;
	/** Run music in place of Minecraft's: the player's own songs (MusicLibrary), picked in the Music menu. */
	public volatile boolean music = true;
	/** Run music in random order (never the same song twice in a row); off: in the order the menu lists them. */
	public volatile boolean shuffle = true;
	/** Songs switched off in the menu (file names; new songs are on until switched off). */
	public java.util.Set<String> disabledTracks = new java.util.LinkedHashSet<>();
	/** The song list's order from the menu (file names; songs not in it go after, A-Z). */
	public java.util.List<String> trackOrder = new java.util.ArrayList<>();
	/** Dying (V1 or Steve) ends the run at once. Off: the run goes on, and you're brought back to the arena after respawning. */
	public volatile boolean endOnDeath = true;
	/** The arena wall (red ring, 5 s outside ends the run). Off: no wall, and enemies left too far behind come to you. */
	public volatile boolean wall = true;
	/** When enemies are outlined (glowing, seen through walls). */
	public volatile Outlines outlines = Outlines.ALWAYS;

	public enum Outlines {
		ALWAYS("Always"),
		LAST_FIVE("Last 5 Enemies"),
		OFF("Off");

		public final String label;

		Outlines(final String label) {
			this.label = label;
		}
	}

	public volatile int startWave = 1;
	/** Most enemies in one wave (bosses come on top). */
	public volatile int cap = 35;
	/** How far from the centre (or from you, without the wall) enemies spawn; the wall is 4 blocks further out. */
	public volatile int range = 20;

	private static Settings instance;

	private Settings() {
	}

	public static synchronized Settings get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Settings load() {
		try {
			if (Files.exists(FILE)) {
				Settings s = GSON.fromJson(Files.readString(FILE), Settings.class);
				if (s != null) {
					s.clamp();
					return s;
				}
			}
		} catch (IOException | RuntimeException e) {
			Cybergrind.LOG.warn("Couldn't read {}, using defaults", FILE, e);
		}
		return new Settings();
	}

	public void clamp() {
		startWave = Math.clamp(startWave, 1, MAX_WAVE);
		cap = Math.clamp(cap, 5, 100);
		range = Math.clamp(range, 8, 40);
		if (outlines == null) {
			outlines = Outlines.ALWAYS;
		}
		if (disabledTracks == null) {
			disabledTracks = new java.util.LinkedHashSet<>();
		}
		if (trackOrder == null) {
			trackOrder = new java.util.ArrayList<>();
		}
	}

	public synchronized void save() {
		clamp();
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(this));
		} catch (IOException e) {
			Cybergrind.LOG.warn("Couldn't save {}", FILE, e);
		}
	}
}
