package dev.cybergrind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The enemy pool: five tiers, unlocked as the waves go up, each with its point cost, and the bosses. Every enemy is a /summon
 * line. Summoning with NBT skips a mob's usual starting gear, so weapons are given here. CATALOG lists them all for the debug
 * menu, with what they do.
 */
public final class Spawns {
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
	/** Phantom spiders: blink behind the player and spit webs. */
	static final String BLINK = "cg_blink";
	/** The Flying Elder Guardian: an elder guardian with ULTRAKILL's Providence attacks (Abilities). */
	static final String PROVIDENCE = "cg_providence";
	/** Drone: a floating guardian with ULTRAKILL's Drone behaviour (Abilities). */
	static final String DRONE = "cg_drone";

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

	/**
	 * One enemy for the debug menu: its name, where it shows up, what it does, its health if this mod changes it (otherwise the
	 * game's own value is shown), and its damage beyond plain melee.
	 */
	public record Entry(String key, String name, String type, String where, String description, Float health, String damage, String command) {
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

	private static final Enemy ZOMBIE = plain("zombie");
	private static final Enemy SKELETON = archer("skeleton");
	private static final Enemy SPIDER = plain("spider");
	private static final Enemy HUSK = plain("husk");
	private static final Enemy CREEPER = plain("creeper");
	private static final Enemy STRAY = archer("stray");
	private static final Enemy BOGGED = archer("bogged");
	private static final Enemy CAVE_SPIDER = plain("cave_spider");
	private static final Enemy BLADE = new Enemy("skeleton", List.of(),
		"CustomName:{text:\"Blade\",color:\"aqua\"}," + gear(Map.of("mainhand", "iron_sword")) + ",attributes:[{id:\"minecraft:movement_speed\",base:0.3d}]");
	private static final Enemy PHANTOM = new Enemy("spider", List.of(BLINK),
		"CustomName:{text:\"Phantom\",color:\"dark_gray\"},active_effects:[{id:\"minecraft:speed\"," + FOREVER + "}]");
	/** A guardian, smaller and floating; Abilities flies it, fires its bolts and makes it dive when killed. */
	private static final Enemy DRONE_ENEMY = new Enemy("guardian", List.of(DRONE),
		"CustomName:{text:\"Drone\",color:\"dark_purple\"},NoGravity:1b,"
			+ "attributes:[{id:\"minecraft:max_health\",base:20d},{id:\"minecraft:scale\",base:0.8d}],Health:20f");
	private static final Enemy WITCH = plain("witch");
	private static final Enemy VINDICATOR = armed("vindicator", "iron_axe");
	private static final Enemy PILLAGER = armed("pillager", "crossbow");
	private static final Enemy BLAZE = plain("blaze");
	private static final Enemy JUGGERNAUT = new Enemy("zombie", List.of(HEAVY),
		"CustomName:{text:\"Juggernaut\",color:\"gray\"},"
			+ gear(new java.util.LinkedHashMap<>(Map.of("head", "iron_helmet", "chest", "diamond_chestplate", "legs", "iron_leggings", "feet", "iron_boots", "mainhand", "iron_axe")))
			+ ",attributes:[{id:\"minecraft:max_health\",base:60d},{id:\"minecraft:knockback_resistance\",base:0.8d},{id:\"minecraft:scale\",base:1.25d},{id:\"minecraft:movement_speed\",base:0.3d}],Health:60f,IsBaby:0b");
	/** Ghasts have 10 HP in vanilla: one hit from V1. These have 100. */
	private static final Enemy GHAST = new Enemy("ghast", List.of(),
		"attributes:[{id:\"minecraft:max_health\",base:100d}],Health:100f");
	private static final Enemy VOLLEY = new Enemy("skeleton", List.of(ARCHER, RAPID),
		"CustomName:{text:\"Volley\",color:\"yellow\"}," + gear(Map.of("mainhand", "bow")) + ",attributes:[{id:\"minecraft:movement_speed\",base:0.2d}]");
	private static final Enemy GUNNER = new Enemy("pillager", List.of(RAPID),
		"CustomName:{text:\"Gunner\",color:\"gold\"}," + gear(Map.of("mainhand", "crossbow")));
	private static final Enemy EVOKER = plain("evoker");
	/** Twice a wither skeleton's health (40). */
	private static final Enemy WITHER_SKELETON = new Enemy("wither_skeleton", List.of(),
		gear(Map.of("mainhand", "stone_sword")) + ",attributes:[{id:\"minecraft:max_health\",base:40d}],Health:40f");
	private static final Enemy BREEZE = plain("breeze");
	private static final Enemy BOMBER = new Enemy("creeper", List.of("cg_bomber"),
		"CustomName:{text:\"Bomber\",color:\"red\"},powered:1b,Fuse:15s,ExplosionRadius:2b,active_effects:[{id:\"minecraft:speed\"," + FOREVER + "}]");
	/** An elder guardian, smaller and floating; Abilities flies it and fires its beams. */
	private static final Enemy PROVIDENCE_ENEMY = new Enemy("elder_guardian", List.of(PROVIDENCE),
		"CustomName:{text:\"Flying Elder Guardian\",color:\"light_purple\"},NoGravity:1b,"
			+ "attributes:[{id:\"minecraft:max_health\",base:70d},{id:\"minecraft:scale\",base:0.7d}],Health:70f");
	private static final Enemy RAVAGER = plain("ravager");

