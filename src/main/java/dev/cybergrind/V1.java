package dev.cybergrind;

import java.lang.reflect.Field;

/**
 * Whether V1 is dead in ULTRAKILL, from the ULTRAKILL x Minecraft mod (its public UKPassthrough.v1Dead: true from V1's death
 * until it respawns). With V1 the Minecraft player never dies, so this is how a V1 death is seen. False without that mod.
 */
final class V1 {
	private static final Field DEAD = find();

	private V1() {
	}

	static boolean dead() {
		try {
			return DEAD != null && DEAD.getBoolean(null);
		} catch (IllegalAccessException e) {
			return false;
		}
	}

	private static Field find() {
		try {
			return Class.forName("dev.ukmc.ukpassthrough.UKPassthrough").getField("v1Dead");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
