package dev.cybergrind.client;

import dev.cybergrind.Arena;
import dev.cybergrind.Settings;
import com.mojang.blaze3d.audio.Channel;
import dev.cybergrind.client.mixin.SoundEngineAccessor;
import dev.cybergrind.client.mixin.SoundManagerAccessor;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The run's music in place of Minecraft's while a run is going: the player's songs (MusicLibrary), shuffled or in order,
 * never the same one twice in a row; a single song loops. The resource pack's Cyber Grind plays its intro, then its loop.
 * Client thread.
 */
public final class RunMusic {
	private static final Identifier PACK_INTRO_FILE = Identifier.fromNamespaceAndPath("cybergrind", "sounds/intro.ogg");
	private static final Identifier PACK_INTRO = Identifier.fromNamespaceAndPath("cybergrind", "intro");
	private static final Identifier PACK_LOOP = Identifier.fromNamespaceAndPath("cybergrind", "music");
	private static final RandomSource RANDOM = RandomSource.create();

	private static SoundInstance playing;
	private static MusicLibrary.Track track;
	/** The resource pack track is on its intro (its loop comes next). */
	private static boolean onIntro;
	private static MusicLibrary.Track last;
	private static boolean wasRunning;
	private static boolean skipRequested;
	/** The song is paused (a pause-type screen is open: Esc, its options, the Cybergrind menus). */
	private static boolean paused;

	private RunMusic() {
	}

	/** While true, Minecraft's own music manager stays quiet (MusicManagerMixin). */
	public static boolean playing() {
		return playing != null;
	}

	/** The song playing now, or null. */
	public static MusicLibrary.Track current() {
		return playing != null ? track : null;
	}

	/** "Next Song" in the menu. */
	public static void skip() {
		skipRequested = true;
	}

	/** The songs switched on in the menu. */
	public static List<MusicLibrary.Track> playlist() {
		Settings s = Settings.get();
		return MusicLibrary.tracks().stream().filter(t -> !s.disabledTracks.contains(t.key())).toList();
	}

	static void tick(final Minecraft minecraft) {
		Arena arena = Arena.current();
		boolean running = minecraft.level != null && arena != null && arena.active();
		if (running && !wasRunning) {
			// looked at once a run: songs can be added between runs (or with Rescan in the menu)
			MusicLibrary.scan();
			if (Settings.get().music && playlist().isEmpty() && minecraft.player != null) {
				minecraft.player.sendSystemMessage(Component.literal("[Cybergrind] No run music: put .ogg songs in the \"cybergrind-music\" "
					+ "folder (Cybergrind menu > Music... > Open Folder), or switch some on in that menu.").withStyle(ChatFormatting.GRAY));
			}
		}
		wasRunning = running;

		if (!running || !Settings.get().music) {
			stop(minecraft);
			return;
		}

		// the ULTRAKILL mod keeps the game running behind menus, so sounds don't pause by themselves: the song pauses itself
		// while a pause-type screen is open (not the inventory or chat)
		boolean pauseWanted = minecraft.gui.screen() != null && minecraft.gui.screen().isPauseScreen();
		if (pauseWanted != paused) {
			paused = pauseWanted;
			setPaused(minecraft, paused);
		}
		if (paused && !skipRequested) {
			return;
		}

		if (skipRequested) {
			skipRequested = false;
			stop(minecraft);
		}
		if (playing != null && minecraft.getSoundManager().isActive(playing)) {
			return;
		}

		List<MusicLibrary.Track> list = playlist();
		if (playing != null && track != null && track.pack() && onIntro) {
			// the Cyber Grind intro ended: its loop now (looping for good if it's the only song)
			onIntro = false;
			start(minecraft, packSound(PACK_LOOP, list.size() <= 1));
			return;
		}
		if (list.isEmpty()) {
			stop(minecraft);
			return;
		}

		MusicLibrary.Track next = pick(list);
		track = next;
		last = next;
		boolean only = list.size() == 1;
		if (next.pack()) {
			onIntro = minecraft.getResourceManager().getResource(PACK_INTRO_FILE).isPresent();
			start(minecraft, onIntro ? packSound(PACK_INTRO, false) : packSound(PACK_LOOP, only));
		} else {
			onIntro = false;
			start(minecraft, new FileSoundInstance(next.file(), only));
		}
	}

	/** Shuffle: any song but the last one. In order: the one after the last one. */
	private static MusicLibrary.Track pick(final List<MusicLibrary.Track> list) {
		if (list.size() == 1) {
			return list.get(0);
		}
		int lastIndex = last == null ? -1 : list.indexOf(last);
		if (Settings.get().shuffle) {
			int i;
			do {
				i = RANDOM.nextInt(list.size());
			} while (i == lastIndex);
			return list.get(i);
		}
		return list.get((lastIndex + 1) % list.size());
	}

	private static void start(final Minecraft minecraft, final SoundInstance sound) {
		minecraft.getMusicManager().stopPlaying();
		playing = sound;
		minecraft.getSoundManager().play(sound);
	}

	private static void setPaused(final Minecraft minecraft, final boolean pause) {
		if (playing == null) {
			return;
		}
		SoundEngine engine = ((SoundManagerAccessor) minecraft.getSoundManager()).cybergrind$soundEngine();
		ChannelAccess.ChannelHandle channel = ((SoundEngineAccessor) engine).cybergrind$channels().get(playing);
		if (channel != null) {
			channel.execute(pause ? Channel::pause : Channel::unpause);
		}
	}

	private static void stop(final Minecraft minecraft) {
		paused = false;
		if (playing != null) {
			minecraft.getSoundManager().stop(playing);
			playing = null;
		}
		onIntro = false;
	}

	/** The resource pack's sounds: not positional, on the Music volume slider. */
	private static SoundInstance packSound(final Identifier event, final boolean looping) {
		return new SimpleSoundInstance(event, SoundSource.MUSIC, 1, 1, RandomSource.create(), looping, 0,
			SoundInstance.Attenuation.NONE, 0, 0, 0, true);
	}
}
