package dev.cybergrind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.cybergrind.mixin.LargeFireballAccessor;
import dev.cybergrind.mixin.MobAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import dev.cybergrind.mixin.PrimedTntAccessor;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * Enemy abilities, run every tick of a run: the Warden hunts you and slams the ground, the Iron Golem fights like Cerberus
 * (orb, tackle, stomp; enraged when another dies), Drones hover, strafe, fire bolts and dive when killed, the Ravager
 * dashes at you, the Wither calls 3 wither skeletons at half health, Ghasts keep throwing fireballs, Volley skeletons and
 * Gunner pillagers keep firing arrows, Phantom spiders blink behind you and spit webs, and Providences fly and fire their beams.
 * Server thread.
 */
final class Abilities {
	private static final int SLAM_COOLDOWN = 160;
	private static final int BREEZE_COOLDOWN = 25;
	private static final double BREEZE_RANGE = 24;
	private static final int BLAZE_COOLDOWN = 20;
	private static final int BLAZE_MOVE_TICKS = 40;
	private static final double BLAZE_SPEED = 0.6;
	private static final double BLAZE_RANGE = 30;
	private static final int TNT_COOLDOWN = 60;
	private static final double TNT_MIN = 5;
	private static final double TNT_MAX = 20;
	private static final int TNT_FUSE = 30;
	private static final float TNT_POWER = 2;
	private static final double TNT_FLIGHT_TICKS = 20;
	private static final double VEX_BOOST = 1.08;
	private static final double VEX_MAX_SPEED = 0.9;
	private static final int SONIC_COOLDOWN = 100;
	private static final int SONIC_CHARGE_TICKS = 30;
	private static final double SONIC_MIN = 5;
	private static final double SONIC_MAX = 28;
	private static final double SONIC_SPEED = 1.0;
	private static final double SONIC_RANGE = 32;
	private static final double SONIC_RADIUS = 1.6;
	private static final float SONIC_DAMAGE = 10;
	private static final double SLAM_RADIUS = 6;
	private static final float SLAM_DAMAGE = 5;
	/** Ticks the slam's disc spreads before it lands (0.7 s). */
	private static final int SLAM_WINDUP = 14;
	private static final BlockState SLAM_DISC_BLOCK = Blocks.STAINED_GLASS.cyan().defaultBlockState();
	private static final BlockState SLAM_FLASH_BLOCK = Blocks.STAINED_GLASS.white().defaultBlockState();
	/** No outline: the ring shows on the ground, not through walls. */
	private static final int SLAM_DISC_GLOW = 0;
	private static final int SLAM_RING_SEGMENTS = 16;
	private static final int DASH_COOLDOWN = 80;
	private static final double DASH_MIN_DISTANCE = 5;
	private static final double DASH_MAX_DISTANCE = 20;
	private static final int FIREBALL_COOLDOWN = 30;
	private static final double WITHER_SPACING = 12;
	private static final int BLINK_COOLDOWN = 100;
	private static final double BLINK_MIN_DISTANCE = 5;
	private static final double BLINK_MAX_DISTANCE = 24;
	private static final double BLINK_BEHIND = 3;
	private static final double FIREBALL_RANGE = 64;
	private static final int FIREBALL_POWER = 2;
	/** Vanilla 0.1: the push a fireball gets each tick (top speed ~19x this, in blocks a tick). */
	private static final double FIREBALL_ACCELERATION = 0.14;
	private static final double FIREBALL_START_SPEED = 1.2;
	private static final String BOOSTED = "cg_boosted";
	private static final double GHAST_HOVER_DISTANCE = 10;
	private static final double GHAST_HOVER_HEIGHT = 7;
	private static final int ARROW_COOLDOWN = 15;
	private static final double ARROW_RANGE = 40;

	private static final int WEB_COOLDOWN = 80;
	private static final double WEB_MIN_DISTANCE = 4;
	private static final double WEB_MAX_DISTANCE = 18;
	/** Blocks a tick a web flies: you can step out of the way. */
	private static final double WEB_SPEED = 1.2;
	private static final int WEB_LIFETIME = 100;
	private static final int PROVIDENCE_DASH_TICKS = 14;
	private static final int PROVIDENCE_CHARGE_TICKS = 20;
	/** The aim stops following you this long before the beams fire (0.3 s). */
	private static final int PROVIDENCE_LOCK_TICKS = 6;
	private static final int PROVIDENCE_CROSS_TICKS = 16;
	private static final int PROVIDENCE_SPIRAL_TICKS = 30;
	private static final float PROVIDENCE_BEAM_DAMAGE = 6;
	private static final int PROVIDENCE_HIT_COOLDOWN = 10;
	private static final double CROSS_ARM = 14;
	private static final double SPIRAL_START = 6;
	private static final double BEAM_WIDTH = 0.8;
	private static final int HEAL_ORB_LIFETIME = 400;
	private static final DustParticleOptions MAGENTA = new DustParticleOptions(0xFF33FF, 1.6F);
	/** The heal orb: a pink glass cube with a red heart inside (no outline), drifting down this much a tick. */
	private static final BlockState ORB_BLOCK = Blocks.STAINED_GLASS.pink().defaultBlockState();
	private static final int ORB_GLOW = 0;
	private static final float HEART_SCALE = 2.2F;
	private static final float ORB_SIZE = 0.7F;
	private static final double ORB_FALL = 0.04;
	/** The beams are thin and see-through (stained glass, no outline): clear, but not slabs in your face. */
	private static final BlockState CHARGE_BLOCK = Blocks.STAINED_GLASS.magenta().defaultBlockState();
	private static final BlockState LOCKED_BLOCK = Blocks.STAINED_GLASS.white().defaultBlockState();
	private static final BlockState BEAM_BLOCK = Blocks.STAINED_GLASS.magenta().defaultBlockState();
	private static final int BEAM_GLOW = 0;
	private static final float BEAM_DRAW_WIDTH = 0.22F;
	/** Arena witches' lingering clouds last this long (vanilla 30 s). */
	private static final int WITCH_CLOUD_TICKS = 100;
	/** Bow users' arrows fly this much faster (with damage brought back down to match). */
	private static final double ARROW_SPEED_FACTOR = 1.35;
	/** Vanilla 3, and shrinking; these stay 5 wide the whole time, in bright red. */
	private static final float WITCH_CLOUD_RADIUS = 5;
	private static final DustParticleOptions BOLT_COLOR = new DustParticleOptions(0xFF7A1A, 1.3F);
	private static final DustParticleOptions ORB_COLOR = new DustParticleOptions(0xFFB020, 2.2F);
	private static final String DIVING = "cg_diving";
	private static final double DIVE_SPEED = 0.8;
	private static final double DRONE_FAR = 14;
	private static final double DRONE_NEAR = 8;
	private static final int DRONE_CHARGE_TICKS = 15;
	private static final double BOLT_SPEED = 1.6;
	private static final int DIVE_TICKS = 60;
	private static final int DRONE_SHOTS = 3;
	/** Ticks between a Drone's shots (semi-auto). */
	private static final int DRONE_SHOT_GAP = 6;
	private static final float DIVE_POWER = 2;
	private static final double STOMP_RANGE = 4.5;
	private static final double DASH_RANGE = 12;
	private static final double ORB_MIN_RANGE = 12;
	private static final double ORB_SPEED = 1.2;
	private static final double ORB_HOMING_RANGE = 16;
	private static final float ORB_POWER = 1.5F;
	private static final float TACKLE_DAMAGE = 7;
	private static final float SHOCKWAVE_DAMAGE = 6;
	private static final double SHOCKWAVE_SPEED = 0.6;
	private static final double SHOCKWAVE_RADIUS = 9;
	private static final String ENRAGED = "cg_enraged";

	private final Arena arena;
	/** The tick each enemy may use its ability again. */
	private final Map<UUID, Integer> readyAt = new HashMap<>();
	/** The same for Phantom webs (a Phantom has two abilities). */
	private final Map<UUID, Integer> webReadyAt = new HashMap<>();
	private final List<FlyingWeb> flyingWebs = new ArrayList<>();
	private final List<PlacedWeb> placedWebs = new ArrayList<>();
	private final Map<UUID, Providence> providences = new HashMap<>();
	/** Heal orbs left by dead Providences (the death event adds them; they're ticked here). */
	private static final List<HealOrb> HEAL_ORBS = new ArrayList<>();

	private record FlyingWeb(ServerLevel level, Vec3 from, Vec3 to, int fired, int lands) {
	}

	private record PlacedWeb(ServerLevel level, BlockPos pos, int melts) {
	}

