package dev.cybergrind;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The enemy pool: five tiers, unlocked as the waves go up, each with its point cost. Every enemy is a /summon line, the same
 * NBT the datapack used and was played with. Summoning with NBT skips a mob's usual starting gear, so weapons are given here.
 */
final class Spawns {
	static final String TAG = "cg_mob";
	/** Spawned this wave and not set up yet. */
	static final String NEW = "cg_new";
	static final String BOSS = "cg_boss";
	/** Bow users (plain, unenchanted bows): slower, and no wave speed bonus. */
	static final String ARCHER = "cg_archer";
	/** Juggernauts: big jumps. */
	static final String HEAVY = "cg_heavy";
	/** Rapid shooters (Volley skeletons, Gunner pillagers): an extra arrow every 0.75 s that never hurts other enemies. */
	static final String RAPID = "cg_rapid";
	/** Phantom spiders: teleport behind the player every few seconds. */
	static final String BLINK = "cg_blink";

	/** Wave each tier unlocks at, and its cost in points. */
	static final int[] UNLOCK = {1, 3, 6, 10, 15};
	static final int[] COST = {1, 2, 3, 5, 8};

	record Enemy(String type, List<String> tags, String nbt) {
		String command() {
			String tagList = java.util.stream.Stream.concat(java.util.stream.Stream.of(TAG, NEW), tags.stream())
				.map(t -> "\"" + t + "\"").collect(Collectors.joining(","));
			return "summon minecraft:" + type + " ~ ~ ~ {Tags:[" + tagList + "],PersistenceRequired:1b" + (nbt.isEmpty() ? "" : "," + nbt) + "}";
		}
	}

	private static String gear(final Map<String, String> slots) {
		String eq = slots.entrySet().stream().map(e -> e.getKey() + ":{id:\"minecraft:" + e.getValue() + "\"}").collect(Collectors.joining(","));
		String dc = slots.keySet().stream().map(s -> s + ":0.0f").collect(Collectors.joining(","));
		return "equipment:{" + eq + "},drop_chances:{" + dc + "}";
	}

	private static final String FOREVER = "duration:-1,amplifier:0b,show_particles:0b";

	private static Enemy plain(final String type) {
		return new Enemy(type, List.of(), "");
	}

	private static Enemy armed(final String type, final String weapon) {
		return new Enemy(type, List.of(), gear(Map.of("mainhand", weapon)));
	}

	/** Archers walk slower (0.2 instead of 0.25) and skip the wave speed bonus, so they can't kite too fast. */
	private static Enemy archer(final String type) {
		return new Enemy(type, List.of(ARCHER), gear(Map.of("mainhand", "bow")) + ",attributes:[{id:\"minecraft:movement_speed\",base:0.2d}]");
	}

	private static final Enemy JUGGERNAUT = new Enemy("zombie", List.of(HEAVY),
		"CustomName:{text:\"Juggernaut\",color:\"gray\"},"
			+ gear(new java.util.LinkedHashMap<>(Map.of("head", "iron_helmet", "chest", "diamond_chestplate", "legs", "iron_leggings", "feet", "iron_boots", "mainhand", "iron_axe")))
			+ ",attributes:[{id:\"minecraft:max_health\",base:60d},{id:\"minecraft:knockback_resistance\",base:0.8d},{id:\"minecraft:scale\",base:1.25d},{id:\"minecraft:movement_speed\",base:0.3d}],Health:60f,IsBaby:0b");
	private static final Enemy BLADE = new Enemy("skeleton", List.of(),
		"CustomName:{text:\"Blade\",color:\"aqua\"}," + gear(Map.of("mainhand", "iron_sword")) + ",attributes:[{id:\"minecraft:movement_speed\",base:0.3d}]");
	/** Phantom spiders blink: every few seconds they teleport right behind you (Abilities). */
	private static final Enemy PHANTOM = new Enemy("spider", List.of(BLINK),
		"CustomName:{text:\"Phantom\",color:\"dark_gray\"},active_effects:[{id:\"minecraft:speed\"," + FOREVER + "}]");
	private static final Enemy BOMBER = new Enemy("creeper", List.of(),
		"CustomName:{text:\"Bomber\",color:\"red\"},powered:1b,Fuse:15s,ExplosionRadius:2b,active_effects:[{id:\"minecraft:speed\"," + FOREVER + "}]");

	private static final Enemy VOLLEY = new Enemy("skeleton", List.of(ARCHER, RAPID),
		"CustomName:{text:\"Volley\",color:\"yellow\"}," + gear(Map.of("mainhand", "bow")) + ",attributes:[{id:\"minecraft:movement_speed\",base:0.2d}]");
	private static final Enemy GUNNER = new Enemy("pillager", List.of(RAPID),
		"CustomName:{text:\"Gunner\",color:\"gold\"}," + gear(Map.of("mainhand", "crossbow")));

	/** Ghasts have 10 HP in vanilla: one hit from V1. These have 100. */
	private static final Enemy GHAST = new Enemy("ghast", List.of(),
		"attributes:[{id:\"minecraft:max_health\",base:100d}],Health:100f");

	static final List<List<Enemy>> TIERS = List.of(
		List.of(plain("zombie"), archer("skeleton"), plain("spider"), plain("husk")),
		List.of(plain("creeper"), archer("stray"), archer("bogged"), plain("cave_spider"), BLADE, PHANTOM),
		List.of(plain("witch"), armed("vindicator", "iron_axe"), armed("pillager", "crossbow"), plain("blaze"), JUGGERNAUT,
			GHAST, VOLLEY, GUNNER),
		List.of(plain("evoker"), armed("wither_skeleton", "stone_sword"), plain("breeze"), BOMBER),
		List.of(plain("ravager")));

	/** The Wither's helpers at half health. */
	static final Enemy WITHER_MINION = armed("wither_skeleton", "stone_sword");

	/** Bosses, in turn every 5th wave from wave 10: golem, warden, wither, then again with one more each round. */
	static final List<String> BOSSES = List.of("iron_golem", "warden", "wither");
	static final List<String> BOSS_NAMES = List.of("IRON GOLEM", "WARDEN", "WITHER");

	static String bossCommand(final String type) {
		return "summon minecraft:" + type + " ~ ~ ~ {Tags:[\"" + TAG + "\",\"" + NEW + "\",\"" + BOSS + "\"],PersistenceRequired:1b}";
	}

	private Spawns() {
	}
}
