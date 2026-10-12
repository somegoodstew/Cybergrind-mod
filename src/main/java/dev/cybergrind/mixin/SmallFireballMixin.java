package dev.cybergrind.mixin;

import dev.cybergrind.Arena;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Drone's bolts (small fireballs) just end on a block: no fire left on the ground. */
@Mixin(SmallFireball.class)
abstract class SmallFireballMixin {
	@Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
	private void cybergrind$noFire(final BlockHitResult hit, final CallbackInfo ci) {
		Entity owner = ((SmallFireball) (Object) this).getOwner();
		if (owner != null && Arena.isArenaMob(owner) && owner.entityTags().contains("cg_drone")) {
			ci.cancel();
		}
	}
}
