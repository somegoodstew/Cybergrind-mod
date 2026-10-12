package dev.cybergrind.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A Bomber stays a charged creeper (the blue aura) but blows up at power 3, not the charged 4: several at once were too deadly. */
@Mixin(Creeper.class)
abstract class CreeperMixin {
	@ModifyArg(method = "explodeCreeper", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"),
		index = 4)
	private float cybergrind$bomberPower(final float power) {
		return ((Creeper) (Object) this).entityTags().contains("cg_bomber") ? 3.0F : power;
	}
}
