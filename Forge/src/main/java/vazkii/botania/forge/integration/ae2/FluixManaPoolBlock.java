package vazkii.botania.forge.integration.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import vazkii.botania.common.block.block_entity.BotaniaBlockEntities;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.block.mana.ManaPoolBlock;

import java.util.Collections;
import java.util.List;

public class FluixManaPoolBlock extends ManaPoolBlock {
	public FluixManaPoolBlock(Properties builder) {
		super(Variant.DEFAULT, builder);
	}

	@NotNull
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return new FluixManaPoolBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type == BotaniaBlockEntities.POOL && !level.isClientSide) {
			return (l, p, s, be) -> FluixManaPoolBlockEntity.replaceLegacy(l, p, s, be);
		}
		if (type != BotaniaAE2.fluixManaPoolType) {
			return null;
		}
		return level.isClientSide
				? (l, p, s, be) -> ManaPoolBlockEntity.clientTick(l, p, s, (ManaPoolBlockEntity) be)
				: (l, p, s, be) -> FluixManaPoolBlockEntity.serverTick(l, p, s, (FluixManaPoolBlockEntity) be);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof Player player && level.getBlockEntity(pos) instanceof FluixManaPoolBlockEntity pool) {
			pool.setOwner(player);
		}
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof FluixManaPoolBlockEntity pool ? (int) pool.getComparatorSignal() : 0;
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return Collections.singletonList(new ItemStack(this));
	}
}
