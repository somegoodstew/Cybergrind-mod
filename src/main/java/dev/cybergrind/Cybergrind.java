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
	/** A development build (gradlew build -PdebugBuild=true): the menu has the Debug screen. Releases are built without it. */
	public static final boolean DEBUG = readDebugFlag();

	private static boolean readDebugFlag() {
		try (java.io.InputStream in = Cybergrind.class.getResourceAsStream("/cybergrind-build.properties")) {
			java.util.Properties p = new java.util.Properties();
			if (in != null) {
				p.load(in);
			}
			return Boolean.parseBoolean(p.getProperty("debug", "false"));
		} catch (java.io.IOException e) {
			return false;
		}
	}

	@Override
	public void onInitialize() {
		// ULTRAKILL's impact frames freeze Minecraft's mobs too (needs the Cybergrind Bridge plugin in ULTRAKILL)
		HitStop.listen();
		ServerTickEvents.START_SERVER_TICK.register(HitStop::tick);
		ServerTickEvents.END_SERVER_TICK.register(server -> Arena.of(server).tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(Arena::shutdown);

		// mob drops off: arena enemies drop no loot (their gear never drops anyway; XP still does)
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Entity killed = context.getOptional(LootContextParams.THIS_ENTITY);
			if (killed != null && Arena.dropsOff(killed)) {
				drops.clear();
			}
		});

		// arena ghasts' fireballs: bigger and faster; arena witches' clouds: short; bow users' arrows: faster
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			Abilities.boost(entity);
			Abilities.shortenCloud(entity);
			Abilities.quickenArrow(entity);
			Abilities.trackVex(entity);
		});

		// ghast and blaze fireballs, wither skulls, witch potions and rapid shooters' arrows don't hurt other arena enemies;
		// the Flying Elder Guardian has no thorns
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> !Abilities.blocked(victim, source));
		// V1's i-frames (dashing, the moments after a hit, parries and impact frames, as ULTRAKILL reports them): no damage gets through
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> !(victim instanceof ServerPlayer && HitStop.invulnerable(source)));

		// deaths: a Flying Elder Guardian leaves a heal orb, a Drone dives at you, a golem's death enrages the others
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> Abilities.onDeath(entity));
		// a Drone's killing blow starts its dive instead (it explodes when the dive ends)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> Abilities.allowDeath(entity));

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
		LOG.info("Cybergrind loaded{}", DEBUG ? " (debug build)" : "");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> action(final String name, final BiConsumer<Arena, ServerPlayer> run) {
		return Commands.literal(name).executes(c -> {
			ServerPlayer player = c.getSource().getPlayerOrException();
			run.accept(Arena.of(c.getSource().getServer()), player);
			return Command.SINGLE_SUCCESS;
		});
	}
}
