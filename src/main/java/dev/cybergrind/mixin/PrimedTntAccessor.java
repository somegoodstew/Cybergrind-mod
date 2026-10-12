package dev.cybergrind.mixin;

import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The size of a creeper's thrown TNT (Abilities: a small charge, not a full block of TNT). */
@Mixin(PrimedTnt.class)
public interface PrimedTntAccessor {
	@Accessor("explosionPower")
	void cybergrind$setExplosionPower(float power);
}
