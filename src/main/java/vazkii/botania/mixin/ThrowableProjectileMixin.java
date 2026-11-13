/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import vazkii.botania.common.entity.ManaBurstEntity;

import java.util.function.Predicate;

@Mixin(ThrowableProjectile.class)
public class ThrowableProjectileMixin {
	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
	private double noDrag(double origScale) {
		// Do not apply drag to bursts
		if ((Object) this instanceof ManaBurstEntity) {
			return 1;
		}
		return origScale;
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
	private HitResult burstHitResult(Entity projectile, Predicate<Entity> filter) {
		if (projectile instanceof ManaBurstEntity burst && !burst.collidesWithEntities()) {
			Vec3 from = projectile.position();
			return projectile.level().clip(new ClipContext(from, from.add(projectile.getDeltaMovement()), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
		}
		return ProjectileUtil.getHitResultOnMoveVector(projectile, filter);
	}
}
