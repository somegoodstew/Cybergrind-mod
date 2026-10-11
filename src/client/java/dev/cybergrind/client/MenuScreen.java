package dev.cybergrind.client;

import dev.cybergrind.Arena;
import dev.cybergrind.Settings;
import java.lang.reflect.Constructor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The Cybergrind menu: settings, and start / stop / skip. Opened with its key (K) or from the pause menu. Single-player only. */
public final class MenuScreen extends Screen {
	private static final int WIDTH = 290;
	private static final int ROW = 24;
	private static final int STEP = 26;

	private final Screen parent;
	private Button stop;
	private Button skip;

	public MenuScreen(final Screen parent) {
		super(Component.literal("CYBERGRIND"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Settings s = Settings.get();
		int left = width / 2 - WIDTH / 2;
		int y = Math.max(44, height / 2 - 100);

		// 1. on/off options, two to a row
		int half = (WIDTH - 4) / 2;
		toggle(left, y, half, "Mob Drops", () -> s.drops, v -> s.drops = v,
			"Loot from arena enemies. XP drops either way (Progression Mode turns it into P).");
		toggle(left + half + 4, y, half, "Run Music", () -> s.music, v -> s.music = v,
			"The Cyber Grind music in place of Minecraft's during a run (from the \"Cybergrind Music\" resource pack).");
		y += ROW;
		toggle(left, y, half, "End on Death", () -> s.endOnDeath, v -> s.endOnDeath = v,
			"ON: dying (V1 or Steve) ends the run at once. OFF: the run goes on, and after respawning you're brought back to the arena.");
		toggle(left + half + 4, y, half, "Arena Wall", () -> s.wall, v -> s.wall = v,
			"ON: the red ring; 5 seconds outside it ends the run. OFF: no wall, waves spawn around you, and enemies left over 40 blocks behind come back to you. Takes effect on the next run.");
		y += ROW;

		addRenderableWidget(CycleButton.builder((Settings.Outlines o) -> (Component) Component.literal(o.label).withStyle(switch (o) {
				case ALWAYS -> ChatFormatting.GREEN;
				case LAST_FIVE -> ChatFormatting.YELLOW;
				case OFF -> ChatFormatting.RED;
			}), s.outlines)
			.withValues(Settings.Outlines.values())
			.withTooltip(o -> Tooltip.create(Component.literal("The red/gold/purple outlines on enemies, seen through walls. "
				+ "Last 5 Enemies: only once a wave is down to its last 5.")))
			.create(left, y, WIDTH, 20, Component.literal("Enemy Outlines"), (b, value) -> {
				s.outlines = value;
				s.save();
			}));
		y += ROW;

		// 2. numbers with steppers
		stepper(left, y, "Starting Wave", () -> s.startWave, v -> s.startWave = v, 10, 1);
		y += ROW;
		stepper(left, y, "Max Enemies per Wave", () -> s.cap, v -> s.cap = v, 5, 1);
		y += ROW;
		stepper(left, y, "Spawn Radius", () -> s.range, v -> s.range = v, 5, 1);
		y += ROW;

		// 3. progression mode belongs to the ULTRAKILL mod: open its own settings screen
		Constructor<?> ukSettings = ultrakillSettings();
		Button uk = addRenderableWidget(Button.builder(Component.literal("ULTRAKILL Settings (Progression Mode)..."), b -> {
				try {
					minecraft.gui.setScreen((Screen) ukSettings.newInstance(this));
				} catch (ReflectiveOperationException e) {
					b.active = false;
				}
			}).bounds(left, y, WIDTH, 20)
			.tooltip(Tooltip.create(Component.literal(ukSettings != null
				? "Progression Mode is the ULTRAKILL mod's setting: guns bought with P earned from XP. Turning it on takes your guns and starts P at 0."
				: "The ULTRAKILL x Minecraft mod isn't installed.")))
			.build());
		uk.active = ukSettings != null;
		y += ROW + 8;

		// 4. game controls
		addRenderableWidget(Button.builder(Component.literal("▶ START CYBERGRIND").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), b -> run(Arena::start, true))
			.bounds(left, y, half, 20)
			.tooltip(Tooltip.create(Component.literal("Start a run here: where you stand is the arena centre. Starts over if a run is going.")))
			.build());
		stop = addRenderableWidget(Button.builder(Component.literal("■ STOP / RESET").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), b -> run(Arena::stop, false))
			.bounds(left + half + 4, y, half, 20)
			.tooltip(Tooltip.create(Component.literal("End the run and clear its enemies.")))
			.build());
		y += ROW;
		skip = addRenderableWidget(Button.builder(Component.literal("Skip Wave"), b -> run(Arena::skip, true))
			.bounds(left, y, WIDTH, 20)
			.tooltip(Tooltip.create(Component.literal("Remove this wave's enemies and go on to the next.")))
			.build());

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
		updateButtons();
	}

