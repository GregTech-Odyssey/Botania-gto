/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.flower.functional;

import com.suried.gtoutils.spatial.BlockOrder;
import com.suried.gtoutils.spatial.BlockRange;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import vazkii.botania.api.block.ExoflameHeatable;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.common.block.BotaniaFlowerBlocks;
import vazkii.botania.common.handler.BotaniaBlockEntityIndexes;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.ArrayList;
import java.util.List;

public class ExoflameBlockEntity extends FunctionalFlowerBlockEntity {
	private static final int RANGE = 5;
	private static final int RANGE_Y = 2;
	private static final int COST = 300;
	private static final List<BlockEntity> SCRATCH = new ArrayList<>();

	public ExoflameBlockEntity(BlockPos pos, BlockState state) {
		super(BotaniaFlowerBlocks.EXOFLAME, pos, state);
	}

	@Override
	public void tickFlower() {
		super.tickFlower();

		if (getLevel().isClientSide || getMana() <= 2) {
			return;
		}

		boolean did = false;

		List<BlockEntity> found = SCRATCH;
		BotaniaBlockEntityIndexes.EXOFLAME_HEATABLES.query(getLevel(), getEffectivePos(), BlockRange.box(RANGE, RANGE_Y, RANGE), null, found);
		BlockOrder.sortBetweenClosed(found, BlockEntity::getBlockPos);
		try {
			for (BlockEntity tile : found) {
				BlockPos pos = tile.getBlockPos();
				ExoflameHeatable heatable = XplatAbstractions.INSTANCE.findExoflameHeatable(getLevel(), pos, getLevel().getBlockState(pos), tile);
				if (heatable != null) {
					if (heatable.canSmelt() && getMana() >= COST) {
						if (heatable.getBurnTime() < 2) {
							heatable.boostBurnTime();
							addMana(-COST);
							did = true;
						}

						if (ticksExisted % 2 == 0) {
							heatable.boostCookTime();
						}

						if (getMana() <= 0) {
							break;
						}
					}
				}
			}
		} finally {
			found.clear();
		}
		if (did) {
			sync();
		}
	}

	@Override
	public RadiusDescriptor getRadius() {
		return RadiusDescriptor.Rectangle.square(getEffectivePos(), RANGE);
	}

	@Override
	public int getMaxMana() {
		return 300;
	}

	@Override
	public int getColor() {
		return 0x661600;
	}

}
