package dev.cybergrind;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

/**
 * One Cybergrind run: waves of tagged mobs around a centre, a particle wall, a boss bar and titles. Server thread, except for the
 * volatile fields the client menu reads (single-player: the menu and the integrated server share this object).
 */
public final class Arena {
	private enum End { DIED, LEFT, STOPPED }

	private static final int BREAK_TICKS = 100;
	private static final int FIRST_BREAK_TICKS = 60;
	private static final int OOB_SECONDS = 5;
	private static final int ITEM_CLEAR_RADIUS = 100;
	/** Without the wall, enemies further than this from every player are brought back (the ULTRAKILL mod sees mobs to 48). */
	private static final int FOLLOW_DISTANCE = 40;
	/** Enemies are never put closer than this (sideways) to a player. */
	private static final int MIN_PLAYER_DISTANCE = 8;
	/** Ghasts hover ~10 blocks out from you, so they may go further before being brought back: this far past the wall, ... */
	private static final int GHAST_WALL_SLACK = 24;
	/** ... or, without the wall, this far from you (the ULTRAKILL mod sees mobs to 48). */
	private static final int GHAST_FOLLOW_DISTANCE = 48;
	private static final Identifier SPEED = Identifier.fromNamespaceAndPath("cybergrind", "speed");
	private static final Identifier JUMP = Identifier.fromNamespaceAndPath("cybergrind", "jump");
	private static final Identifier ARMOR = Identifier.fromNamespaceAndPath("cybergrind", "armor");
	private static final Identifier TOUGHNESS = Identifier.fromNamespaceAndPath("cybergrind", "toughness");
	private static final String CENTER_TAG = "cg_center";

	private static volatile Arena current;

	private final MinecraftServer server;
	private final RandomSource random = RandomSource.create();
	private final Abilities abilities = new Abilities(this);
	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Cybergrind"),
		BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

	// read by the menu
	private volatile boolean active;
	private volatile int wave;
	private volatile int best;
	private volatile int alive;

	private ServerLevel level;
	private Vec3 center;
	private int maxY;
	private int range;
	private int wall;
	private int cap;
	/** Ticks left of the break before the next wave; -1 while a wave is being fought. */
	private int breakTicks;
	private int timer;
	private int oob;
	private int provoke;
	/** Whether this run has the wall (from the menu when it started). */
	private boolean walled;
	/** A player (or V1) is dead right now, or was on the last tick. */
	private boolean dead;
	private boolean wasDead;
	/** Ticks until a respawned player is brought back to the arena; -1 when nobody is waiting. */
	private int respawnWait = -1;
	/** The armor and toughness last announced to the player (0 at a run's start). */
	private int shownArmor;
	private int shownToughness;
	/** Enemies spawned this wave. */
	private int count;
	private int kills;

	private Arena(final MinecraftServer server) {
		this.server = server;
		this.best = readBest();
	}

	/** The arena of this server (a new one for each world opened). */
	public static Arena of(final MinecraftServer server) {
		Arena a = current;
		if (a == null || a.server != server) {
			a = new Arena(server);
			current = a;
		}
		return a;
	}

	/** For the menu: the arena of the open world, or null. */
	public static Arena current() {
		return current;
	}

	public static void shutdown(final MinecraftServer server) {
		Arena a = current;
		if (a != null && a.server == server) {
			if (a.active) {
				a.end(End.STOPPED);
			}
			current = null;
		}
	}

	public boolean active() {
		return active;
	}

	public int wave() {
		return wave;
	}

	public int best() {
		return best;
	}

	public int alive() {
		return alive;
	}

	/** The armor points a run gives the player at this wave: 1 per wave up to a full bar (20) at wave 20. */
	public static int armorFor(final int wave) {
		return Math.clamp(wave, 0, 20);
	}

	/** Armor toughness at this wave: 1 per 3 waves, up to 12 (diamond is 8, netherite 12), so big hits are cut too. */
	public static int toughnessFor(final int wave) {
		return Math.clamp(wave / 3, 0, 12);
	}

	// ---- commands ----