	/**
	 * Label, then [-big] [-small] [typed number] [+small] [+big]. Typing changes the setting as you go; it's kept within the
	 * setting's limits when the menu closes (or a button is pressed).
	 */
	private void stepper(final int left, final int y, final String label, final IntSupplier get, final IntConsumer set, final int big, final int small) {
		int box = 46;
		int rowWidth = 4 * STEP + box + 4 * 2;
		int x = left + WIDTH - rowWidth;
		addRenderableWidget(new StringWidget(left, y + 6, WIDTH - rowWidth - 4, 9, Component.literal(label), font));

		EditBox field = new EditBox(font, x + 2 * (STEP + 2), y, box, 20, Component.literal(label));
		field.setMaxLength(3);
		field.setValue(String.valueOf(get.getAsInt()));
		field.setResponder(text -> {
			String digits = text.replaceAll("[^0-9]", "");
			if (!digits.equals(text)) {
				field.setValue(digits);
				return;
			}
			if (!digits.isEmpty()) {
				set.accept(Integer.parseInt(digits));
			}
		});

		int[] steps = {-big, -small, small, big};
		int[] xs = {x, x + STEP + 2, x + 2 * (STEP + 2) + box + 2, x + 3 * (STEP + 2) + box + 2};
		for (int i = 0; i < 4; i++) {
			int step = steps[i];
			Component text = Component.literal((step > 0 ? "+" : "") + step).withStyle(step > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
			addRenderableWidget(Button.builder(text, b -> {
				Settings s = Settings.get();
				s.clamp();
				set.accept(get.getAsInt() + step);
				s.clamp();
				s.save();
				field.setValue(String.valueOf(get.getAsInt()));
			}).bounds(xs[i], y, STEP, 20).build());
		}
		addRenderableWidget(field);
	}

	/** Typed numbers are kept within their limits and saved however the menu closes. */
	@Override
	public void removed() {
		Settings.get().save();
		super.removed();
	}

	/** An ON/OFF button for one setting, saved when clicked. */
	private void toggle(final int x, final int y, final int width, final String label, final BooleanSupplier get, final Consumer<Boolean> set, final String tooltip) {
		addRenderableWidget(Button.builder(onOff(label, get.getAsBoolean()), b -> {
				set.accept(!get.getAsBoolean());
				Settings.get().save();
				b.setMessage(onOff(label, get.getAsBoolean()));
			}).bounds(x, y, width, 20)
			.tooltip(Tooltip.create(Component.literal(tooltip)))
			.build());
	}

	private static Component onOff(final String label, final boolean on) {
		return Component.literal(label + ": ").append(on
			? Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
			: Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
	}

	/** Runs an arena action on the integrated server, as this player. */
	private void run(final BiConsumer<Arena, ServerPlayer> action, final boolean close) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.player == null) {
			return;
		}
		java.util.UUID id = minecraft.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				action.accept(Arena.of(server), player);
			}
		});
		if (close) {
			minecraft.gui.setScreen(null);
		}
	}

	private void updateButtons() {
		Arena a = Arena.current();
		boolean running = a != null && a.active();
		if (stop != null) {
			stop.active = running;
		}
		if (skip != null) {
			skip.active = running;
		}
	}

	@Override
	public void tick() {
		super.tick();
		updateButtons();
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int top = Math.max(44, height / 2 - 100);
		graphics.centeredText(font, Component.literal("CYBERGRIND").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, top - 32, 0xFFFFFFFF);
		Arena a = Arena.current();
		Component status;
		if (a != null && a.active()) {
			status = Component.literal("RUNNING").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
				.append(Component.literal(" · wave " + a.wave() + " · " + a.alive() + " enemies left · armor +" + Arena.armorFor(a.wave()) + " · best " + a.best()).withStyle(ChatFormatting.GRAY));
		} else {
			status = Component.literal("Idle · best wave " + (a != null ? a.best() : 0)).withStyle(ChatFormatting.GRAY);
		}
		graphics.centeredText(font, status, width / 2, top - 18, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** The ULTRAKILL x Minecraft mod's settings screen (where Progression Mode lives), if that mod is installed. */
	private static Constructor<?> ultrakillSettings() {
		try {
			Constructor<?> c = Class.forName("dev.ukmc.ukpassthrough.client.SettingsScreen").getDeclaredConstructor(Screen.class);
			c.setAccessible(true);
			return c;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
