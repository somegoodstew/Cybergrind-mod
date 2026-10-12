package dev.cybergrind.mixin;

import dev.cybergrind.Arena;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Arena witches only throw lingering Harming potions (never Slowness, Poison or Weakness, never plain splash ones). */
@Mixin(Witch.class)
abstract class WitchMixin {
	@ModifyArg(method = "performRangedAttack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/alchemy/PotionContents;createItemStack(Lnet/minecraft/world/item/Item;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/item/ItemStack;"), index = 1)
	private Holder<Potion> cybergrind$onlyHarming(final Holder<Potion> potion) {
		return Arena.isArenaMob((Witch) (Object) this) ? Potions.HARMING : potion;
	}

	@ModifyArg(method = "performRangedAttack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/alchemy/PotionContents;createItemStack(Lnet/minecraft/world/item/Item;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/item/ItemStack;"), index = 0)
	private Item cybergrind$lingeringItem(final Item item) {
		return Arena.isArenaMob((Witch) (Object) this) ? Items.LINGERING_POTION : item;
	}

	@ModifyArg(method = "performRangedAttack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile$ProjectileFactory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"),
		index = 0)
	private Projectile.ProjectileFactory<?> cybergrind$lingeringProjectile(final Projectile.ProjectileFactory<?> factory) {
		return Arena.isArenaMob((Witch) (Object) this) ? (Projectile.ProjectileFactory<ThrownLingeringPotion>) ThrownLingeringPotion::new : factory;
	}
}