	static final List<List<Enemy>> TIERS = List.of(
		List.of(ZOMBIE, SKELETON, SPIDER, HUSK, PILLAGER),
		List.of(CREEPER, STRAY, BOGGED, CAVE_SPIDER, BLADE, PHANTOM, DRONE_ENEMY),
		List.of(WITCH, VINDICATOR, BLAZE, JUGGERNAUT, GHAST, VOLLEY, GUNNER),
		List.of(EVOKER, WITHER_SKELETON, BREEZE, BOMBER, PROVIDENCE_ENEMY),
		List.of(RAVAGER));

	/** The Wither's helpers at half health. */
	static final Enemy WITHER_MINION = WITHER_SKELETON;

	/** Bosses, in turn every 5th wave from wave 10: golem, warden, wither, then again with one more each round. */
	static final List<String> BOSSES = List.of("iron_golem", "warden", "wither");
	static final List<String> BOSS_NAMES = List.of("IRON GOLEM", "WARDEN", "WITHER");

	/** Bosses' own changes: the Warden has 300 health instead of 500. */
	private static final Map<String, String> BOSS_NBT = Map.of("warden", "attributes:[{id:\"minecraft:max_health\",base:300d}],Health:300f");

	static String bossCommand(final String type) {
		String extra = BOSS_NBT.getOrDefault(type, "");
		return "summon minecraft:" + type + " ~ ~ ~ {Tags:[\"" + TAG + "\",\"" + NEW + "\",\"" + BOSS + "\"],PersistenceRequired:1b"
			+ (extra.isEmpty() ? "" : "," + extra) + "}";
	}

	/** Every enemy, for the debug menu. */
	public static final List<Entry> CATALOG = catalog();