	public void start(final ServerPlayer player) {
		ServerLevel l = player.level();
		if (l.getDifficulty() == Difficulty.PEACEFUL) {
			player.sendSystemMessage(prefix().append(Component.literal("Monsters can't exist on Peaceful. Raise the difficulty first.").withStyle(ChatFormatting.RED)));
			return;
		}

		Settings s = Settings.get();
		s.clamp();
		cleanup();
		level = l;
		center = player.position();
		maxY = (int) Math.floor(center.y) + 6;
		range = s.range;
		wall = range + 4;
		cap = s.cap;
		walled = s.wall;
		dead = false;
		wasDead = false;
		respawnWait = -1;
		shownArmor = 0;
		shownToughness = 0;
		wave = s.startWave - 1;
		kills = 0;
		count = 0;
		alive = 0;
		timer = 0;
		oob = 0;
		provoke = 0;
		breakTicks = FIRST_BREAK_TICKS;
		active = true;

		bar.setName(Component.literal("GET READY").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		bar.setProgress(1);
		bar.setVisible(true);
		title(Component.literal("CYBERGRIND").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
			Component.literal("Survive.").withStyle(ChatFormatting.GRAY), 5, 40, 10);
	}

	public void stop(final ServerPlayer player) {
		if (!active) {
			player.sendSystemMessage(prefix().append(Component.literal("No run going.").withStyle(ChatFormatting.RED)));
			return;
		}
		broadcast(prefix().append(Component.literal("Run stopped.").withStyle(ChatFormatting.GRAY)));
		end(End.STOPPED);
	}

	/** Removes this wave's enemies: the run goes on to the break and the next wave. */
	public void skip(final ServerPlayer player) {
		if (!active) {
			player.sendSystemMessage(prefix().append(Component.literal("No run going.").withStyle(ChatFormatting.RED)));
			return;
		}
		mobs().forEach(Entity::discard);
	}

	/** An enemy of a Cybergrind run (the mixins and the loot event ask). */
	public static boolean isArenaMob(final Entity e) {
		return e.entityTags().contains(Spawns.TAG);
	}

	/** For the loot event: an arena enemy whose loot is dropped while the menu's Mob Drops is off. */
	public static boolean dropsOff(final Entity killed) {
		return !Settings.get().drops && isArenaMob(killed);
	}

	// ---- the tick ----

	public void tick() {
		if (!active) {
			return;
		}

		List<Entity> mobs = mobs();
		alive = mobs.size();
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		players.forEach(bar::addPlayer);

		// a death: Steve's (the death screen) or V1's (in ULTRAKILL; the Minecraft player stays alive then)
		dead = V1.dead() || players.stream().anyMatch(LivingEntity::isDeadOrDying);
		if (dead && Settings.get().endOnDeath) {
			end(End.DIED);
			return;
		}
		if (dead) {
			wasDead = true;
		} else if (wasDead) {
			// respawned: back to the arena a second later (after the ULTRAKILL mod has put V1 at the respawn point)
			wasDead = false;
			respawnWait = 20;
		}
		// no poison, slowness, weakness, wither, darkness and the like during a run (Harming still hurts: it's instant)
		players.forEach(Arena::clearHarmfulEffects);

		if (respawnWait > 0 && --respawnWait == 0) {
			respawnWait = -1;
			bringBack(players);
		}

		abilities.tick(mobs, players);

		if (breakTicks == -1) {
			bar.setProgress(count == 0 ? 0 : Math.min(1, alive / (float) count));
			if (alive == 0) {
				waveCleared();
			}
		} else if (breakTicks > 0 && --breakTicks == 0) {
			nextWave();
		}

		if (++timer == 10) {
			if (walled) {
				ring();
			}
			boolean outlined = outlinesWanted();
			for (Entity e : mobs) {
				if (e instanceof LivingEntity le) {
					outline(le, outlined);
					// a wither skull's Wither effect would still kill other enemies after the hit itself was blocked
					if (le.hasEffect(MobEffects.WITHER)) {
						le.removeEffect(MobEffects.WITHER);
					}
				}
			}
		}
		if (timer >= 20) {
			timer = 0;
			players.forEach(this::giveArmor);
			if (walled) {
				ring();
				second(mobs, players);
			} else {
				follow(mobs, players);
			}
			provokeBosses(mobs);
		}
	}

	/** Without the wall: enemies more than 40 blocks away (sideways) come back around the nearest player. */
	private void follow(final List<Entity> mobs, final List<ServerPlayer> players) {
		for (Entity e : mobs) {
			ServerPlayer nearest = null;
			double best2 = Double.MAX_VALUE;
			for (ServerPlayer p : players) {
				if (p.level() == e.level() && !p.isDeadOrDying()) {
					double d2 = flat2(e, p.position());
					if (d2 < best2) {
						best2 = d2;
						nearest = p;
					}
				}
			}
			int limit = e instanceof Ghast ? GHAST_FOLLOW_DISTANCE : FOLLOW_DISTANCE;
			if (nearest != null && best2 > (double) limit * limit) {
				place(e, nearest.position(), (int) Math.floor(nearest.getY()) + 6);
			}
		}
	}

	/** With "End Run on Death" off: a respawned player comes back to the arena's centre (without the wall, enemies come to them). */
	private void bringBack(final List<ServerPlayer> players) {
		if (!walled) {
			return;
		}
		for (ServerPlayer p : players) {
			if (!p.isDeadOrDying()) {
				p.teleportTo(level, center.x, center.y, center.z, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
			}
		}
		oob = 0;
	}

	private void second(final List<Entity> mobs, final List<ServerPlayer> players) {
		// outside the wall: 5 seconds to come back. Not while someone is dead or about to be brought back.
		double wall2 = (double) wall * wall;
		if (dead || wasDead || respawnWait > 0) {
			oob = 0;
		} else {
			boolean inside = players.stream().anyMatch(p -> p.level() == level && flat2(p, center) <= wall2);
			oob = inside ? 0 : oob + 1;
			if (oob >= OOB_SECONDS) {
				end(End.LEFT);
				return;
			}
			if (oob > 0) {
				Component warn = Component.literal("BACK INTO THE ARENA! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
					.append(Component.literal(oob + "/" + OOB_SECONDS).withStyle(ChatFormatting.WHITE));
				players.forEach(p -> p.sendOverlayMessage(warn));
			}
		}

		// enemies that got past the wall come back in
		for (Entity e : mobs) {
			double limit = e instanceof Ghast ? (double) (wall + GHAST_WALL_SLACK) * (wall + GHAST_WALL_SLACK) : wall2;
			if (flat2(e, center) > limit) {
				place(e, center, maxY);
			}
		}
	}

	/** Every 10 s, golems and wardens are reminded who they're after. */
	private void provokeBosses(final List<Entity> mobs) {
		if (active && ++provoke >= 10) {
			provoke = 0;
			for (Entity e : mobs) {
				// (wardens are kept angry by Abilities instead: a hit only angers them for a second or two)
				if (e instanceof IronGolem && e instanceof LivingEntity le) {
					provoke(le);
				}
			}
		}
	}

	// ---- waves ----

	private void nextWave() {
		// without the wall the arena is wherever you are: each wave spawns around a living player
		if (!walled) {
			server.getPlayerList().getPlayers().stream().filter(p -> !p.isDeadOrDying()).findFirst().ifPresent(p -> {
				level = p.level();
				center = p.position();
				maxY = (int) Math.floor(center.y) + 6;
			});
		}

		// dropped items from earlier waves go (less lag)
		level.getEntities(EntityTypeTest.forClass(ItemEntity.class), e -> e.distanceToSqr(center) <= ITEM_CLEAR_RADIUS * ITEM_CLEAR_RADIUS)
			.forEach(Entity::discard);
		clearVexes();

		wave++;
		breakTicks = -1;
		server.getPlayerList().getPlayers().forEach(this::giveArmor);
		announceArmor();
		count = 0;
		boolean big = wave % 5 == 0;
		int budget = 3 + 2 * wave;
		if (big) {
			budget = budget * 3 / 2;
		}
		int maxTier = 0;
		while (maxTier < Spawns.UNLOCK.length && Spawns.UNLOCK[maxTier] <= wave) {
			maxTier++;
		}

		CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPosition(center).withSuppressedOutput();
		while (budget > 0 && count < cap) {
			// the lower of two rolls, so cheap enemies stay common
			int tier = Math.min(random.nextInt(maxTier), random.nextInt(maxTier));
			if (Spawns.COST[tier] > budget) {
				tier = 0;
			}
			budget -= Spawns.COST[tier];
			count++;
			List<Spawns.Enemy> pool = Spawns.TIERS.get(tier);
			server.getCommands().performPrefixedCommand(source, pool.get(random.nextInt(pool.size())).command());
		}

		int bossType = -1;
		if (big && wave >= 10) {
			int k = wave / 5 - 2;
			bossType = k % 3;
			int bosses = 1 + k / 3;
			for (int i = 0; i < bosses; i++) {
				server.getCommands().performPrefixedCommand(source, Spawns.bossCommand(Spawns.BOSSES.get(bossType)));
				count++;
			}
		}

		alive = count;
		adoptNew(level, center, maxY);

		Component name = Component.literal("WAVE " + wave).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
		bar.setName(name);
		bar.setProgress(1);
		Component sub;
		if (bossType >= 0) {
			sub = Component.literal("BOSS: " + Spawns.BOSS_NAMES.get(bossType)).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
		} else if (big) {
			sub = Component.literal("Big wave").withStyle(ChatFormatting.GOLD);
		} else {
			sub = Component.literal(count + " enemies").withStyle(ChatFormatting.GRAY);
		}
		title(name, sub, 5, 30, 10);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN, SoundSource.HOSTILE, 2, 1);
		}
	}

	private void waveCleared() {
		breakTicks = BREAK_TICKS;
		kills += count;
		count = 0;
		if (wave > best) {
			best = wave;
			writeBest();
		}
		bar.setName(Component.literal("WAVE CLEARED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		bar.setProgress(1);
		title(Component.literal("WAVE CLEARED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
			Component.literal("Next wave in 5 seconds").withStyle(ChatFormatting.GRAY), 5, 30, 10);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2, false, false));
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1, 1);
		}
	}

