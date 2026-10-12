package dev.cybergrind.client;

import dev.cybergrind.Arena;
import dev.cybergrind.Settings;
import dev.cybergrind.Spawns;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * For development: every enemy, with where it shows up, what it does, its health and damage, and buttons to spawn it in front
 * of you (as at the current wave, or the menu's Starting Wave when no run is going) or clear them all.
 */
final class DebugScreen extends Screen {
	private static final int LIST_WIDTH = 130;
	private static final int PANEL_WIDTH = 230;
	private static final int PER_PAGE = 12;

	private final Screen parent;
	private int page;
	private int selected;

	DebugScreen(final Screen parent) {
		super(Component.literal("Debug: Enemies"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<Spawns.Entry> all = Spawns.CATALOG;
		int pages = (all.size() + PER_PAGE - 1) / PER_PAGE;
		int left = width / 2 - (LIST_WIDTH + 10 + PANEL_WIDTH) / 2;
		int panel = left + LIST_WIDTH + 10;
		int top = 30;

		for (int i = page * PER_PAGE; i < Math.min(all.size(), (page + 1) * PER_PAGE); i++) {
			int index = i;
			Component label = Component.literal((i == selected ? "▶ " : "") + all.get(i).name())
				.withStyle(i == selected ? ChatFormatting.GOLD : ChatFormatting.WHITE);
			addRenderableWidget(Button.builder(label, b -> {
				selected = index;
				rebuildWidgets();
			}).bounds(left, top + (i - page * PER_PAGE) * 18, LIST_WIDTH, 16).build());
		}
		int pagesY = top + PER_PAGE * 18 + 2;
		Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(left, pagesY, 30, 16).build());
		prev.active = page > 0;
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(left + LIST_WIDTH - 30, pagesY, 30, 16).build());
		next.active = page < pages - 1;

		Spawns.Entry entry = all.get(selected);
		int buttonsY = height - 52;
		int third = (PANEL_WIDTH - 8) / 3;
		addRenderableWidget(Button.builder(Component.literal("Spawn x1").withStyle(ChatFormatting.GREEN), b -> run((a, p) -> a.debugSpawn(p, entry.key(), 1)))
			.bounds(panel, buttonsY, third, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Spawn x5").withStyle(ChatFormatting.GREEN), b -> run((a, p) -> a.debugSpawn(p, entry.key(), 5)))
			.bounds(panel + third + 4, buttonsY, third, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Clear All").withStyle(ChatFormatting.RED), b -> run(Arena::debugClear))
			.bounds(panel + 2 * (third + 4), buttonsY, PANEL_WIDTH - 2 * (third + 4), 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	/** Runs on the integrated server as this player, then closes the menus so you can watch. */
	private void run(final BiConsumer<Arena, ServerPlayer> action) {
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
		minecraft.gui.setScreen(null);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, Component.literal("DEBUG - ENEMIES").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), width / 2, 12, 0xFFFFFFFF);

		Spawns.Entry entry = Spawns.CATALOG.get(selected);
		int left = width / 2 - (LIST_WIDTH + 10 + PANEL_WIDTH) / 2;
		int x = left + LIST_WIDTH + 10;
		int y = 30;
		graphics.text(font, Component.literal(entry.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, 0xFFFFFFFF);
		y += 12;
		graphics.text(font, Component.literal(entry.where() + "  (" + entry.type() + ")").withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFFFF);
		y += 14;

		Stats stats = stats(entry);
		y = line(graphics, x, y, "Health", stats.health());
		y = line(graphics, x, y, "Melee", stats.melee());
		y = line(graphics, x, y, "Other damage", entry.damage());
		int wave = Arena.current() != null && Arena.current().active() ? Arena.current().wave() : Math.max(1, Settings.get().startWave);
		y = line(graphics, x, y, "At wave " + wave, "+" + Math.min(50 + 3 * wave, 120) + "% speed, "
			+ Math.clamp((wave - 9) * 3L, 0, 40) + "% radiant, run armour " + Arena.armorFor(wave));
		y += 4;
		for (FormattedCharSequence part : font.split(Component.literal(entry.description()).withStyle(ChatFormatting.WHITE), PANEL_WIDTH)) {
			graphics.text(font, part, x, y, 0xFFFFFFFF);
			y += font.lineHeight + 1;
		}
	}

	/** "Label: value", the value wrapped under the panel's width. */
	private int line(final GuiGraphicsExtractor graphics, final int x, final int y, final String label, final String value) {
		Component text = Component.literal(label + ": ").withStyle(ChatFormatting.AQUA).append(Component.literal(value).withStyle(ChatFormatting.WHITE));
		int at = y;
		for (FormattedCharSequence part : font.split(text, PANEL_WIDTH)) {
			graphics.text(font, part, x, at, 0xFFFFFFFF);
			at += font.lineHeight + 1;
		}
		return at + 2;
	}

	private record Stats(String health, String melee) {
	}

	/** Health and melee from the game's own values for the mob (this mod's health changes win). */
	@SuppressWarnings("unchecked")
	private static Stats stats(final Spawns.Entry entry) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(entry.type()));
		if (!DefaultAttributes.hasSupplier(type)) {
			return new Stats(entry.health() != null ? fmt(entry.health()) : "?", "-");
		}
		AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
		double health = entry.health() != null ? entry.health() : supplier.getBaseValue(Attributes.MAX_HEALTH);
		String melee = supplier.hasAttribute(Attributes.ATTACK_DAMAGE)
			? fmt(supplier.getBaseValue(Attributes.ATTACK_DAMAGE)) + " base (Normal; x1.5 on Hard; weapons add more)"
			: "-";
		return new Stats(fmt(health) + " (" + fmt(health / 2) + " hearts)", melee);
	}

	private static String fmt(final double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
