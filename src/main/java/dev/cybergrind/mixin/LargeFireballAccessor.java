package dev.cybergrind.mixin;

import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets a ghast fireball's explosion size (Abilities: arena ghasts' fireballs are bigger). */
@Mixin(LargeFireball.class)
public interface LargeFireballAccessor {
	@Accessor("explosionPower")
	void cybergrind$setExplosionPower(int power);
}