	/** A heal orb: a glowing pink cube (a block display) that floats slowly down to the ground, and waits there to be touched. */
	private static final class HealOrb {
		final ServerLevel level;
		final int expires;
		Vec3 pos;
		Display.BlockDisplay cube;
		Display.TextDisplay heart;

		HealOrb(final ServerLevel level, final Vec3 pos, final int expires) {
			this.level = level;
			this.pos = pos;
			this.expires = expires;
		}

		void remove() {
			if (cube != null) {
				cube.discard();
				cube = null;
			}
			if (heart != null) {
				heart.discard();
				heart = null;
			}
		}
	}

	/** A Providence's fight: dashing (twice), charging, then firing; the attack and where it was aimed. */
	private static final class Providence {
		int phase;
		int timer;
		int dashes;
		boolean spiral;
		Vec3 dashTo;
		Vec3 aim;
		/** Where it will fire, fixed 0.3 s before it does (null while it's still tracking you). */
		Vec3 locked;
		final Map<UUID, Integer> hitAt = new HashMap<>();
		/** Its visuals (block displays): the charging core, the aiming beam, the firing beams. */
		Display.BlockDisplay core;
		Display.BlockDisplay aimBeam;
		final List<Display.BlockDisplay> beams = new ArrayList<>();
		/** Seen this tick (a Providence that's gone takes its visuals with it). */
		boolean seen;

		void clearFx() {
			if (core != null) {
				core.discard();
				core = null;
			}
			if (aimBeam != null) {
				aimBeam.discard();
				aimBeam = null;
			}
			beams.forEach(Entity::discard);
			beams.clear();
		}
	}

	private static final int DASHING = 0;
	private static final int CHARGING = 1;
	private static final int FIRING = 2;
	/** Withers that have called their minions. */
	private final Set<UUID> minionsCalled = new HashSet<>();
	private int tick;

	Abilities(final Arena arena) {
		this.arena = arena;
	}

	void clear() {
		readyAt.clear();
		webReadyAt.clear();
		minionsCalled.clear();
		providences.values().forEach(Providence::clearFx);
		providences.clear();
		flyingWebs.clear();
		for (PlacedWeb web : placedWebs) {
			melt(web);
		}
		placedWebs.clear();
		HEAL_ORBS.forEach(HealOrb::remove);
		HEAL_ORBS.clear();
		orbs.clear();
		blazeSpots.clear();
		VEXES.clear();
		shockwaves.clear();
		sonicBooms.clear();
		sonicCharge.clear();
		slamWindup.clear();
		slamDiscs.values().forEach(ring -> ring.forEach(Entity::discard));
		slamDiscs.clear();
		slamDiscFade.clear();
		sonicReadyAt.clear();
		drones.clear();
		cerberi.clear();
	}

