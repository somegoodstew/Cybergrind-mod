package dev.cybergrind.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A text display's own setters (private in 26.3), for Beams: the heart inside the heal orb. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayInvoker {
	@Invoker("setText")
	void cybergrind$setText(Component text);

	@Invoker("setBackgroundColor")
	void cybergrind$setBackgroundColor(int argb);
}
