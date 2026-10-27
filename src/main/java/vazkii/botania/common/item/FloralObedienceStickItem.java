/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item;

import com.suried.gtoutils.spatial.BlockOrder;
import com.suried.gtoutils.spatial.BlockRange;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

import vazkii.botania.api.block_entity.BindableSpecialFlowerBlockEntity;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.mana.ManaCollector;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.common.handler.BotaniaBlockEntityIndexes;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.ArrayList;
import java.util.List;

public class FloralObedienceStickItem extends Item {

	public FloralObedienceStickItem(Properties props) {
		super(props);
	}

	@NotNull
	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		return applyStick(world, pos)
				? InteractionResult.sidedSuccess(world.isClientSide())
				: InteractionResult.PASS;
	}

	public static boolean applyStick(Level world, BlockPos pos) {
		var receiver = XplatAbstractions.INSTANCE.findManaReceiver(world, pos, null);
		if (receiver instanceof ManaPool || receiver instanceof ManaCollector) {
			int range = receiver instanceof ManaPool ? FunctionalFlowerBlockEntity.LINK_RANGE : GeneratingFlowerBlockEntity.LINK_RANGE;

			List<BindableSpecialFlowerBlockEntity<?>> flowers = new ArrayList<>();
			BotaniaBlockEntityIndexes.BINDABLE_FLOWERS.query(world, pos, BlockRange.sphere(range), bindable -> bindable.wouldBeValidBinding(pos), flowers);
			BlockOrder.sortBetweenClosed(flowers, BindableSpecialFlowerBlockEntity::getBlockPos);
			for (BindableSpecialFlowerBlockEntity<?> bindable : flowers) {
				bindable.setBindingPos(pos);
				WandOfTheForestItem.doParticleBeamWithOffset(world, bindable.getBlockPos(), pos);
			}

			return true;
		}
		if (world.getBlockEntity(pos) instanceof BindableSpecialFlowerBlockEntity<?> bindableFlower) {
			bindableFlower.setBindingPos(null);
			return true;
		}

		return false;
	}
}
