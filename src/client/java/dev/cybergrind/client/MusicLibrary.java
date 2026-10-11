package dev.cybergrind.client;

import dev.cybergrind.Cybergrind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.cybergrind.Settings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The run music the player has: every .ogg file in the "cybergrind-music" folder of the game directory, plus the "Cybergrind
 * Music" resource pack's track (intro.ogg + music.ogg) if one is on. No music ships with the mod. Client thread.
 */
public final class MusicLibrary {
	/** Namespace of the sound ids that stand for files in the music folder (SoundBufferLibraryMixin reads them from disk). */
	public static final String FILE_NAMESPACE = "cybergrind_file";
	/** The resource pack track's key in the settings (files use their file name). */
	public static final String PACK_KEY = "<resource pack>";

	private static final Identifier PACK_LOOP = Identifier.fromNamespaceAndPath("cybergrind", "sounds/music.ogg");
	private static final String README = String.join("\n",
		"Put your Cybergrind run music here.",
		"",
		"- Files must be Ogg Vorbis (.ogg). Convert .mp3, .wav or .flac with Audacity (File > Export Audio > Ogg) or any converter.",
		"- Every .ogg in this folder shows up in the Cybergrind menu (Music...), where each song can be switched on or off.",
		"- Songs shuffle during a run (or play in order, if Shuffle is off). A single song loops.",
		"- Click Rescan in the menu after adding or removing files.",
		"- Use music you have the right to use, and don't share other people's music with the mod.",
		"");

	public record Track(String key, String name, Path file) {
		public boolean pack() {
			return file == null;
		}
	}

	/** Sound id path (like "t3") -> file, for every file that has been given an id. Ids are never reused. */
	private static final Map<String, Path> IDS = new HashMap<>();
	private static final Map<Path, String> BY_FILE = new HashMap<>();
	private static List<Track> tracks = List.of();

	private MusicLibrary() {
	}

	public static Path folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("cybergrind-music");
	}

	/** Looks at the folder (and the resource pack) again. Creates the folder, with a how-to file, the first time. */
	public static List<Track> scan() {
		Path folder = folder();
		List<Track> found = new ArrayList<>();
		if (Minecraft.getInstance().getResourceManager().getResource(PACK_LOOP).isPresent()) {
			found.add(new Track(PACK_KEY, "Cyber Grind (resource pack)", null));
		}
		try {
			Files.createDirectories(folder);
			Path readme = folder.resolve("README.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, README);
			}
			try (Stream<Path> files = Files.list(folder)) {
				files.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
					.sorted()
					.forEach(f -> {
						String file = f.getFileName().toString();
						found.add(new Track(file, file.substring(0, file.length() - 4), f));
					});
			}
		} catch (IOException e) {
			Cybergrind.LOG.warn("Couldn't read the music folder {}", folder, e);
		}
		// the menu's order first (songs it doesn't know yet keep their place after those: the pack, then A-Z)
		List<String> order = Settings.get().trackOrder;
		found.sort(Comparator.comparingInt(t -> {
			int i = order.indexOf(t.key());
			return i < 0 ? Integer.MAX_VALUE : i;
		}));
		tracks = List.copyOf(found);
		return tracks;
	}

	/** Moves a song up (-1) or down (+1) in the list, and saves the new order. */
	public static void move(final Track track, final int by) {
		List<Track> list = new ArrayList<>(tracks);
		int from = list.indexOf(track);
		int to = from + by;
		if (from < 0 || to < 0 || to >= list.size()) {
			return;
		}
		list.set(from, list.get(to));
		list.set(to, track);
		tracks = List.copyOf(list);
		Settings s = Settings.get();
		s.trackOrder = new ArrayList<>(list.stream().map(Track::key).toList());
		s.save();
	}

	public static List<Track> tracks() {
		return tracks;
	}

	/** The sound id that plays this file (SoundBufferLibraryMixin resolves it back to the file). */
	static Identifier idFor(final Path file) {
		String path = BY_FILE.computeIfAbsent(file, f -> {
			String id = "t" + IDS.size();
			IDS.put(id, f);
			return id;
		});
		return Identifier.fromNamespaceAndPath(FILE_NAMESPACE, path);
	}

	/** For the mixin: "cybergrind_file:sounds/t3.ogg" (or "cybergrind_file:t3") -> the file, or null. */
	public static Path fileFor(final Identifier soundPath) {
		String path = soundPath.getPath();
		if (path.startsWith("sounds/")) {
			path = path.substring("sounds/".length());
		}
		if (path.endsWith(".ogg")) {
			path = path.substring(0, path.length() - 4);
		}
		return IDS.get(path);
	}

	/** Opens the music folder in the system's file manager. */
	public static void openFolder() {
		Path folder = folder();
		try {
			Files.createDirectories(folder);
			String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
			String[] command = os.contains("win") ? new String[] {"explorer.exe", folder.toString()}
				: os.contains("mac") ? new String[] {"open", folder.toString()}
				: new String[] {"xdg-open", folder.toString()};
			new ProcessBuilder(command).start();
		} catch (IOException e) {
			Cybergrind.LOG.warn("Couldn't open the music folder {}", folder, e);
		}
	}
}
