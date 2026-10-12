package dev.cybergrind.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A display entity's own setters (private in 26.3), for Beams: solid beams drawn with block displays. */
@Mixin(Display.class)
public interface DisplayInvoker {
	@Invoker("setTransformation")
	void cybergrind$setTransformation(Transformation transformation);

	@Invoker("setTransformationInterpolationDuration")
	void cybergrind$setInterpolationDuration(int ticks);

	@Invoker("setTransformationInterpolationDelay")
	void cybergrind$setInterpolationDelay(int ticks);

	@Invoker("setBrightnessOverride")
	void cybergrind$setBrightness(Brightness brightness);

	@Invoker("setGlowColorOverride")
	void cybergrind$setGlowColor(int color);

	@Invoker("setViewRange")
	void cybergrind$setViewRange(float range);

	@Invoker("setBillboardConstraints")
	void cybergrind$setBillboard(Display.BillboardConstraints billboard);
}
