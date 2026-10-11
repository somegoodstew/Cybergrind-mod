package dev.cybergrind.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The menu's key (K, rebindable in Controls) and its button in the pause menu. */
public final class CybergrindClient implements ClientModInitializer {
	private static final int FULL_WIDTH = 204;
	private static boolean keyWasDown;

	private static final KeyMapping OPEN_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cybergrind.menu",
		InputConstants.Type.KEYBOARD, InputConstants.KEY_K, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("cybergrind", "cybergrind"))));

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			RunMusic.tick(minecraft);

			// In V1 mode the ULTRAKILL mod sends most keys to ULTRAKILL and Minecraft never hears of them, so a keyboard key is
			// read straight from the keyboard; a mouse button bound to the menu goes through Minecraft as usual.
			InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(OPEN_MENU);
			boolean pressed = false;
			while (OPEN_MENU.consumeClick()) {
				pressed |= key.getType() != InputConstants.Type.KEYBOARD;
			}
			boolean down = key.getType() == InputConstants.Type.KEYBOARD && minecraft.isWindowActive() && InputConstants.isKeyDown(key.getValue());
			pressed |= down && !keyWasDown;
			keyWasDown = down;
			if (pressed && minecraft.gui.screen() == null) {
				open(minecraft);
			}
		});

		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
				addPauseButton(minecraft, pause);
			}
		});
	}

	private static void open(final Minecraft minecraft) {
		if (minecraft.getSingleplayerServer() == null) {
			if (minecraft.player != null) {
				minecraft.player.sendOverlayMessage(Component.literal("Cybergrind's menu works in single-player worlds."));
			}
			return;
		}
		minecraft.gui.setScreen(new MenuScreen(minecraft.gui.screen()));
	}

	/**
	 * "Cybergrind..." above "Save and Quit to Title" (the lowest full-width button), which moves down a row. The same placement
	 * as the ULTRAKILL x Minecraft mod's pause button (MIT), so the two stack.
	 */
	private static void addPauseButton(final Minecraft minecraft, final PauseScreen pause) {
		if (minecraft.getSingleplayerServer() == null) {
			return;
		}
		List<AbstractWidget> widgets = Screens.getWidgets(pause);
		AbstractWidget last = null;
		for (AbstractWidget w : widgets) {
			if (w instanceof Button && w.getWidth() >= FULL_WIDTH && (last == null || w.getY() > last.getY())) {
				last = w;
			}
		}

		int x = last != null ? last.getX() : pause.width / 2 - FULL_WIDTH / 2;
		int y = last != null ? last.getY() : pause.height / 4 + 120;
		if (last != null) {
			last.setY(y + 24);
		}
		widgets.add(Button.builder(Component.literal("Cybergrind..."), b -> minecraft.gui.setScreen(new MenuScreen(pause)))
			.bounds(x, y, last != null ? last.getWidth() : FULL_WIDTH, 20).build());
	}
}
