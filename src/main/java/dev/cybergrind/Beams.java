package dev.cybergrind;

import com.mojang.math.Transformation;
import dev.cybergrind.mixin.BlockDisplayInvoker;
import dev.cybergrind.mixin.DisplayInvoker;
import dev.cybergrind.mixin.TextDisplayInvoker;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Solid beams and shapes drawn with block displays (no particles): a block stretched into a thin box between two points, full
 * bright, with a glowing outline. Each one is a display entity tagged "cg_fx" (removed with the run's enemies). Server thread.
 */
final class Beams {
	static final String FX = "cg_fx";

	private Beams() {
	}

	/** A new, empty display at a spot (shown once it's given a shape). */
	static Display.BlockDisplay create(final ServerLevel level, final Vec3 at) {
		Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("block_display")).create(level, EntitySpawnReason.COMMAND);
		Display.BlockDisplay display = (Display.BlockDisplay) entity;
		display.setPos(at);
		display.addTag(FX);
		DisplayInvoker invoker = (DisplayInvoker) display;
		invoker.cybergrind$setBrightness(Brightness.FULL_BRIGHT);
		invoker.cybergrind$setViewRange(4);
		level.addFreshEntity(display);
		return display;
	}

	/**
	 * A beam from one point to another, this thick, made of this block, outlined in this colour (0: no outline). Moves smoothly
	 * from its last shape over a tick.
	 */
	static void beam(final Display.BlockDisplay display, final Vec3 from, final Vec3 to, final float width, final BlockState block, final int glow) {
		Vec3 along = to.subtract(from);
		float length = (float) along.length();
		if (length < 1.0E-3) {
			return;
		}
		Vector3f direction = new Vector3f((float) along.x, (float) along.y, (float) along.z).normalize();
		// the block's box runs 0..1 on each axis: thin on x and y, the beam's length on z, turned from +z to the beam's direction
		Quaternionf turn = new Quaternionf().rotationTo(0, 0, 1, direction.x, direction.y, direction.z);
		Vector3f centring = turn.transform(new Vector3f(-width / 2, -width / 2, 0));
		Vec3 start = from.subtract(display.position());
		Vector3f translation = new Vector3f((float) start.x, (float) start.y, (float) start.z).add(centring);
		shape(display, new Transformation(translation, turn, new Vector3f(width, width, length), null), block, glow);
	}

	/** A cube of this size centred on a point, turned by this angle (radians) about the vertical. */
	static void cube(final Display.BlockDisplay display, final Vec3 center, final float size, final float spin, final BlockState block, final int glow) {
		Quaternionf turn = new Quaternionf().rotationY(spin).rotateX(spin * 0.6F);
		Vector3f centring = turn.transform(new Vector3f(-size / 2, -size / 2, -size / 2));
		Vec3 at = center.subtract(display.position());
		Vector3f translation = new Vector3f((float) at.x, (float) at.y, (float) at.z).add(centring);
		shape(display, new Transformation(translation, turn, new Vector3f(size, size, size), null), block, glow);
	}

	/** A big red heart (a text display, always facing you, no background) at a spot: the heal orb's inside. */
	static Display.TextDisplay heart(final ServerLevel level, final Vec3 at) {
		Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("text_display")).create(level, EntitySpawnReason.COMMAND);
		Display.TextDisplay heart = (Display.TextDisplay) entity;
		heart.setPos(at);
		heart.addTag(FX);
		DisplayInvoker invoker = (DisplayInvoker) heart;
		invoker.cybergrind$setBrightness(Brightness.FULL_BRIGHT);
		invoker.cybergrind$setViewRange(4);
		invoker.cybergrind$setBillboard(Display.BillboardConstraints.CENTER);
		((TextDisplayInvoker) heart).cybergrind$setText(Component.literal("❤").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		((TextDisplayInvoker) heart).cybergrind$setBackgroundColor(0);
		level.addFreshEntity(heart);
		return heart;
	}

	/** Moves a display's drawing to centre on a point (its entity stays put), at this scale. Text sits on its bottom edge, so it's lowered. */
	static void centre(final Display display, final Vec3 center, final float scale, final float drop) {
		Vec3 at = center.subtract(display.position());
		DisplayInvoker invoker = (DisplayInvoker) display;
		invoker.cybergrind$setTransformation(new Transformation(new Vector3f((float) at.x, (float) at.y - drop, (float) at.z), null,
			new Vector3f(scale, scale, scale), null));
		invoker.cybergrind$setInterpolationDelay(0);
		invoker.cybergrind$setInterpolationDuration(1);
	}

	private static void shape(final Display.BlockDisplay display, final Transformation transformation, final BlockState block, final int glow) {
		DisplayInvoker invoker = (DisplayInvoker) display;
		((BlockDisplayInvoker) display).cybergrind$setBlockState(block);
		invoker.cybergrind$setTransformation(transformation);
		invoker.cybergrind$setInterpolationDelay(0);
		invoker.cybergrind$setInterpolationDuration(1);
		display.setGlowingTag(glow != 0);
		if (glow != 0) {
			invoker.cybergrind$setGlowColor(glow);
		}
	}
}
