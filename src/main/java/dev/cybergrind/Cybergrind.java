package dev.cybergrind;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.BiConsumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cybergrind: endless ULTRAKILL-style waves of Minecraft mobs. Server side: the run, the /cybergrind command, the drops toggle. */
public final class Cybergrind implements ModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("cybergrind");

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> Arena.of(server).tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(Arena::shutdown);

		// mob drops off: arena enemies drop no loot (their gear never drops anyway; XP still does)
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Entity killed = context.getOptional(LootContextParams.THIS_ENTITY);
			if (killed != null && Arena.dropsOff(killed)) {
				drops.clear();
			}
		});

		// arena ghasts' fireballs: bigger and faster
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Abilities.boost(entity));

		// ghast and blaze fireballs, wither skulls and rapid shooters' arrows don't hurt other arena enemies
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> !Abilities.friendlyFire(victim, source));

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
			Commands.literal("cybergrind")
				.executes(c -> {
					c.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(
						"Cybergrind: /cybergrind start | stop | skip, or press K (or Esc > Cybergrind...) for the menu."));
					return Command.SINGLE_SUCCESS;
				})
				.then(action("start", Arena::start))
				.then(action("stop", Arena::stop))
				.then(action("skip", Arena::skip))));
		LOG.info("Cybergrind loaded");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> action(final String name, final BiConsumer<Arena, ServerPlayer> run) {
		return Commands.literal(name).executes(c -> {
			ServerPlayer player = c.getSource().getPlayerOrException();
			run.accept(Arena.of(c.getSource().getServer()), player);
			return Command.SINGLE_SUCCESS;
		});
	}
}
