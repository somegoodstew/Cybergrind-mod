package dev.cybergrind.client;

import dev.cybergrind.Settings;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The run music menu: shuffle, open the music folder, rescan it, next song, and per song (8 a page) ▲/▼ to reorder and an
 * ON/OFF button.
 */
final class MusicScreen extends Screen {
	private static final int WIDTH = 290;
	private static final int ROW = 24;
	private static final int PER_PAGE = 8;
	private static final int ARROW = 20;

	private final Screen parent;
	private int page;
	private Button next;

	MusicScreen(final Screen parent) {
		super(Component.literal("Run Music"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Settings s = Settings.get();
		List<MusicLibrary.Track> tracks = MusicLibrary.scan();
		int pages = Math.max(1, (tracks.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.clamp(page, 0, pages - 1);
		int left = width / 2 - WIDTH / 2;
		int third = (WIDTH - 8) / 3;
		int y = 36;

		addRenderableWidget(Button.builder(shuffleLabel(s), b -> {
				s.shuffle = !s.shuffle;
				s.save();
				b.setMessage(shuffleLabel(s));
			}).bounds(left, y, third, 20)
			.tooltip(Tooltip.create(Component.literal("ON: random order, never the same song twice in a row. OFF: the order of this list.")))
			.build());
		addRenderableWidget(Button.builder(Component.literal("Open Folder"), b -> MusicLibrary.openFolder())
			.bounds(left + third + 4, y, third, 20)
			.tooltip(Tooltip.create(Component.literal("The cybergrind-music folder: put .ogg songs in it, then Rescan.")))
			.build());
		addRenderableWidget(Button.builder(Component.literal("Rescan"), b -> rebuildWidgets())
			.bounds(left + 2 * (third + 4), y, WIDTH - 2 * (third + 4), 20)
			.tooltip(Tooltip.create(Component.literal("Look at the music folder again after adding or removing songs.")))
			.build());
		y += ROW + 4;

		int from = page * PER_PAGE;
		for (int i = from; i < Math.min(tracks.size(), from + PER_PAGE); i++) {
			MusicLibrary.Track track = tracks.get(i);
			int index = i;
			Button up = addRenderableWidget(Button.builder(Component.literal("▲"), b -> move(track, -1, index))
				.bounds(left, y, ARROW, 20).tooltip(Tooltip.create(Component.literal("Move up"))).build());
			up.active = i > 0;
			Button down = addRenderableWidget(Button.builder(Component.literal("▼"), b -> move(track, 1, index))
				.bounds(left + ARROW + 2, y, ARROW, 20).tooltip(Tooltip.create(Component.literal("Move down"))).build());
			down.active = i < tracks.size() - 1;
			addRenderableWidget(Button.builder(trackLabel(track, s), b -> {
					if (!s.disabledTracks.remove(track.key())) {
						s.disabledTracks.add(track.key());
					}
					s.save();
					b.setMessage(trackLabel(track, s));
				}).bounds(left + 2 * (ARROW + 2), y, WIDTH - 2 * (ARROW + 2), 20)
				.tooltip(Tooltip.create(Component.literal(track.pack() ? "From the \"Cybergrind Music\" resource pack (intro, then the loop)."
					: track.key())))
				.build());
			y += ROW;
		}

		int bottom = height - 28;
		int quarter = (WIDTH - 12) / 4;
		Button prev = addRenderableWidget(Button.builder(Component.literal("< Page"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(left, bottom - ROW, quarter, 20).build());
		prev.active = page > 0;
		Button nextPage = addRenderableWidget(Button.builder(Component.literal("Page >"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(left + quarter + 4, bottom - ROW, quarter, 20).build());
		nextPage.active = page < pages - 1;
		next = addRenderableWidget(Button.builder(Component.literal("Next Song ▶"), b -> RunMusic.skip())
			.bounds(left + 2 * (quarter + 4), bottom - ROW, WIDTH - 2 * (quarter + 4), 20)
			.tooltip(Tooltip.create(Component.literal("Skip to another song (during a run).")))
			.build());
		next.active = RunMusic.playing();
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, bottom, 200, 20).build());
	}

	/** Moves a song and keeps it in view (a move across a page edge turns the page with it). */
	private void move(final MusicLibrary.Track track, final int by, final int index) {
		MusicLibrary.move(track, by);
		page = (index + by) / PER_PAGE;
		rebuildWidgets();
	}

	private static Component shuffleLabel(final Settings s) {
		return Component.literal("Shuffle: ").append(s.shuffle
			? Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
			: Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
	}

	private static Component trackLabel(final MusicLibrary.Track track, final Settings s) {
		String name = track.name().length() > 30 ? track.name().substring(0, 29) + "…" : track.name();
		boolean on = !s.disabledTracks.contains(track.key());
		return Component.literal("♪ " + name + "  ").append(on
			? Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
			: Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
	}

	@Override
	public void tick() {
		super.tick();
		if (next != null) {
			next.active = RunMusic.playing();
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, Component.literal("RUN MUSIC").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, 12, 0xFFFFFFFF);
		List<MusicLibrary.Track> tracks = MusicLibrary.tracks();
		MusicLibrary.Track now = RunMusic.current();
		Component status;
		if (tracks.isEmpty()) {
			status = Component.literal("No songs yet: put .ogg files in the folder (Open Folder), then Rescan.").withStyle(ChatFormatting.GRAY);
		} else if (now != null) {
			status = Component.literal("Now playing: ").withStyle(ChatFormatting.GRAY).append(Component.literal(now.name()).withStyle(ChatFormatting.WHITE));
		} else {
			int pages = Math.max(1, (tracks.size() + PER_PAGE - 1) / PER_PAGE);
			status = Component.literal(tracks.size() + " songs · page " + (page + 1) + "/" + pages).withStyle(ChatFormatting.GRAY);
		}
		graphics.centeredText(font, status, width / 2, 24, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
