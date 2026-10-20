/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import vazkii.botania.common.entity.ManaSparkIndex;
import vazkii.botania.common.world.SkyblockWorldEvents;
import vazkii.botania.xplat.XplatAbstractions;

@Mixin(ServerLevel.class)
public class ServerLevelMixin implements ManaSparkIndex.Holder {
	@Unique
	private ManaSparkIndex botania$sparkIndex;

	@Inject(at = @At("RETURN"), method = "addPlayer")
	private void onEntityAdd(ServerPlayer entity, CallbackInfo ci) {
		if (XplatAbstractions.INSTANCE.gogLoaded()) {
			SkyblockWorldEvents.syncGogStatus(entity);
		}
	}

	@Override
	public ManaSparkIndex botania$getSparkIndex() {
		var index = botania$sparkIndex;
		if (index == null) {
			botania$sparkIndex = index = new ManaSparkIndex();
		}
		return index;
	}
}