	private static List<Entry> catalog() {
		List<Entry> c = new ArrayList<>();
		String t1 = "Tier 1 - wave 1+ (cost 1)";
		String t2 = "Tier 2 - wave 3+ (cost 2)";
		String t3 = "Tier 3 - wave 6+ (cost 3)";
		String t4 = "Tier 4 - wave 10+ (cost 5)";
		String t5 = "Tier 5 - wave 15+ (cost 8)";
		add(c, "zombie", "Zombie", t1, ZOMBIE, null, "Walks at you and hits.", "-");
		add(c, "skeleton", "Skeleton", t1, SKELETON, null, "Archer: plain bow (faster arrows), walks slower.", "Arrows: about 2-5");
		add(c, "spider", "Spider", t1, SPIDER, null, "Fast climber; attacks in daylight too.", "-");
		add(c, "husk", "Husk", t1, HUSK, null, "Desert zombie (its Hunger is removed during runs).", "-");
		add(c, "creeper", "Creeper", t2, CREEPER, null,
			"Lobs a small lit TNT at you every 3 s from 5-20 blocks (fuse 1.5 s; breaks terrain, never hurts other enemies), and walks up and explodes as usual.",
			"TNT: explosion power 2; own blast: up to about 40 at point blank (power 3)");
		add(c, "stray", "Stray", t2, STRAY, null, "Archer (its Slowness arrows' effect is removed).", "Arrows: about 2-5");
		add(c, "bogged", "Bogged", t2, BOGGED, null, "Archer (its Poison arrows' effect is removed).", "Arrows: about 2-5");
		add(c, "cave_spider", "Cave Spider", t2, CAVE_SPIDER, null, "Small fast spider (Poison removed).", "-");
		add(c, "blade", "Blade", t2, BLADE, null, "Skeleton with an iron sword: rushes you in melee.", "Melee with iron sword");
		add(c, "phantom", "Phantom", t2, PHANTOM, null,
			"Spider with Speed. Every 5 s it blinks to 3 blocks behind you; every 4 s it spits a web that lands where you stood (the web melts after 5 s).",
			"Webs slow you; no damage");
		add(c, "drone", "Drone", t2, DRONE_ENEMY, 20f,
			"Based on ULTRAKILL's Drone. Hovers at medium range (closer when you're far, backs off fast when you're close), dashes side to side, "
				+ "and every 2-3 s charges (orange sparks, a chirp) and fires 3 small fireballs one after another, semi-auto. Its killing blow "
				+ "sends it hurtling straight the way it was looking (it doesn't follow you) to explode on impact; shoot it on the way to blow it up early. No thorns.",
			"Fireballs: 5 + fire each; dive: explosion power 2");
		add(c, "witch", "Witch", t3, WITCH, null,
			"Throws only lingering Harming potions: a bright red cloud, 5 blocks wide, for 5 s (never hurts other enemies); drinks to heal.",
			"Harming cloud: 6 a second inside it");
		add(c, "vindicator", "Vindicator", t3, VINDICATOR, null, "Axe illager.", "Melee with iron axe");
		add(c, "pillager", "Pillager", t1, PILLAGER, null, "Crossbow illager.", "Crossbow bolts");
		add(c, "blaze", "Blaze", t3, BLAZE, null,
			"Keeps flying about you fast (a new spot every 2 s) and fires an extra fireball every second on top of its volleys (never hurt other enemies).",
			"Fireballs: 5 + fire");
		add(c, "juggernaut", "Juggernaut", t3, JUGGERNAUT, 60f,
			"Big armoured zombie: iron armour, diamond chestplate, iron axe, hard to knock back, jumps about 8 blocks.", "Melee with iron axe");
		add(c, "ghast", "Ghast", t3, GHAST, 100f,
			"Hovers about 10 blocks away and 7 up; an extra fireball every 1.5 s. Fireballs: size 2, about 1.4x speed, never hurt other enemies.",
			"Fireball: 6 on hit + explosion");
		add(c, "volley", "Volley", t3, VOLLEY, null, "Skeleton: an extra arrow every 0.75 s while it sees you.", "Arrows: about 3-5 each");
		add(c, "gunner", "Gunner", t3, GUNNER, null, "Pillager: an extra arrow every 0.75 s while it sees you.", "Arrows: about 3-5 each");
		add(c, "evoker", "Evoker", t4, EVOKER, null, "Fangs and vexes; its vexes fly faster (they're cleared each wave).", "Fangs: 6; vexes melee");
		add(c, "wither_skeleton", "Wither Skeleton", t4, WITHER_SKELETON, 40f, "Stone sword, twice the health (its Wither effect is removed).", "Melee with stone sword");
		add(c, "breeze", "Breeze", t4, BREEZE, null, "Jumps around, fires a wind charge at you every 1.25 s on top of its own.", "Wind charge: 1 + knockback");
		add(c, "bomber", "Bomber", t4, BOMBER, null, "Charged creeper (blue aura) with Speed and a short fuse; its blast is power 3, not the charged 4.",
			"Explosion: power 3");
		add(c, "providence", "Flying Elder Guardian", t4, PROVIDENCE_ENEMY, 70f,
			"Based on ULTRAKILL's Providence. Dashes twice around you, then charges 1 s (a magenta core grows, a thin beam tracks you; both turn white 0.3 s before it fires) and fires there: "
				+ "a CROSS (a giant + of beams) or a SPIRAL (4 beams that close in). No thorns. Dying, it drops a pink glass box with a heart inside that floats slowly down; "
				+ "touch it to heal to full.",
			"Beams: 6 per hit (armour doesn't help)");
		add(c, "ravager", "Ravager", t5, RAVAGER, null, "Charges: dashes at you every 4 s from 5-20 blocks.", "-");
		String boss = "Boss - every 5th wave from 10";
		addBoss(c, "iron_golem", "Iron Golem", boss,
			"Fights like ULTRAKILL's Cerberus. Over 12 blocks away: winds up and throws a fireball that bends toward you. Mid range: a tackle dash. "
				+ "Close: stomps, sending a shockwave ring along the ground (jump it). If another golem dies, it's Enraged: faster, quicker attacks.",
			"Fireball: 6 + explosion; tackle: 7; shockwave: 6; punch: about 7-21");
		addBoss(c, "warden", "Warden", boss, 300f,
			"Always hunts you (walks straight at you); 300 health. Every 8 s, when you're within 6 blocks, it stops and a glowing ring spreads out on the ground "
				+ "around it (0.7 s): get outside it before it slams. Every 5 s at 5-28 blocks, stops, winds up its sonic boom (1.5 s) and fires it: a stream of "
				+ "rings that flies at where you were (dodge it).",
			"Slam: 5; sonic boom: 10 (through armour)");
		addBoss(c, "wither", "Wither", boss, "Flies, skulls (never hurt other enemies). At half health calls 3 wither skeletons.", "Skulls: 8 + explosion");
		return List.copyOf(c);
	}

	private static void add(final List<Entry> c, final String key, final String name, final String where, final Enemy enemy, final Float health,
		final String description, final String damage) {
		c.add(new Entry(key, name, enemy.type(), where, description, health, damage, enemy.command()));
	}

	private static void addBoss(final List<Entry> c, final String type, final String name, final String where, final String description, final String damage) {
		addBoss(c, type, name, where, null, description, damage);
	}

	private static void addBoss(final List<Entry> c, final String type, final String name, final String where, final Float health, final String description,
		final String damage) {
		c.add(new Entry(type, name, type, where, description, health, damage, bossCommand(type)));
	}

	private Spawns() {
	}
}
