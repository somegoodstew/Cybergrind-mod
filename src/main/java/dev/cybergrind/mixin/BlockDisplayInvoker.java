package dev.cybergrind.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The block a block display shows (private in 26.3), for Beams. */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayInvoker {
	@Invoker("setBlockState")
	void cybergrind$setBlockState(BlockState state);
}
