package dev.cybergrind;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;

/**
 * ULTRAKILL's impact frames in Minecraft: the Cybergrind Bridge plugin (bridge/, in ULTRAKILL's BepInEx) sends "1" over UDP on
 * 127.0.0.1 every 0.2 s while ULTRAKILL's time stands still (hit-stop, parry flashes), "0" when it runs again. While it does, and
 * V1 is in control, Minecraft's world is tick-frozen (as /tick freeze: mobs and projectiles stop, players don't). Half a second
 * without a message also unfreezes. A freeze the player set themselves is never undone here.
 */
final class HitStop {
	private static final int PORT = Integer.getInteger("cybergrind.bridgePort", 25633);
	private static final long TIMEOUT_NANOS = 500_000_000L;
	private static final Field V1_DRIVING = find();

	/** Until when (System.nanoTime) ULTRAKILL said its time is stopped; 0 when it said it runs. */
	private static volatile long frozenUntil;
	/** Until when V1 is invincible (dash i-frames, or the moment after a hit), as ULTRAKILL says; 0 when it said it isn't. */
	private static volatile long invincibleUntil;
	/** A parry or impact frame keeps V1 safe a little past the freeze itself. */
	private static final long AFTER_FREEZE_NANOS = 150_000_000L;
	private static volatile long lastFrozen;
	private static boolean frozeIt;
	/** When the current freeze began (System.nanoTime), for the log line when it ends. */
	private static long frozeAt;

	private HitStop() {
	}

	/** At start-up: the listener thread (a daemon; nothing happens if the port is taken). */
	static void listen() {
		Thread thread = new Thread(() -> {
			try (DatagramSocket socket = new DatagramSocket(PORT, InetAddress.getLoopbackAddress())) {
				Cybergrind.LOG.info("Cybergrind: listening for ULTRAKILL's time stops on 127.0.0.1:{}", PORT);
				byte[] buffer = new byte[16];
				while (true) {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					socket.receive(packet);
					String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.US_ASCII).trim();
					switch (message) {
						case "1" -> frozenUntil = System.nanoTime() + TIMEOUT_NANOS;
						case "0" -> frozenUntil = 0;
						case "i1" -> invincibleUntil = System.nanoTime() + TIMEOUT_NANOS;
						case "i0" -> invincibleUntil = 0;
						default -> {
						}
					}
				}
			} catch (IOException e) {
				Cybergrind.LOG.warn("Cybergrind: can't listen for ULTRAKILL's time stops on port {} (impact frames won't freeze mobs)", PORT, e);
			}
		}, "Cybergrind hit-stop listener");
		thread.setDaemon(true);
		thread.start();
	}

	/** Each server tick, before the world ticks: freeze or unfreeze. */
	static void tick(final MinecraftServer server) {
		boolean wanted = Settings.get().hitStop && frozenUntil > System.nanoTime() && v1Driving();
		if (frozenUntil > System.nanoTime()) {
			lastFrozen = System.nanoTime();
		}
		ServerTickRateManager rate = server.tickRateManager();
		if (wanted && !frozeIt && !rate.isFrozen()) {
			rate.setFrozen(true);
			frozeIt = true;
			frozeAt = System.nanoTime();
		} else if (!wanted && frozeIt) {
			if (rate.isFrozen()) {
				rate.setFrozen(false);
			}
			frozeIt = false;
			Cybergrind.LOG.info("hit-stop: Minecraft's mobs were frozen {} ms with ULTRAKILL", (System.nanoTime() - frozeAt) / 1_000_000);
		}
	}

	/**
	 * V1 can't be hurt right now: it's dashing (ULTRAKILL's i-frames), just took a hit, or ULTRAKILL's time is stopped (a parry or
	 * an impact frame, and 0.15 s after). Falls and the void still count.
	 */
	static boolean invulnerable(final net.minecraft.world.damagesource.DamageSource source) {
		if (!v1Driving() || source.is(net.minecraft.world.damagesource.DamageTypes.FALL) || source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
			return false;
		}
		long now = System.nanoTime();
		if (frozenUntil > now) {
			lastFrozen = now;
			return true;
		}
		return invincibleUntil > now || now - lastFrozen < AFTER_FREEZE_NANOS;
	}

	/** True while this has the world frozen (a run's own clock waits too). */
	static boolean frozen() {
		return frozeIt;
	}

	/** The ULTRAKILL x Minecraft mod's "V1 has the controls" (false without it: then nothing freezes). */
	private static boolean v1Driving() {
		try {
			return V1_DRIVING != null && V1_DRIVING.getBoolean(null);
		} catch (IllegalAccessException e) {
			return false;
		}
	}

	private static Field find() {
		try {
			return Class.forName("dev.ukmc.ukpassthrough.UKPassthrough").getField("v1Driving");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