	private void end(final End why) {
		if (breakTicks == -1) {
			kills += Math.max(0, count - alive);
		}
		count = 0;
		active = false;
		server.getPlayerList().getPlayers().forEach(Arena::takeArmor);
		bar.setVisible(false);
		bar.removeAllPlayers();
		if (why == End.DIED) {
			title(Component.literal("YOU DIED").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.literal("Wave " + wave), 5, 50, 20);
		} else if (why == End.LEFT) {
			title(Component.literal("RUN OVER").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.literal("You left the arena (or V1 died and respawned)").withStyle(ChatFormatting.GRAY), 5, 50, 20);
		}
		broadcast(prefix().append(Component.literal("Reached wave ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(String.valueOf(wave)).withStyle(ChatFormatting.GOLD))
			.append(Component.literal(" | Kills: ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(String.valueOf(kills)).withStyle(ChatFormatting.GOLD))
			.append(Component.literal(" | Best: wave ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(String.valueOf(best)).withStyle(ChatFormatting.AQUA)));
		cleanup();
	}

	/** Removes every arena enemy, and the datapack version's leftovers (its centre marker). No loot: they're discarded. */
	/** Vexes the evokers summoned (they aren't arena enemies themselves, so they'd outlive the wave). */
	private void clearVexes() {
		if (level != null && center != null) {
			level.getEntities(EntityTypeTest.forClass(Vex.class), v -> flat2(v, center) <= ITEM_CLEAR_RADIUS * ITEM_CLEAR_RADIUS)
				.forEach(Entity::discard);
		}
	}

	/** Wither at half health: 3 wither skeletons join the wave (on the ground near it, at least 8 blocks from you). */
	void spawnMinions(final Entity wither) {
		ServerLevel l = (ServerLevel) wither.level();
		CommandSourceStack source = server.createCommandSourceStack().withLevel(l).withPosition(wither.position()).withSuppressedOutput();
		for (int i = 0; i < 3; i++) {
			server.getCommands().performPrefixedCommand(source, Spawns.WITHER_MINION.command());
			count++;
		}
		adoptNew(l, wither.position(), (int) Math.floor(wither.getY()) + 6);
		Component call = Component.literal("THE WITHER CALLS FOR HELP").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
		server.getPlayerList().getPlayers().forEach(p -> p.sendOverlayMessage(call));
		l.playSound(null, wither.getX(), wither.getY(), wither.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3, 0.6F);
	}

	/** Just-summoned enemies (tagged new): placed around {@code around} and set up (glow, team, buffs). */
	private void adoptNew(final ServerLevel l, final Vec3 around, final int topY) {
		for (Entity e : l.getEntities(EntityTypeTest.forClass(Entity.class), e -> e.entityTags().contains(Spawns.NEW))) {
			e.removeTag(Spawns.NEW);
			place(e, around, topY);
			setup(e);
		}
	}

	private void cleanup() {
		abilities.clear();
		clearVexes();
		for (ServerLevel l : server.getAllLevels()) {
			l.getEntities(EntityTypeTest.forClass(Entity.class),
				e -> e.entityTags().contains(Spawns.TAG) || e.entityTags().contains(CENTER_TAG)).forEach(Entity::discard);
		}
		alive = 0;
	}

	private List<Entity> mobs() {
		return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Entity.class), e -> e.isAlive() && e.entityTags().contains(Spawns.TAG)));
	}

	// ---- enemies ----

	/**
	 * A random spot within the arena radius of {@code around}: standing on ground, or swimming on water, never in lava or mid-air,
	 * and at least 8 blocks (sideways) from every player. Searched from {@code topY} down to 96 blocks below it, so a player high
	 * up (a pillar, a big jump) gets their enemies on the ground under them, not in the air next to them.
	 */
	private void place(final Entity e, final Vec3 around, final int topY) {
		if (e instanceof Ghast) {
			placeInSky(e, around, topY);
		} else {
			placeOnGround(e, around, topY);
		}
	}

	/**
	 * Ghasts: 12 blocks above the open-sky surface (never in a cave or under a roof), in a spot their big body fits, at least 8
	 * blocks sideways from every player. In a world with a ceiling (the Nether) there's no sky: 10 above the ground there instead.
	 */
	private void placeInSky(final Entity e, final Vec3 around, final int topY) {
		ServerLevel l = (ServerLevel) e.level();
		if (l.dimensionType().hasCeiling()) {
			placeOnGround(e, around, topY);
			e.snapTo(e.getX(), e.getY() + 10, e.getZ(), e.getYRot(), 0);
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = Math.max(MIN_PLAYER_DISTANCE, Math.sqrt(random.nextDouble()) * range);
			double x = around.x + Math.cos(angle) * r;
			double z = around.z + Math.sin(angle) * r;
			if (nearPlayer(players, l, x, z)) {
				continue;
			}
			int surface = l.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
			double y = Math.max(surface, around.y) + 12;
			if (l.noCollision(e, e.getType().getSpawnAABB(x, y, z))) {
				e.snapTo(x, y, z, random.nextFloat() * 360, 0);
				return;
			}
		}
		int surface = l.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(around.x), (int) Math.floor(around.z));
		e.snapTo(around.x + range, Math.max(surface, around.y) + 16, around.z, random.nextFloat() * 360, 0);
	}

	private void placeOnGround(final Entity e, final Vec3 around, final int topY) {
		ServerLevel l = (ServerLevel) e.level();
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		int bottom = Math.max(l.getMinY() + 1, topY - 96);
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = Math.max(2, Math.sqrt(random.nextDouble()) * range);
			double x = around.x + Math.cos(angle) * r;
			double z = around.z + Math.sin(angle) * r;
			if (nearPlayer(players, l, x, z)) {
				continue;
			}
			for (int y = topY; y >= bottom; y--) {
				BlockPos feet = BlockPos.containing(x, y, z);
				if (standable(l, feet)) {
					e.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, random.nextFloat() * 360, 0);
					return;
				}
			}
		}

		// nowhere found: the terrain's own surface at the edge of the radius (still not mid-air, not on top of the player)
		double angle = random.nextDouble() * Math.PI * 2;
		int x = (int) Math.floor(around.x + Math.cos(angle) * range);
		int z = (int) Math.floor(around.z + Math.sin(angle) * range);
		int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		e.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
	}

	/** Feet and head free, and either solid ground under the feet or water at the feet with air above it. No lava. */
	static boolean standable(final ServerLevel l, final BlockPos feet) {
		BlockPos below = feet.below();
		BlockPos head = feet.above();
		if (!l.getBlockState(head).getCollisionShape(l, head).isEmpty() || l.getFluidState(head).is(FluidTags.LAVA)) {
			return false;
		}
		boolean ground = l.getBlockState(below).isFaceSturdy(l, below, Direction.UP)
			&& l.getBlockState(feet).getCollisionShape(l, feet).isEmpty()
			&& !l.getFluidState(feet).is(FluidTags.LAVA);
		boolean waterSurface = l.getFluidState(feet).is(FluidTags.WATER) && !l.getFluidState(head).is(FluidTags.WATER);
		return ground || waterSurface;
	}

	private static boolean nearPlayer(final List<ServerPlayer> players, final ServerLevel l, final double x, final double z) {
		for (ServerPlayer p : players) {
			double dx = p.getX() - x;
			double dz = p.getZ() - z;
			if (p.level() == l && dx * dx + dz * dz < MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE) {
				return true;
			}
		}
		return false;
	}

	/** Distance squared, sideways only: height doesn't count (a player up high isn't "far" from what's below them). */
	static double flat2(final Entity e, final Vec3 v) {
		double dx = e.getX() - v.x;
		double dz = e.getZ() - v.z;
		return dx * dx + dz * dz;
	}

	private void setup(final Entity e) {
		if (!(e instanceof LivingEntity le)) {
			return;
		}
		boolean boss = e.entityTags().contains(Spawns.BOSS);
		boolean archer = e.entityTags().contains(Spawns.ARCHER);
		boolean heavy = e.entityTags().contains(Spawns.HEAVY);

		// notice you from across the arena; keep up with V1 (faster, higher jumps, walk up blocks, no fall damage from them)
		base(le, Attributes.FOLLOW_RANGE, 48);
		// (no daylight burning: MobMixin.) Archers walk slower inside the wall; without it they get the bonus too, to keep up
		if (!boss && (!archer || !walled)) {
			modifier(le, Attributes.MOVEMENT_SPEED, SPEED, Math.min(50 + 3 * wave, 120) / 100.0);
		}
		// V1 jumps 6-8 blocks: enemies jump about 6 (+140%), Juggernauts about 8 (+170%)
		modifier(le, Attributes.JUMP_STRENGTH, JUMP, heavy ? 1.7 : 1.4);
		base(le, Attributes.STEP_HEIGHT, 1);
		base(le, Attributes.SAFE_FALL_DISTANCE, e instanceof IronGolem ? 40 : 20);

		// radiant from wave 10: +3% chance per wave, up to 40%; tougher and faster, but no extra damage
		int radiantChance = Math.clamp((wave - 9) * 3L, 0, 40);
		boolean radiant = !boss && wave >= 10 && random.nextInt(100) < radiantChance;
		if (radiant) {
			effect(le, MobEffects.RESISTANCE);
			effect(le, MobEffects.SPEED);
		}

		// outlined (through walls) per the menu's Enemy Outlines: red, gold for radiant, purple for bosses
		outline(le, outlinesWanted());
		if (boss) {
			team(e, "cg_boss", TeamColor.DARK_PURPLE);
		} else if (radiant) {
			team(e, "cg_radiant", TeamColor.GOLD);
		} else {
			team(e, "cg_enemy", TeamColor.RED);
		}

		if (e instanceof IronGolem) {
			provoke(le);
		}
	}

	/** Golems and wardens only fight back: the boss "gets hit" by the nearest player, so it hunts them. */
	private void provoke(final LivingEntity boss) {
		Player target = level.getNearestPlayer(boss, 128);
		if (target != null) {
			boss.hurtServer(level, level.damageSources().playerAttack(target), 1);
		}
	}

	/** The run's armor: attribute bonuses (not items), shown on the armor bar, never saved with the player, gone when the run ends. */
	private void giveArmor(final ServerPlayer p) {
		bonus(p, Attributes.ARMOR, ARMOR, armorFor(wave));
		bonus(p, Attributes.ARMOR_TOUGHNESS, TOUGHNESS, toughnessFor(wave));
	}

	/** At the bottom of the screen, when a wave raises the run's armor or toughness. */
	private void announceArmor() {
		int armor = armorFor(wave);
		int toughness = toughnessFor(wave);
		if (armor <= shownArmor && toughness <= shownToughness) {
			return;
		}
		boolean maxed = armor == armorFor(Integer.MAX_VALUE) && toughness == toughnessFor(Integer.MAX_VALUE);
		MutableComponent message = Component.literal(maxed ? "ARMOR MAXED OUT  " : "ARMOR UPGRADED  ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			.append(Component.literal("Armor " + armor).withStyle(ChatFormatting.WHITE))
			.append(gain(armor - shownArmor))
			.append(Component.literal(" · Toughness " + toughness).withStyle(ChatFormatting.WHITE))
			.append(gain(toughness - shownToughness));
		shownArmor = armor;
		shownToughness = toughness;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendOverlayMessage(message);
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), maxed ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.ARMOR_EQUIP_IRON,
				SoundSource.PLAYERS, 1, 1);
		}
	}

	private static Component gain(final int amount) {
		return amount > 0 ? Component.literal(" (+" + amount + ")").withStyle(ChatFormatting.GREEN) : Component.empty();
	}

	private static void takeArmor(final ServerPlayer p) {
		AttributeInstance armor = p.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(ARMOR);
		}
		AttributeInstance toughness = p.getAttribute(Attributes.ARMOR_TOUGHNESS);
		if (toughness != null) {
			toughness.removeModifier(TOUGHNESS);
		}
	}

	private static void bonus(final LivingEntity e, final Holder<Attribute> attribute, final Identifier id, final double amount) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Enemy Outlines: always, only for a wave's last 5 enemies, or never. */
	private boolean outlinesWanted() {
		return switch (Settings.get().outlines) {
			case ALWAYS -> true;
			case LAST_FIVE -> breakTicks == -1 && alive <= 5;
			case OFF -> false;
		};
	}

	/** Glowing on or off. */
	private static void outline(final LivingEntity e, final boolean on) {
		if (on && !e.hasEffect(MobEffects.GLOWING)) {
			effect(e, MobEffects.GLOWING);
		} else if (!on && e.hasEffect(MobEffects.GLOWING)) {
			e.removeEffect(MobEffects.GLOWING);
		}
	}

	private static void clearHarmfulEffects(final ServerPlayer p) {
		List<Holder<MobEffect>> bad = new ArrayList<>();
		for (MobEffectInstance i : p.getActiveEffects()) {
			if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				bad.add(i.getEffect());
			}
		}
		bad.forEach(p::removeEffect);
	}

	private static void base(final LivingEntity e, final Holder<Attribute> attribute, final double value) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.setBaseValue(value);
		}
	}

	private static void modifier(final LivingEntity e, final Holder<Attribute> attribute, final Identifier id, final double amount) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	private static void effect(final LivingEntity e, final Holder<MobEffect> effect) {
		e.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false));
	}

	private void team(final Entity e, final String name, final TeamColor color) {
		Scoreboard scoreboard = server.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(name);
		if (team == null) {
			team = scoreboard.addPlayerTeam(name);
		}
		team.setColor(Optional.of(color));
		scoreboard.addPlayerToTeam(e.getScoreboardName(), team);
	}

	// ---- display ----

	/** The wall: a thick band of red dust at the arena's edge, one point every 2 degrees. */
	private void ring() {
		DustParticleOptions dust = new DustParticleOptions(0xFF1A1A, 4.0F);
		for (int i = 0; i < 180; i++) {
			double angle = Math.toRadians(i * 2);
			level.sendParticles(dust, true, true, center.x + Math.cos(angle) * wall, center.y + 1.5, center.z + Math.sin(angle) * wall,
				5, 0.2, 1.3, 0.2, 0);
		}
	}

	private void title(final Component title, final Component subtitle, final int in, final int stay, final int out) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
			p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	private void broadcast(final Component message) {
		server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(message));
	}

	private static MutableComponent prefix() {
		return Component.literal("[Cybergrind] ").withStyle(ChatFormatting.RED);
	}

	// ---- best wave, kept per world ----

	private Path bestFile() {
		return server.getWorldPath(LevelResource.ROOT).resolve("cybergrind.json");
	}

	private int readBest() {
		try {
			Path file = bestFile();
			if (Files.exists(file)) {
				JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				return o.has("best") ? o.get("best").getAsInt() : 0;
			}
		} catch (IOException | RuntimeException e) {
			Cybergrind.LOG.warn("Couldn't read the best wave", e);
		}
		return 0;
	}

	private void writeBest() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("best", best);
			Files.writeString(bestFile(), o.toString());
		} catch (IOException e) {
			Cybergrind.LOG.warn("Couldn't save the best wave", e);
		}
	}
}
