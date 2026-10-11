package dev.cybergrind.mixin;

import dev.cybergrind.Arena;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Arena witches only throw Harming (never Slowness, Poison or Weakness). */
@Mixin(Witch.class)
abstract class WitchMixin {
	@ModifyArg(method = "performRangedAttack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/alchemy/PotionContents;createItemStack(Lnet/minecraft/world/item/Item;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/item/ItemStack;"), index = 1)
	private Holder<Potion> cybergrind$onlyHarming(final Holder<Potion> potion) {
		return Arena.isArenaMob((Witch) (Object) this) ? Potions.HARMING : potion;
	}
}