	void tick(final List<Entity> mobs, final List<ServerPlayer> players) {
		tick++;
		providences.values().forEach(p -> p.seen = false);
		spreadWithers(mobs);
		tickWebs();
		tickHealOrbs(players);
		steerOrbs(players);
		speedVexes();
		tickShockwaves(players);
		tickSonicBooms(players);
		for (Entity e : mobs) {
			// enemies only ever fight you: a target that's another arena enemy (a golem going for a skeleton, a mob turning on
			// whoever hit it by accident) is swapped for the nearest player
			if (e instanceof Mob mob && mob.getTarget() != null && Arena.isArenaMob(mob.getTarget())) {
				mob.setTarget(nearest(e, players));
			}
			if (e instanceof Mob providence && e.entityTags().contains(Spawns.PROVIDENCE)) {
				providence(providence, players);
			} else if (e instanceof Mob drone && e.entityTags().contains(Spawns.DRONE)) {
				drone(drone, players);
			} else if (e instanceof Warden warden) {
				ServerPlayer target = nearest(e, players);
				if (target != null) {
					if (tick % 10 == 0) {
						// a hit only angers a warden for a second or two: keep it at full anger on the nearest player. Its target is
						// only set when it changes (setting it resets the warden's attack), and it's walked to you
						warden.increaseAngerAt(target, 150, false);
						if (warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) != target) {
							warden.setAttackTarget(target);
						}
						if (!sonicCharge.containsKey(warden.getUUID()) && warden.distanceTo(target) > 3) {
							warden.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 1.3F, 2));
						}
					}
					// its own instant sonic boom stays off: this one travels
					warden.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_COOLDOWN, Unit.INSTANCE, 40);
					sonicBoom(warden, target, level(warden));
				}
				slam(warden, players);
			} else if (e instanceof IronGolem golem) {
				ServerPlayer target = nearest(e, players);
				if (target != null && tick % 10 == 0 && golem.getTarget() != target) {
					// golems only fight back on their own: keep it on the nearest player
					golem.setTarget(target);
				}
				cerberus(golem, target);
			} else if (e instanceof Ravager ravager) {
				dash(ravager, nearest(e, players));
			} else if (e instanceof Ghast ghast) {
				ServerPlayer target = nearest(e, players);
				chase(ghast, target);
				fireball(ghast, target);
			} else if (e instanceof Mob phantom && e.entityTags().contains(Spawns.BLINK)) {
				ServerPlayer target = nearest(e, players);
				blink(phantom, target);
				spitWeb(phantom, target);
			} else if (e instanceof Mob shooter && e.entityTags().contains(Spawns.RAPID)) {
				arrow(shooter, nearest(e, players));
			} else if (e instanceof Breeze breeze) {
				windCharge(breeze, nearest(e, players));
			} else if (e instanceof Blaze blaze) {
				blaze(blaze, nearest(e, players));
			} else if (e instanceof Creeper creeper) {
				lobTnt(creeper, nearest(e, players));
			} else if (e instanceof WitherBoss wither && wither.getHealth() <= wither.getMaxHealth() / 2 && minionsCalled.add(wither.getUUID())) {
				arena.spawnMinions(wither);
			}
		}
		// a Warden that died or went away takes its slam ring with it
		Set<UUID> wardens = new HashSet<>();
		for (Entity e : mobs) {
			if (e instanceof Warden) {
				wardens.add(e.getUUID());
			}
		}
		slamDiscs.entrySet().removeIf(ring -> {
			if (wardens.contains(ring.getKey())) {
				return false;
			}
			ring.getValue().forEach(Entity::discard);
			slamWindup.remove(ring.getKey());
			slamDiscFade.remove(ring.getKey());
			return true;
		});
		// a Providence that died or went away takes its beams with it
		providences.values().removeIf(pr -> {
			if (!pr.seen) {
				pr.clearFx();
			}
			return !pr.seen;
		});
	}

	/**
	 * Withers all fly at the same player along the same path, so several end up in one clump. Any two within 12 blocks of each
	 * other are pushed apart a little every tick (stronger the closer they are), so each flies its own way.
	 */
	private static void spreadWithers(final List<Entity> mobs) {
		List<Entity> withers = mobs.stream().filter(e -> e instanceof WitherBoss).toList();
		for (int i = 0; i < withers.size(); i++) {
			for (int j = i + 1; j < withers.size(); j++) {
				Entity a = withers.get(i);
				Entity b = withers.get(j);
				double dx = a.getX() - b.getX();
				double dz = a.getZ() - b.getZ();
				double d2 = dx * dx + dz * dz;
				if (d2 >= WITHER_SPACING * WITHER_SPACING) {
					continue;
				}
				double d = Math.sqrt(d2);
				if (d < 0.01) {
					dx = 1;
					dz = 0;
					d = 1;
				}
				double push = 0.06 + 0.14 * (1 - d / WITHER_SPACING);
				a.setDeltaMovement(a.getDeltaMovement().add(dx / d * push, 0, dz / d * push));
				b.setDeltaMovement(b.getDeltaMovement().add(-dx / d * push, 0, -dz / d * push));
				a.needsSync = true;
				b.needsSync = true;
			}
		}
	}

	/** A flat ring of glowing segments on the ground, this wide. */
	private static void drawRing(final List<Display.BlockDisplay> ring, final Vec3 center, final double radius, final BlockState block, final int glow) {
		for (int i = 0; i < ring.size(); i++) {
			double a = Math.PI * 2 * i / ring.size();
			double b = Math.PI * 2 * (i + 1) / ring.size();
			Vec3 from = center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
			Vec3 to = center.add(Math.cos(b) * radius, 0, Math.sin(b) * radius);
			Beams.beam(ring.get(i), from, to, 0.18F, block, glow);
		}
	}

	/** Wardens winding up a slam: ticks left; and the disc showing where it will land (it stays a moment after, white). */
	private final Map<UUID, Integer> slamWindup = new HashMap<>();
	private final Map<UUID, List<Display.BlockDisplay>> slamDiscs = new HashMap<>();
	private final Map<UUID, Integer> slamDiscFade = new HashMap<>();

	/**
	 * Warden: every 8 s, when you're within 6 blocks, it stops and a glowing cyan ring spreads out on the ground around it for 0.7 s,
	 * to the slam's edge; then it slams: anyone still inside the ring (and within 3 blocks up or down) takes 5. The ring flashes
	 * white and fades.
	 */
	private void slam(final Warden warden, final List<ServerPlayer> players) {
		ServerLevel level = (ServerLevel) warden.level();
		UUID id = warden.getUUID();
		Integer fade = slamDiscFade.get(id);
		if (fade != null) {
			if (fade <= 0) {
				List<Display.BlockDisplay> ring = slamDiscs.remove(id);
				if (ring != null) {
					ring.forEach(Entity::discard);
				}
				slamDiscFade.remove(id);
			} else {
				slamDiscFade.put(id, fade - 1);
			}
		}

		Integer windup = slamWindup.get(id);
		if (windup == null) {
			boolean near = players.stream().anyMatch(p -> p.level() == level && !p.isDeadOrDying()
				&& Arena.flat2(p, warden.position()) <= SLAM_RADIUS * SLAM_RADIUS && Math.abs(p.getY() - warden.getY()) <= 3);
			if (!near || sonicCharge.containsKey(id) || !use(warden, SLAM_COOLDOWN)) {
				return;
			}
			slamWindup.put(id, SLAM_WINDUP);
			level.playSound(null, warden.getX(), warden.getY(), warden.getZ(), SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE, 2, 0.8F);
			return;
		}

		warden.getNavigation().stop();
		warden.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		Vec3 ground = warden.position().add(0, 0.1, 0);
		List<Display.BlockDisplay> ring = slamDiscs.computeIfAbsent(id, k -> new ArrayList<>());
		while (ring.size() < SLAM_RING_SEGMENTS) {
			ring.add(Beams.create(level, ground));
		}
		double grown = 1 - windup / (double) SLAM_WINDUP;
		if (windup > 0) {
			// a glowing ring spreads out to the slam's edge: get outside it
			drawRing(ring, ground, SLAM_RADIUS * (0.3 + 0.7 * grown), SLAM_DISC_BLOCK, SLAM_DISC_GLOW);
			slamWindup.put(id, windup - 1);
			return;
		}

		// the slam: the ring flashes white at full size, then fades
		slamWindup.remove(id);
		drawRing(ring, ground, SLAM_RADIUS, SLAM_FLASH_BLOCK, 0);
		slamDiscFade.put(id, 6);
		for (ServerPlayer p : players) {
			if (p.level() == level && !p.isDeadOrDying() && Arena.flat2(p, warden.position()) <= SLAM_RADIUS * SLAM_RADIUS
				&& Math.abs(p.getY() - warden.getY()) <= 3) {
				p.hurtServer(level, level.damageSources().mobAttack(warden), SLAM_DAMAGE);
			}
		}
		level.playSound(null, warden.getX(), warden.getY(), warden.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 2, 0.6F);
		level.playSound(null, warden.getX(), warden.getY(), warden.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1, 0.8F);
	}

	/** Ravager: when you're 5-20 blocks away, it dashes straight at you every 4 s (its hits knock you back as usual). */
	private void dash(final Ravager ravager, final ServerPlayer target) {
		if (target == null || !ravager.onGround()) {
			return;
		}
		double distance = Math.sqrt(Arena.flat2(ravager, target.position()));
		if (distance < DASH_MIN_DISTANCE || distance > DASH_MAX_DISTANCE || !use(ravager, DASH_COOLDOWN)) {
			return;
		}
		launch(ravager, target, 1.6, 0.2);
		ravager.level().playSound(null, ravager.getX(), ravager.getY(), ravager.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2, 1.2F);
	}

	/**
	 * Ghast: instead of drifting about, it keeps hovering 10 blocks from you and 7 above, on your side of where it is, and keeps
	 * you as its target.
	 */
	private void chase(final Ghast ghast, final ServerPlayer target) {
		if (target == null || tick % 10 != 0) {
			return;
		}
		ghast.setTarget(target);
		double dx = ghast.getX() - target.getX();
		double dz = ghast.getZ() - target.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 0.1) {
			dx = 1;
			dz = 0;
			length = 1;
		}
		ghast.getMoveControl().setWantedPosition(target.getX() + dx / length * GHAST_HOVER_DISTANCE, target.getY() + GHAST_HOVER_HEIGHT,
			target.getZ() + dz / length * GHAST_HOVER_DISTANCE, 1);
	}

	/**
	 * Every fireball an arena ghast throws (its own and the extra ones): explosion 2 instead of 1, and about 1.4x as fast. Called
	 * when the fireball enters the world.
	 */
	static void boost(final Entity e) {
		if (e instanceof LargeFireball fireball && fireball.getOwner() instanceof Ghast ghast && Arena.isArenaMob(ghast)
			&& fireball.addTag(BOOSTED)) {
			((LargeFireballAccessor) fireball).cybergrind$setExplosionPower(FIREBALL_POWER);
			fireball.accelerationPower = FIREBALL_ACCELERATION;
			Vec3 motion = fireball.getDeltaMovement();
			if (motion.lengthSqr() > 1.0E-6) {
				fireball.setDeltaMovement(motion.normalize().scale(FIREBALL_START_SPEED));
			}
		}
	}

	/**
	 * Phantom spider: every 5 s, when you're 5-24 blocks away, it vanishes in a puff of portal particles and reappears 3 blocks
	 * behind you (where you're not looking), on the ground there.
	 */
	private void blink(final Mob phantom, final ServerPlayer target) {
		if (target == null) {
			return;
		}
		double distance = Math.sqrt(Arena.flat2(phantom, target.position()));
		if (distance < BLINK_MIN_DISTANCE || distance > BLINK_MAX_DISTANCE || !use(phantom, BLINK_COOLDOWN)) {
			return;
		}
		ServerLevel level = (ServerLevel) phantom.level();
		Vec3 look = target.getLookAngle();
		double lx = look.x;
		double lz = look.z;
		double length = Math.sqrt(lx * lx + lz * lz);
		if (length < 0.01) {
			lx = 0;
			lz = 1;
			length = 1;
		}
		double x = target.getX() - lx / length * BLINK_BEHIND;
		double z = target.getZ() - lz / length * BLINK_BEHIND;
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos feet = BlockPos.containing(x, target.getY() + dy, z);
			if (Arena.standable(level, feet)) {
				level.sendParticles(ParticleTypes.PORTAL, phantom.getX(), phantom.getY() + 0.5, phantom.getZ(), 30, 0.5, 0.5, 0.5, 0.5);
				level.playSound(null, phantom.getX(), phantom.getY(), phantom.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1, 1.2F);
				float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - x), target.getZ() - z));
				phantom.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0);
				phantom.setTarget(target);
				level.sendParticles(ParticleTypes.PORTAL, phantom.getX(), phantom.getY() + 0.5, phantom.getZ(), 30, 0.5, 0.5, 0.5, 0.5);
				level.playSound(null, phantom.getX(), phantom.getY(), phantom.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1, 1.2F);
				return;
			}
		}
	}

	/** Ghast: a fireball at you every 1.5 s while it can see you (on top of its own shots). They don't hurt other enemies. */
	private void fireball(final Ghast ghast, final ServerPlayer target) {
		if (target == null || ghast.distanceToSqr(target) > FIREBALL_RANGE * FIREBALL_RANGE || !ghast.hasLineOfSight(target)
			|| !use(ghast, FIREBALL_COOLDOWN)) {
			return;
		}
		ServerLevel level = (ServerLevel) ghast.level();
		Vec3 view = ghast.getViewVector(1);
		Vec3 from = new Vec3(ghast.getX() + view.x * 4, ghast.getY(0.5) + 0.5, ghast.getZ() + view.z * 4);
		Vec3 direction = new Vec3(target.getX() - from.x, target.getY(0.5) - from.y, target.getZ() - from.z).normalize();
		LargeFireball fireball = new LargeFireball(level, ghast, direction, 1);
		fireball.setPos(from);
		level.addFreshEntity(fireball);
		level.playSound(null, ghast.getX(), ghast.getY(), ghast.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 3, 1);
	}

	/** Volley skeletons and Gunner pillagers: an arrow at you every 0.75 s while they can see you. They don't hurt other enemies. */
	private void arrow(final Mob shooter, final ServerPlayer target) {
		if (target == null || shooter.distanceToSqr(target) > ARROW_RANGE * ARROW_RANGE || !shooter.hasLineOfSight(target)
			|| !use(shooter, ARROW_COOLDOWN)) {
			return;
		}
		ServerLevel level = (ServerLevel) shooter.level();
		Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), shooter.getMainHandItem());
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		double dx = target.getX() - shooter.getX();
		double dy = target.getY(1 / 3.0) - arrow.getY();
		double dz = target.getZ() - shooter.getZ();
		arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.6F, 4);
		level.addFreshEntity(arrow);
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
			shooter instanceof Pillager ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1, 1);
	}

	/**
	 * Damage that doesn't happen: ghast and blaze fireballs, wither skulls, witch potions and rapid shooters' arrows never hurt arena
	 * enemies (they'd tear through the crowd), nor do the golems' orbs, and Flying Elder Guardians and Drones have no thorns.
	 */
	static boolean blocked(final LivingEntity victim, final DamageSource source) {
		Entity owner = source.getEntity();
		if (owner == null || owner == victim || !Arena.isArenaMob(owner)) {
			return false;
		}
		if (source.is(DamageTypes.THORNS) && (owner.entityTags().contains(Spawns.PROVIDENCE) || owner.entityTags().contains(Spawns.DRONE))) {
			return true;
		}
		return Arena.isArenaMob(victim) && (owner instanceof Ghast || owner instanceof Blaze || owner instanceof WitherBoss || owner instanceof Witch
			|| owner instanceof IronGolem || owner instanceof Creeper || owner.entityTags().contains(Spawns.RAPID) || owner.entityTags().contains(Spawns.DRONE));
	}

	/**
	 * Arrows from arena enemies holding a bow (skeletons, strays, bogged, Volleys) fly faster. Minecraft's arrow damage goes up
	 * with speed, so their base damage comes down by as much: they hit as hard as before. Called when the arrow enters the world.
	 */
	static void quickenArrow(final Entity e) {
		if (e instanceof AbstractArrow arrow && arrow.getOwner() instanceof LivingEntity archer && Arena.isArenaMob(archer)
			&& archer.getMainHandItem().getItem() == Items.BOW && arrow.addTag("cg_quick")) {
			arrow.setDeltaMovement(arrow.getDeltaMovement().scale(ARROW_SPEED_FACTOR));
			arrow.setBaseDamage(2.0 / ARROW_SPEED_FACTOR);
			arrow.needsSync = true;
		}
	}

	/** An arena witch's lingering Harming cloud: 5 s instead of 30, radius 5, bright red. Called when the cloud enters the world. */
	static void shortenCloud(final Entity e) {
		if (e instanceof AreaEffectCloud cloud && cloud.getOwner() instanceof Witch witch && Arena.isArenaMob(witch) && cloud.addTag("cg_short")) {
			cloud.setDuration(WITCH_CLOUD_TICKS);
			cloud.setRadius(WITCH_CLOUD_RADIUS);
			cloud.setRadiusPerTick(0);
			cloud.setCustomParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFFFF1A1A));
		}
	}

	// ---- Breeze, Blaze, Creeper, vexes ----

	/** Breeze: a wind charge at you every 1.25 s while it sees you, on top of its own (much more often than vanilla). */
	private void windCharge(final Breeze breeze, final ServerPlayer target) {
		if (target == null || breeze.distanceTo(target) > BREEZE_RANGE || !breeze.hasLineOfSight(target) || !use(breeze, BREEZE_COOLDOWN)) {
			return;
		}
		ServerLevel level = (ServerLevel) breeze.level();
		BreezeWindCharge charge = new BreezeWindCharge(breeze, level);
		charge.setPos(breeze.getX(), breeze.getEyeY() - 0.2, breeze.getZ());
		Vec3 aim = target.position().add(0, target.getBbHeight() / 2, 0).subtract(charge.position());
		charge.shoot(aim.x, aim.y, aim.z, 0.9F, 2);
		level.addFreshEntity(charge);
		level.playSound(null, breeze.getX(), breeze.getY(), breeze.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 1.5F, 1);
	}

	/** A Blaze's next spot to fly to, and when it picks another. */
	private final Map<UUID, Vec3> blazeSpots = new HashMap<>();

	/**
	 * Blaze: keeps flying about (a new spot around you, 6-12 blocks out and 2-5 up, every 2 s, fast), and fires an extra
	 * fireball at you every second while it sees you, on top of its own volleys.
	 */
	private void blaze(final Blaze blaze, final ServerPlayer target) {
		if (target == null) {
			return;
		}
		Vec3 spot = blazeSpots.get(blaze.getUUID());
		if (spot == null || tick % BLAZE_MOVE_TICKS == Math.floorMod(blaze.getId(), BLAZE_MOVE_TICKS)) {
			double angle = blaze.getRandom().nextDouble() * Math.PI * 2;
			double r = 6 + blaze.getRandom().nextDouble() * 6;
			spot = new Vec3(target.getX() + Math.cos(angle) * r, target.getY() + 2 + blaze.getRandom().nextDouble() * 3, target.getZ() + Math.sin(angle) * r);
			blazeSpots.put(blaze.getUUID(), spot);
		}
		Vec3 velocity = spot.subtract(blaze.position()).scale(0.15);
		if (velocity.length() > BLAZE_SPEED) {
			velocity = velocity.normalize().scale(BLAZE_SPEED);
		}
		blaze.setDeltaMovement(velocity);
		blaze.needsSync = true;

		if (blaze.distanceTo(target) < BLAZE_RANGE && blaze.hasLineOfSight(target) && use(blaze, BLAZE_COOLDOWN)) {
			ServerLevel level = (ServerLevel) blaze.level();
			Vec3 from = blaze.position().add(0, blaze.getBbHeight() / 2, 0);
			Vec3 aim = target.position().add(0, target.getBbHeight() / 2, 0).subtract(from).normalize();
			SmallFireball fireball = new SmallFireball(level, blaze, aim);
			fireball.setPos(from.add(aim.scale(0.6)));
			fireball.setDeltaMovement(aim.scale(1.2));
			level.addFreshEntity(fireball);
			level.playSound(null, blaze.getX(), blaze.getY(), blaze.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1, 1);
		}
	}

	/**
	 * Creeper: every 3 s, when you're 5-20 blocks away and it sees you, it lobs a small lit TNT at you (fuse 1.5 s, explosion 2:
	 * it breaks terrain, but never hurts other enemies). It still walks up and explodes as usual too.
	 */
	private void lobTnt(final Creeper creeper, final ServerPlayer target) {
		if (target == null) {
			return;
		}
		double distance = creeper.distanceTo(target);
		if (distance < TNT_MIN || distance > TNT_MAX || !creeper.hasLineOfSight(target) || !use(creeper, TNT_COOLDOWN)) {
			return;
		}
		ServerLevel level = (ServerLevel) creeper.level();
		PrimedTnt tnt = new PrimedTnt(level, creeper.getX(), creeper.getY() + creeper.getBbHeight() + 0.2, creeper.getZ(), creeper);
		tnt.setFuse(TNT_FUSE);
		((PrimedTntAccessor) tnt).cybergrind$setExplosionPower(TNT_POWER);
		// a lob that lands about where you stand: TNT falls 0.04 a tick, so over t ticks it needs that much more lift
		Vec3 to = target.position().subtract(tnt.position());
		double t = TNT_FLIGHT_TICKS;
		tnt.setDeltaMovement(to.x / t, to.y / t + 0.5 * 0.04 * t, to.z / t);
		level.addFreshEntity(tnt);
		level.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.5F, 1.4F);
	}

	/** Vexes the arena's evokers summon (they're not arena enemies themselves). */
	private static final List<Vex> VEXES = new ArrayList<>();

	/** Called when a vex enters the world: an arena evoker's vex is sped up from now on. */
	static void trackVex(final Entity e) {
		if (e instanceof Vex vex && vex.getOwner() instanceof Mob owner && Arena.isArenaMob(owner)) {
			VEXES.add(vex);
		}
	}

	/** Arena vexes fly faster: whatever way they're going, a bit quicker (capped). */
	private static void speedVexes() {
		for (Iterator<Vex> it = VEXES.iterator(); it.hasNext();) {
			Vex vex = it.next();
			if (!vex.isAlive()) {
				it.remove();
				continue;
			}
			Vec3 velocity = vex.getDeltaMovement();
			double speed = velocity.length();
			if (speed > 0.05) {
				vex.setDeltaMovement(velocity.scale(Math.min(speed * VEX_BOOST, VEX_MAX_SPEED) / speed));
				vex.needsSync = true;
			}
		}
	}

	// ---- the Warden's sonic boom ----

	/** A sonic boom on its way: where it is, its fixed direction, how far it has gone. */
	private record SonicBoom(ServerLevel level, Warden owner, Vec3 pos, Vec3 direction, double travelled) {
	}

	private final List<SonicBoom> sonicBooms = new ArrayList<>();
	/** Wardens charging a boom: ticks of charge left. */
	private final Map<UUID, Integer> sonicCharge = new HashMap<>();
	private final Map<UUID, Integer> sonicReadyAt = new HashMap<>();

	private static ServerLevel level(final Entity e) {
		return (ServerLevel) e.level();
	}

	/**
	 * The Warden's sonic boom, with travel time: every 5 s, when you're 5-28 blocks away and it can see you, it stops and plays its
	 * own sonic-boom wind-up (its chest glows, 1.5 s), then fires a boom that flies at where you were (1 block a tick, so dashing
	 * dodges it), drawn as a stream of sonic rings. It hits once, for 10, through armour (as the vanilla boom).
	 */
	private void sonicBoom(final Warden warden, final ServerPlayer target, final ServerLevel level) {
		Integer charge = sonicCharge.get(warden.getUUID());
		if (charge != null) {
			warden.getNavigation().stop();
			warden.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			face(warden, target.getEyePosition());
			if (charge > 1) {
				sonicCharge.put(warden.getUUID(), charge - 1);
				return;
			}
			sonicCharge.remove(warden.getUUID());
			Vec3 from = warden.position().add(0, warden.getBbHeight() * 0.6, 0);
			Vec3 direction = target.position().add(0, target.getBbHeight() / 2, 0).subtract(from).normalize();
			sonicBooms.add(new SonicBoom(level, warden, from, direction, 0));
			level.playSound(null, warden.getX(), warden.getY(), warden.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3, 1);
			return;
		}
		double distance = warden.distanceTo(target);
		if (distance < SONIC_MIN || distance > SONIC_MAX || !warden.hasLineOfSight(target) || !use(warden, SONIC_COOLDOWN, sonicReadyAt)) {
			return;
		}
		sonicCharge.put(warden.getUUID(), SONIC_CHARGE_TICKS);
		// the warden's own sonic-boom animation (its chest lights up) and charging sound
		level.broadcastEntityEvent(warden, (byte) 62);
		level.playSound(null, warden.getX(), warden.getY(), warden.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3, 1);
	}

	private void tickSonicBooms(final List<ServerPlayer> players) {
		List<SonicBoom> next = new ArrayList<>();
		for (SonicBoom boom : sonicBooms) {
			Vec3 pos = boom.pos().add(boom.direction().scale(SONIC_SPEED));
			// two rings a tick, so the boom reads as a solid moving shockwave
			boom.level().sendParticles(ParticleTypes.SONIC_BOOM, true, true, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
			Vec3 half = boom.pos().lerp(pos, 0.5);
			boom.level().sendParticles(ParticleTypes.SONIC_BOOM, true, true, half.x, half.y, half.z, 1, 0, 0, 0, 0);
			ServerPlayer hit = players.stream()
				.filter(p -> p.level() == boom.level() && !p.isDeadOrDying() && p.position().add(0, p.getBbHeight() / 2, 0).distanceTo(pos) < SONIC_RADIUS)
				.findFirst().orElse(null);
			if (hit != null) {
				hit.hurtServer(boom.level(), boom.level().damageSources().sonicBoom(boom.owner()), SONIC_DAMAGE);
				continue;
			}
			if (boom.travelled() + SONIC_SPEED < SONIC_RANGE) {
				next.add(new SonicBoom(boom.level(), boom.owner(), pos, boom.direction(), boom.travelled() + SONIC_SPEED));
			}
		}
		sonicBooms.clear();
		sonicBooms.addAll(next);
	}

	// ---- Phantom webs ----

	/**
	 * Phantom spider: every 4 s, when you're 4-18 blocks away and in sight, it spits a web at where you're standing. The web
	 * flies there (about 1.2 blocks a tick, so you can move out of the way) and leaves cobwebs at feet and head height, which melt
	 * after 5 s.
	 */
	private void spitWeb(final Mob phantom, final ServerPlayer target) {
		if (target == null) {
			return;
		}
		double distance = phantom.distanceTo(target);
		if (distance < WEB_MIN_DISTANCE || distance > WEB_MAX_DISTANCE || !phantom.hasLineOfSight(target) || !use(phantom, WEB_COOLDOWN, webReadyAt)) {
			return;
		}
		ServerLevel level = (ServerLevel) phantom.level();
		Vec3 from = phantom.getEyePosition();
		Vec3 to = target.position();
		flyingWebs.add(new FlyingWeb(level, from, to, tick, tick + Math.max(4, (int) Math.ceil(from.distanceTo(to) / WEB_SPEED))));
		level.playSound(null, phantom.getX(), phantom.getY(), phantom.getZ(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.5F, 0.6F);
	}

	private void tickWebs() {
		for (Iterator<FlyingWeb> it = flyingWebs.iterator(); it.hasNext();) {
			FlyingWeb web = it.next();
			double progress = Math.min(1, (tick - web.fired()) / (double) (web.lands() - web.fired()));
			Vec3 at = web.from().lerp(web.to(), progress).add(0, Math.sin(progress * Math.PI) * 1.5, 0);
			web.level().sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0);
			if (tick >= web.lands()) {
				it.remove();
				BlockPos feet = BlockPos.containing(web.to());
				for (BlockPos pos : List.of(feet, feet.above())) {
					if (web.level().getBlockState(pos).isAir()) {
						web.level().setBlock(pos, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
						placedWebs.add(new PlacedWeb(web.level(), pos, tick + WEB_LIFETIME));
					}
				}
				web.level().sendParticles(ParticleTypes.ITEM_COBWEB, feet.getX() + 0.5, feet.getY() + 0.5, feet.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0);
			}
		}
		for (Iterator<PlacedWeb> it = placedWebs.iterator(); it.hasNext();) {
			PlacedWeb web = it.next();
			if (tick >= web.melts()) {
				melt(web);
				it.remove();
			}
		}
	}

	/** A web goes (if it's still a web: something else may have been put there since). */
	private static void melt(final PlacedWeb web) {
		if (web.level().getBlockState(web.pos()).is(Blocks.COBWEB)) {
			web.level().setBlock(web.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			web.level().sendParticles(ParticleTypes.ITEM_COBWEB, web.pos().getX() + 0.5, web.pos().getY() + 0.5, web.pos().getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
		}
	}

	// ---- Providence ----

	/**
	 * Providence (ULTRAKILL's lesser angel), on an elder guardian: it never uses the guardian's own beam. It dashes twice to a spot
	 * around you (8-13 blocks out, 4-7 up), then hovers and charges for 1 s (a magenta core grows in front of it, slow-turning for
	 * a cross, fast-spinning for a spiral, and a thin beam follows you; both go white 0.3 s before it fires, locked on that spot),
	 * and fires there: a CROSS, a giant + of beams through that spot facing it, or a SPIRAL, four beams that start spread around
	 * that spot and wind in onto it. The beams are solid (block displays). Then it dashes again.
	 */
	private void providence(final Mob angel, final List<ServerPlayer> players) {
		stripGoals(angel);
		angel.setTarget(null);
		ServerPlayer target = nearest(angel, players);
		Providence p = providences.computeIfAbsent(angel.getUUID(), id -> new Providence());
		p.seen = true;
		ServerLevel level = (ServerLevel) angel.level();
		if (target == null) {
			angel.setDeltaMovement(angel.getDeltaMovement().scale(0.8));
			p.clearFx();
			return;
		}
		face(angel, p.phase == FIRING && p.aim != null ? p.aim : target.getEyePosition());

		switch (p.phase) {
			case DASHING -> {
				if (p.timer <= 0) {
					if (p.dashes >= 2) {
						p.phase = CHARGING;
						p.timer = PROVIDENCE_CHARGE_TICKS;
						p.spiral = random(angel) < 0.5;
						level.playSound(null, angel.getX(), angel.getY(), angel.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2, 1.6F);
						return;
					}
					double angle = random(angel) * Math.PI * 2;
					double r = 8 + random(angel) * 5;
					p.dashTo = new Vec3(target.getX() + Math.cos(angle) * r, target.getY() + 4 + random(angel) * 3, target.getZ() + Math.sin(angle) * r);
					p.dashes++;
					p.timer = PROVIDENCE_DASH_TICKS;
				}
				p.timer--;
				Vec3 velocity = p.dashTo.subtract(angel.position()).scale(0.25);
				if (velocity.length() > 1.3) {
					velocity = velocity.normalize().scale(1.3);
				}
				angel.setDeltaMovement(velocity);
				angel.needsSync = true;
			}
			case CHARGING -> {
				angel.setDeltaMovement(angel.getDeltaMovement().scale(0.5));
				angel.needsSync = true;
				// the charge, shown solid: a magenta core grows and spins in front of it, and a thin aiming beam follows you and
				// thickens; 0.3 s before firing both turn white and the beam stops on where it will fire (time to dash)
				Vec3 eye = angel.position().add(0, angel.getBbHeight() / 2, 0);
				if (p.locked == null && p.timer <= PROVIDENCE_LOCK_TICKS) {
					p.locked = target.position().add(0, 1, 0);
					level.playSound(null, angel.getX(), angel.getY(), angel.getZ(), SoundEvents.NOTE_BLOCK_BIT, SoundSource.HOSTILE, 2, 1.8F);
				}
				double charged = 1 - p.timer / (double) PROVIDENCE_CHARGE_TICKS;
				boolean locked = p.locked != null;
				Vec3 aimAt = locked ? p.locked : target.position().add(0, target.getBbHeight() / 2, 0);
				Vec3 front = eye.add(aimAt.subtract(eye).normalize().scale(angel.getBbWidth() * 0.6 + 0.3));
				if (p.core == null) {
					p.core = Beams.create(level, eye);
				}
				if (p.aimBeam == null) {
					p.aimBeam = Beams.create(level, eye);
				}
				float spin = (float) (tick * (p.spiral ? 0.5 : 0.15));
				Beams.cube(p.core, front, locked ? 0.8F : (float) (0.2 + 0.5 * charged), spin, locked ? LOCKED_BLOCK : CHARGE_BLOCK, BEAM_GLOW);
				Beams.beam(p.aimBeam, front, aimAt, locked ? 0.08F : (float) (0.02 + 0.04 * charged), locked ? LOCKED_BLOCK : CHARGE_BLOCK, BEAM_GLOW);
				if (--p.timer <= 0) {
					p.phase = FIRING;
					p.timer = p.spiral ? PROVIDENCE_SPIRAL_TICKS : PROVIDENCE_CROSS_TICKS;
					p.aim = p.locked != null ? p.locked : target.position().add(0, 1, 0);
					p.locked = null;
					p.hitAt.clear();
					p.clearFx();
					level.playSound(null, angel.getX(), angel.getY(), angel.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3, 1.8F);
					level.playSound(null, angel.getX(), angel.getY(), angel.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 3, 0.6F);
				}
			}
			default -> {
				angel.setDeltaMovement(Vec3.ZERO);
				angel.needsSync = true;
				int total = p.spiral ? PROVIDENCE_SPIRAL_TICKS : PROVIDENCE_CROSS_TICKS;
				double t = 1 - p.timer / (double) total;
				List<Vec3[]> segments = beams(angel, p, t);
				// solid beams: they snap out thin and swell to full width over the first few ticks
				float width = (float) Math.min(BEAM_DRAW_WIDTH, 0.06 + (total - p.timer) * 0.05);
				for (int i = 0; i < segments.size(); i++) {
					if (p.beams.size() <= i) {
						p.beams.add(Beams.create(level, angel.position().add(0, angel.getBbHeight() / 2, 0)));
					}
					Beams.beam(p.beams.get(i), segments.get(i)[0], segments.get(i)[1], width, BEAM_BLOCK, BEAM_GLOW);
				}
				for (Vec3[] beam : segments) {
					for (ServerPlayer player : players) {
						Vec3 body = player.position().add(0, player.getBbHeight() / 2, 0);
						Integer last = p.hitAt.get(player.getUUID());
						if (player.level() == level && !player.isDeadOrDying() && distanceToSegment(body, beam[0], beam[1]) < BEAM_WIDTH
							&& (last == null || tick - last >= PROVIDENCE_HIT_COOLDOWN)) {
							p.hitAt.put(player.getUUID(), tick);
							player.hurtServer(level, level.damageSources().indirectMagic(angel, angel), PROVIDENCE_BEAM_DAMAGE);
						}
					}
				}
				if (--p.timer <= 0) {
					p.phase = DASHING;
					p.dashes = 0;
					p.timer = 0;
					p.clearFx();
				}
			}
		}
	}

	/** The beams right now, each a segment. t: 0 at firing, 1 at the end. */
	private static List<Vec3[]> beams(final Mob angel, final Providence p, final double t) {
		Vec3 eye = angel.position().add(0, angel.getBbHeight() / 2, 0);
		Vec3 forward = p.aim.subtract(eye).normalize();
		Vec3 right = forward.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(forward).normalize();
		List<Vec3[]> beams = new ArrayList<>();
		if (!p.spiral) {
			// a + through the aimed spot, in the plane facing the angel, and the beam that feeds it
			beams.add(new Vec3[] {eye, p.aim});
			beams.add(new Vec3[] {p.aim.add(right.scale(-CROSS_ARM)), p.aim.add(right.scale(CROSS_ARM))});
			beams.add(new Vec3[] {p.aim.add(up.scale(-CROSS_ARM)), p.aim.add(up.scale(CROSS_ARM))});
		} else {
			// four beams from the angel, aimed around the spot, turning a quarter and closing in until they meet on it
			double radius = SPIRAL_START * (1 - t);
			double turn = t * Math.PI / 2;
			for (int k = 0; k < 4; k++) {
				double a = turn + k * Math.PI / 2;
				Vec3 point = p.aim.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
				Vec3 direction = point.subtract(eye).normalize();
				beams.add(new Vec3[] {eye, point.add(direction.scale(20))});
			}
		}
		return beams;
	}

	private static double distanceToSegment(final Vec3 point, final Vec3 a, final Vec3 b) {
		Vec3 ab = b.subtract(a);
		double length2 = ab.lengthSqr();
		double t = length2 < 1.0E-6 ? 0 : Math.clamp(point.subtract(a).dot(ab) / length2, 0, 1);
		return point.distanceTo(a.add(ab.scale(t)));
	}

	private static void face(final Mob mob, final Vec3 at) {
		Vec3 d = at.subtract(mob.getEyePosition());
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setXRot(pitch);
	}

	private static double random(final Mob mob) {
		return mob.getRandom().nextDouble();
	}

	private void tickHealOrbs(final List<ServerPlayer> players) {
		for (Iterator<HealOrb> it = HEAL_ORBS.iterator(); it.hasNext();) {
			HealOrb orb = it.next();
			// it drifts down until it sits on the ground
			BlockPos below = BlockPos.containing(orb.pos.x, orb.pos.y - ORB_SIZE / 2 - ORB_FALL, orb.pos.z);
			if (orb.level.getBlockState(below).getCollisionShape(orb.level, below).isEmpty() && orb.pos.y > orb.level.getMinY()) {
				orb.pos = orb.pos.add(0, -ORB_FALL, 0);
			}
			if (orb.cube == null) {
				orb.cube = Beams.create(orb.level, orb.pos);
				orb.heart = Beams.heart(orb.level, orb.pos);
			}
			float pulse = (float) (ORB_SIZE * (1 + 0.12 * Math.sin(tick * 0.3)));
			Beams.cube(orb.cube, orb.pos, pulse, tick * 0.12F, ORB_BLOCK, ORB_GLOW);
			// the heart inside, beating with the box
			float beat = (float) (HEART_SCALE * (1 + 0.15 * Math.sin(tick * 0.3)));
			Beams.centre(orb.heart, orb.pos, beat, beat * 0.13F);

			ServerPlayer taker = players.stream()
				.filter(p -> p.level() == orb.level && !p.isDeadOrDying() && p.position().add(0, 1, 0).distanceTo(orb.pos) < 2.5)
				.findFirst().orElse(null);
			if (taker != null) {
				taker.setHealth(taker.getMaxHealth());
				// the ULTRAKILL mod heals V1 through Regeneration
				taker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 4, false, true));
				orb.level.playSound(null, orb.pos.x, orb.pos.y, orb.pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3, 1.4F);
				orb.level.playSound(null, orb.pos.x, orb.pos.y, orb.pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1, 1.6F);
				orb.remove();
				it.remove();
			} else if (tick >= orb.expires) {
				orb.remove();
				it.remove();
			}
		}
	}

	// ---- the golems' orbs ----

	/** Golem orbs in flight: real fireballs, bent toward the nearest player once close. */
	private final List<LargeFireball> orbs = new ArrayList<>();

	private void steerOrbs(final List<ServerPlayer> players) {
		for (Iterator<LargeFireball> it = orbs.iterator(); it.hasNext();) {
			LargeFireball orb = it.next();
			if (!orb.isAlive()) {
				it.remove();
				continue;
			}
			ServerPlayer target = nearestTo((ServerLevel) orb.level(), orb.position(), players);
			if (target != null && orb.position().distanceTo(target.position()) < ORB_HOMING_RANGE) {
				Vec3 velocity = orb.getDeltaMovement();
				Vec3 want = target.position().add(0, target.getBbHeight() / 2, 0).subtract(orb.position()).normalize().scale(velocity.length());
				orb.setDeltaMovement(velocity.scale(0.94).add(want.scale(0.06)));
				orb.needsSync = true;
			}
		}
	}

	private static ServerPlayer nearestTo(final ServerLevel level, final Vec3 pos, final List<ServerPlayer> players) {
		ServerPlayer nearest = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			if (p.level() == level && !p.isDeadOrDying() && !p.isSpectator() && p.position().distanceToSqr(pos) < best) {
				best = p.position().distanceToSqr(pos);
				nearest = p;
			}
		}
		return nearest;
	}

	// ---- Drone ----

	/**
	 * A Drone's state: charging its attack (ticks left), shots left in the burst and the wait to the next, its next attack, its
	 * next side-dash, and once killed, the way it dives and for how long.
	 */
	private static final class Drone {
		int charge;
		int shotsLeft;
		int shotWait;
		int dived;
		Vec3 diveDirection;
		int nextAttack = 40;
		int nextStrafe = 30;
		int strafing;
		Vec3 strafe = Vec3.ZERO;
	}

	private final Map<UUID, Drone> drones = new HashMap<>();

	/**
	 * Drone (ULTRAKILL's Lesser Machine), on a guardian: hovers at medium range (comes closer when you're far, backs off fast when
	 * you're close), dashes side to side at random, and every 2-3 s charges (orange sparks, a chirp) and fires three shots one after
	 * another, semi-auto, each aimed at where you are then. Killed, it hurtles the way it was looking and explodes on impact.
	 */
	private void drone(final Mob drone, final List<ServerPlayer> players) {
		stripGoals(drone);
		drone.setTarget(null);
		Drone d = drones.computeIfAbsent(drone.getUUID(), id -> new Drone());
		ServerLevel level = (ServerLevel) drone.level();
		if (drone.entityTags().contains(DIVING)) {
			dive(drone, d, level);
			return;
		}
		ServerPlayer target = nearest(drone, players);
		if (target == null) {
			drone.setDeltaMovement(drone.getDeltaMovement().scale(0.8));
			return;
		}
		Vec3 body = target.position().add(0, target.getBbHeight() / 2, 0);
		face(drone, body);

		// keep to medium range, 3 blocks above you
		Vec3 to = target.position().subtract(drone.position());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		Vec3 sideways = flat < 0.01 ? new Vec3(1, 0, 0) : new Vec3(to.x / flat, 0, to.z / flat);
		double speed = flat > DRONE_FAR ? 0.3 : flat < DRONE_NEAR ? -0.55 : 0;
		double up = Math.clamp((target.getY() + 3 - drone.getY()) * 0.1, -0.3, 0.3);
		Vec3 move = new Vec3(sideways.x * speed, up, sideways.z * speed);

		if (d.strafing > 0) {
			d.strafing--;
			move = move.add(d.strafe);
		} else if (--d.nextStrafe <= 0 && d.charge == 0 && d.shotsLeft == 0) {
			// a random dash to the side: hard to hit, but not a dodge of your shots
			double side = drone.getRandom().nextBoolean() ? 1 : -1;
			d.strafe = new Vec3(-sideways.z * side * 0.9, 0, sideways.x * side * 0.9);
			d.strafing = 6;
			d.nextStrafe = 25 + drone.getRandom().nextInt(35);
		}
		drone.setDeltaMovement(d.charge > 0 || d.shotsLeft > 0 ? move.scale(0.3) : move);
		drone.needsSync = true;

		if (d.charge > 0) {
			level.sendParticles(BOLT_COLOR, drone.getX(), drone.getY() + drone.getBbHeight() / 2, drone.getZ(), 3, 0.4, 0.4, 0.4, 0);
			if (--d.charge == 0) {
				d.shotsLeft = DRONE_SHOTS;
				d.shotWait = 0;
			}
		} else if (d.shotsLeft > 0) {
			// semi-auto: one small fireball at a time, each aimed at where you are now
			if (--d.shotWait <= 0) {
				Vec3 from = drone.position().add(0, drone.getBbHeight() / 2, 0);
				Vec3 aim = body.subtract(from).normalize();
				SmallFireball bolt = new SmallFireball(level, drone, aim);
				bolt.setPos(from.add(aim.scale(0.6)));
				bolt.setDeltaMovement(aim.scale(BOLT_SPEED));
				bolt.accelerationPower = 0.15;
				level.addFreshEntity(bolt);
				level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1, 1.6F);
				d.shotsLeft--;
				d.shotWait = DRONE_SHOT_GAP;
			}
		} else if (--d.nextAttack <= 0 && drone.hasLineOfSight(target) && flat < 30) {
			d.charge = DRONE_CHARGE_TICKS;
			d.nextAttack = 40 + drone.getRandom().nextInt(25);
			level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.NOTE_BLOCK_BIT, SoundSource.HOSTILE, 1.5F, 2);
		}
	}

	/**
	 * A killed Drone hurtles in a straight line the way it was looking when it died (at you, then; it doesn't follow you after),
	 * and explodes on impact: a block, you, or any other creature in its way, or after 3 s. Shot again on the way, it explodes
	 * where it is (onDeath).
	 */
	private void dive(final Mob drone, final Drone d, final ServerLevel level) {
		if (d.diveDirection == null) {
			d.diveDirection = drone.getLookAngle().normalize();
		}
		drone.setDeltaMovement(d.diveDirection.scale(DIVE_SPEED));
		drone.needsSync = true;
		level.sendParticles(ParticleTypes.SMOKE, drone.getX(), drone.getY() + drone.getBbHeight() / 2, drone.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
		boolean struck = !level.getEntities(drone, drone.getBoundingBox().inflate(0.3), e -> e instanceof LivingEntity && e.isAlive()).isEmpty();
		if (struck || drone.horizontalCollision || drone.verticalCollision || ++d.dived > DIVE_TICKS) {
			blowUp(drone);
		}
	}

	/** The dive's blast has no owner, so it can take other enemies with it (as in ULTRAKILL). */
	private void blowUp(final Mob drone) {
		drones.remove(drone.getUUID());
		Vec3 at = drone.position().add(0, drone.getBbHeight() / 2, 0);
		drone.discard();
		drone.level().explode(null, at.x, at.y, at.z, DIVE_POWER, Level.ExplosionInteraction.NONE);
	}

	/** Death event: a Drone's killing blow starts its dive (it lives on at 1 HP until it explodes). */
	static boolean allowDeath(final LivingEntity entity) {
		if (Arena.isArenaMob(entity) && entity.entityTags().contains(Spawns.DRONE) && entity.addTag(DIVING)) {
			entity.setHealth(1);
			// dead, really: no outline on the way down
			entity.removeEffect(MobEffects.GLOWING);
			// it dives the way it was looking at that moment
			Arena arena = Arena.current();
			if (arena != null) {
				arena.abilities().drones.computeIfAbsent(entity.getUUID(), id -> new Drone()).diveDirection = entity.getLookAngle().normalize();
			}
			entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.NOTE_BLOCK_BIT, SoundSource.HOSTILE, 2, 0.5F);
			return false;
		}
		return true;
	}

	// ---- Iron Golem as Cerberus ----

	/** A golem's attack in progress: which one, ticks of wind-up (or of the dash) left. */
	private static final class Cerberus {
		int cooldown = 30;
		int windup;
		int dashing;
		char attack;
		boolean dashHit;
		Vec3 dashDir = Vec3.ZERO;
	}

	private final Map<UUID, Cerberus> cerberi = new HashMap<>();

	/**
	 * The Iron Golem fights like ULTRAKILL's Cerberus. Far away (over 12 blocks): winds up and throws an explosive orb that bends
	 * toward you when near. Mid range: a tackle dash that hurts on contact. Up close (under 4.5): stomps, sending a shockwave ring
	 * along the ground. While winding up it stands still. Enraged (another golem died): faster, shorter wind-ups and cooldowns.
	 */
	private void cerberus(final IronGolem golem, final ServerPlayer target) {
		if (target == null) {
			return;
		}
		Cerberus c = cerberi.computeIfAbsent(golem.getUUID(), id -> new Cerberus());
		ServerLevel level = (ServerLevel) golem.level();
		boolean enraged = golem.entityTags().contains(ENRAGED);
		double distance = Math.sqrt(Arena.flat2(golem, target.position()));

		if (c.dashing > 0) {
			c.dashing--;
			golem.setDeltaMovement(c.dashDir.x, golem.getDeltaMovement().y, c.dashDir.z);
			golem.needsSync = true;
			if (!c.dashHit && golem.getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
				c.dashHit = true;
				target.hurtServer(level, level.damageSources().mobAttack(golem), TACKLE_DAMAGE);
				level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 2, 0.7F);
			}
			return;
		}
		if (c.windup > 0) {
			golem.getNavigation().stop();
			golem.setDeltaMovement(0, golem.getDeltaMovement().y, 0);
			face(golem, target.getEyePosition());
			Vec3 at = golem.position().add(0, golem.getBbHeight() + 0.5, 0);
			if (c.attack == 'O') {
				level.sendParticles(ORB_COLOR, at.x, at.y, at.z, 4, 0.25, 0.25, 0.25, 0);
			} else {
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, at.x, at.y - 0.4, at.z, 1, 0.4, 0.2, 0.4, 0);
			}
			if (--c.windup == 0) {
				release(golem, c, target, level, enraged);
			}
			return;
		}
		if (--c.cooldown > 0) {
			return;
		}
		if (distance < STOMP_RANGE) {
			c.attack = 'S';
		} else if (distance > ORB_MIN_RANGE && golem.hasLineOfSight(target)) {
			c.attack = 'O';
		} else if (distance <= DASH_RANGE) {
			c.attack = 'D';
		} else {
			return;
		}
		int windup = c.attack == 'O' ? 20 : c.attack == 'D' ? 12 : 14;
		c.windup = enraged ? windup * 6 / 10 : windup;
		level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.IRON_GOLEM_HURT, SoundSource.HOSTILE, 1.5F, 0.5F);
	}

	private void release(final IronGolem golem, final Cerberus c, final ServerPlayer target, final ServerLevel level, final boolean enraged) {
		c.cooldown = enraged ? 20 : 40;
		switch (c.attack) {
			case 'O' -> {
				Vec3 from = golem.position().add(0, golem.getBbHeight() + 0.5, 0);
				Vec3 aim = target.position().add(0, target.getBbHeight() / 2, 0).subtract(from).normalize();
				LargeFireball orb = new LargeFireball(level, golem, aim, 1);
				orb.setPos(from);
				orb.setDeltaMovement(aim.scale(ORB_SPEED));
				orb.accelerationPower = 0.12;
				level.addFreshEntity(orb);
				orbs.add(orb);
				level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2, 0.6F);
			}
			case 'D' -> {
				Vec3 to = target.position().subtract(golem.position());
				double flat = Math.max(0.1, Math.sqrt(to.x * to.x + to.z * to.z));
				double speed = enraged ? 1.6 : 1.2;
				c.dashDir = new Vec3(to.x / flat * speed, 0, to.z / flat * speed);
				c.dashing = 10;
				c.dashHit = false;
				level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 2, 0.6F);
			}
			default -> {
				// the stomp: a ring that runs out along the ground; anyone it passes under takes the hit
				shockwaves.add(new Shockwave(level, golem, golem.position(), 0, new HashSet<>()));
				level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2, 0.5F);
			}
		}
	}

	private record Shockwave(ServerLevel level, Entity owner, Vec3 center, int age, Set<UUID> hit) {
	}

	private final List<Shockwave> shockwaves = new ArrayList<>();

	private void tickShockwaves(final List<ServerPlayer> players) {
		List<Shockwave> next = new ArrayList<>();
		for (Shockwave wave : shockwaves) {
			double radius = 1 + wave.age() * SHOCKWAVE_SPEED;
			int points = (int) (radius * 6);
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points;
				wave.level().sendParticles(ParticleTypes.POOF, wave.center().x + Math.cos(a) * radius, wave.center().y + 0.2,
					wave.center().z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0);
			}
			for (ServerPlayer p : players) {
				double d = Math.sqrt(Arena.flat2(p, wave.center()));
				if (p.level() == wave.level() && Math.abs(d - radius) < 0.9 && Math.abs(p.getY() - wave.center().y) < 1.5 && wave.hit().add(p.getUUID())) {
					p.hurtServer(wave.level(), wave.level().damageSources().mobAttack((LivingEntity) wave.owner()), SHOCKWAVE_DAMAGE);
					p.setDeltaMovement(p.getDeltaMovement().x, 0.8, p.getDeltaMovement().z);
					p.needsSync = true;
				}
			}
			if (radius < SHOCKWAVE_RADIUS) {
				next.add(new Shockwave(wave.level(), wave.owner(), wave.center(), wave.age() + 1, wave.hit()));
			}
		}
		shockwaves.clear();
		shockwaves.addAll(next);
	}

	/** One golem died: every other golem still fighting becomes Enraged for good (faster, quicker attacks). */
	private void enrageGolems(final IronGolem dead) {
		for (IronGolem golem : ((ServerLevel) dead.level()).getEntities(EntityTypeTest.forClass(IronGolem.class),
			g -> g != dead && g.isAlive() && Arena.isArenaMob(g) && !g.entityTags().contains(ENRAGED))) {
			golem.addTag(ENRAGED);
			AttributeInstance speed = golem.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.addOrReplacePermanentModifier(new AttributeModifier(Identifier.fromNamespaceAndPath("cybergrind", "enraged"), 0.5,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
			golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.5F, 1.4F);
			((ServerLevel) golem.level()).sendParticles(ParticleTypes.ANGRY_VILLAGER, golem.getX(), golem.getY() + 2, golem.getZ(), 8, 0.6, 0.6, 0.6, 0);
		}
	}

	// ---- deaths ----

	/** An arena enemy died: Flying Elder Guardians leave a heal orb, a diving Drone explodes, a golem's death enrages the others. */
	static void onDeath(final LivingEntity dead) {
		Arena arena = Arena.current();
		if (arena == null || !Arena.isArenaMob(dead) || !(dead.level() instanceof ServerLevel level)) {
			return;
		}
		Abilities abilities = arena.abilities();
		if (dead.entityTags().contains(Spawns.PROVIDENCE)) {
			HEAL_ORBS.add(new HealOrb(level, dead.position().add(0, dead.getBbHeight() / 2, 0), abilities.tick + HEAL_ORB_LIFETIME));
		} else if (dead.entityTags().contains(Spawns.DRONE) && dead instanceof Mob drone) {
			// shot down mid-dive: it explodes where it is
			abilities.drones.remove(drone.getUUID());
			Vec3 at = drone.position().add(0, drone.getBbHeight() / 2, 0);
			level.explode(null, at.x, at.y, at.z, DIVE_POWER, Level.ExplosionInteraction.NONE);
		} else if (dead instanceof IronGolem golem) {
			abilities.enrageGolems(golem);
		}
	}

	/** An elder guardian's or guardian's own goals (wandering, its laser) go, including after a reload: this mod flies it. */
	private static void stripGoals(final Mob mob) {
		MobAccessor goals = (MobAccessor) mob;
		if (!goals.cybergrind$goals().getAvailableGoals().isEmpty() || !goals.cybergrind$targets().getAvailableGoals().isEmpty()) {
			goals.cybergrind$goals().removeAllGoals(g -> true);
			goals.cybergrind$targets().removeAllGoals(g -> true);
		}
	}

	int tick() {
		return tick;
	}

	private static void launch(final Entity e, final ServerPlayer target, final double speed, final double up) {
		double dx = target.getX() - e.getX();
		double dz = target.getZ() - e.getZ();
		double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
		e.setDeltaMovement(dx / length * speed, up, dz / length * speed);
		e.needsSync = true;
	}

	/** True (and starts the cooldown) when the enemy's ability is ready. */
	private boolean use(final Entity e, final int cooldown) {
		return use(e, cooldown, readyAt);
	}

	private boolean use(final Entity e, final int cooldown, final Map<UUID, Integer> ready) {
		Integer at = ready.get(e.getUUID());
		if (at != null && tick < at) {
			return false;
		}
		ready.put(e.getUUID(), tick + cooldown);
		return true;
	}

	private static ServerPlayer nearest(final Entity e, final List<ServerPlayer> players) {
		ServerPlayer nearest = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			if (p.level() == e.level() && !p.isDeadOrDying() && !p.isSpectator()) {
				double d = Arena.flat2(p, e.position());
				if (d < best) {
					best = d;
					nearest = p;
				}
			}
		}
		return nearest;
	}
}
