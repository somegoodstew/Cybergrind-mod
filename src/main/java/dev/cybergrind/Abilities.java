package dev.cybergrind;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.cybergrind.mixin.LargeFireballAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * Enemy abilities, run every tick of a run: the Warden hunts you and slams the ground, the Iron Golem leaps at you, the Ravager
 * dashes at you, the Wither calls 3 wither skeletons at half health, Ghasts keep throwing fireballs, Volley skeletons and
 * Gunner pillagers keep firing arrows, and Phantom spiders blink behind you. Server thread.
 */
final class Abilities {
	private static final int SLAM_COOLDOWN = 160;
	private static final double SLAM_RADIUS = 6;
	private static final float SLAM_DAMAGE = 10;
	private static final int LEAP_COOLDOWN = 80;
	private static final double LEAP_MIN_DISTANCE = 10;
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

	private final Arena arena;
	/** The tick each enemy may use its ability again. */
	private final Map<UUID, Integer> readyAt = new HashMap<>();
	/** Withers that have called their minions. */
	private final Set<UUID> minionsCalled = new HashSet<>();
	private int tick;

	Abilities(final Arena arena) {
		this.arena = arena;
	}

	void clear() {
		readyAt.clear();
		minionsCalled.clear();
	}

	void tick(final List<Entity> mobs, final List<ServerPlayer> players) {
		tick++;
		spreadWithers(mobs);
		for (Entity e : mobs) {
			// enemies only ever fight you: a target that's another arena enemy (a golem going for a skeleton, a mob turning on
			// whoever hit it by accident) is swapped for the nearest player
			if (e instanceof Mob mob && mob.getTarget() != null && Arena.isArenaMob(mob.getTarget())) {
				mob.setTarget(nearest(e, players));
			}
			if (e instanceof Warden warden) {
				ServerPlayer target = nearest(e, players);
				if (target != null && tick % 20 == 0) {
					// a hit only makes a warden angry for a second or two: keep it at full anger, locked on the nearest player
					warden.increaseAngerAt(target, 150, false);
					warden.setAttackTarget(target);
				}
				slam(warden, players);
			} else if (e instanceof IronGolem golem) {
				leap(golem, nearest(e, players));
			} else if (e instanceof Ravager ravager) {
				dash(ravager, nearest(e, players));
			} else if (e instanceof Ghast ghast) {
				ServerPlayer target = nearest(e, players);
				chase(ghast, target);
				fireball(ghast, target);
			} else if (e instanceof Mob phantom && e.entityTags().contains(Spawns.BLINK)) {
				blink(phantom, nearest(e, players));
			} else if (e instanceof Mob shooter && e.entityTags().contains(Spawns.RAPID)) {
				arrow(shooter, nearest(e, players));
			} else if (e instanceof WitherBoss wither && wither.getHealth() <= wither.getMaxHealth() / 2 && minionsCalled.add(wither.getUUID())) {
				arena.spawnMinions(wither);
			}
		}
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

	/** Warden: every 8 s, players within 6 blocks take 10 damage (armor reduces it). No knockback: it wouldn't reach V1. */
	private void slam(final Warden warden, final List<ServerPlayer> players) {
		List<ServerPlayer> hit = players.stream()
			.filter(p -> p.level() == warden.level() && !p.isDeadOrDying()
				&& Arena.flat2(p, warden.position()) <= SLAM_RADIUS * SLAM_RADIUS && Math.abs(p.getY() - warden.getY()) <= 3)
			.toList();
		if (hit.isEmpty() || !use(warden, SLAM_COOLDOWN)) {
			return;
		}

		ServerLevel level = (ServerLevel) warden.level();
		for (ServerPlayer p : hit) {
			p.hurtServer(level, level.damageSources().mobAttack(warden), SLAM_DAMAGE);
		}
		for (int i = 0; i < 16; i++) {
			double angle = Math.PI * 2 * i / 16;
			level.sendParticles(ParticleTypes.EXPLOSION, warden.getX() + Math.cos(angle) * 3, warden.getY() + 0.2, warden.getZ() + Math.sin(angle) * 3,
				1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, warden.getX(), warden.getY() + 1, warden.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, warden.getX(), warden.getY(), warden.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 2, 0.6F);
		level.playSound(null, warden.getX(), warden.getY(), warden.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1, 0.8F);
	}

	/** Iron Golem: when you're over 10 blocks away, it pounces toward you every 4 s. */
	private void leap(final IronGolem golem, final ServerPlayer target) {
		if (target == null || !golem.onGround()) {
			return;
		}
		double distance = Math.sqrt(Arena.flat2(golem, target.position()));
		if (distance <= LEAP_MIN_DISTANCE || !use(golem, LEAP_COOLDOWN)) {
			return;
		}
		launch(golem, target, Math.min(distance * 0.12, 2.0), 0.6);
		golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2, 0.7F);
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

	/** Ghast and blaze fireballs, wither skulls and rapid shooters' arrows don't hurt arena enemies (they'd tear through the crowd). */
	static boolean friendlyFire(final LivingEntity victim, final DamageSource source) {
		Entity owner = source.getEntity();
		return Arena.isArenaMob(victim) && owner != null && owner != victim && Arena.isArenaMob(owner)
			&& (owner instanceof Ghast || owner instanceof Blaze || owner instanceof WitherBoss || owner.entityTags().contains(Spawns.RAPID));
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
		Integer ready = readyAt.get(e.getUUID());
		if (ready != null && tick < ready) {
			return false;
		}
		readyAt.put(e.getUUID(), tick + cooldown);
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
