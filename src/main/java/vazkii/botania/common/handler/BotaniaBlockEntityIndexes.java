/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.handler;

import com.suried.gtoutils.spatial.BlockEntityIndex;
import com.suried.gtoutils.spatial.LevelStore;
import com.suried.gtoutils.spatial.ProbedBlockEntityIndex;

import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import vazkii.botania.api.block_entity.BindableSpecialFlowerBlockEntity;
import vazkii.botania.common.block.block_entity.CellularBlockEntity;
import vazkii.botania.common.block.block_entity.corporea.CorporeaIndexBlockEntity;
import vazkii.botania.common.entity.ManaSparkIndex;
import vazkii.botania.xplat.XplatAbstractions;

/**
 * 植物魔法全部按范围查找方块实体所用的索引与按维度存放的数据，由 GTOUtils 在区块增删方块实体时自动维护；模组构造时调用 {@link #init()} 完成创建。
 */
public final class BotaniaBlockEntityIndexes {

	public static final ProbedBlockEntityIndex EXOFLAME_HEATABLES = ProbedBlockEntityIndex.create(
			type -> AbstractFurnaceBlockEntity.class.isAssignableFrom(type) ? ProbedBlockEntityIndex.Kind.CANDIDATE : ProbedBlockEntityIndex.Kind.PROBE,
			be -> XplatAbstractions.INSTANCE.findExoflameHeatable(be.getLevel(), be.getBlockPos(), be.getBlockState(), be) != null,
			be -> !be.getLevel().isClientSide());
	public static final BlockEntityIndex<CellularBlockEntity> CELLS = BlockEntityIndex.create(CellularBlockEntity.class,
			cell -> !cell.getLevel().isClientSide());
	public static final BlockEntityIndex<CorporeaIndexBlockEntity> CORPOREA_INDEXES = BlockEntityIndex.create(CorporeaIndexBlockEntity.class);
	@SuppressWarnings("unchecked")
	public static final BlockEntityIndex<BindableSpecialFlowerBlockEntity<?>> BINDABLE_FLOWERS = BlockEntityIndex.create(
			(Class<BindableSpecialFlowerBlockEntity<?>>) (Class<?>) BindableSpecialFlowerBlockEntity.class);
	public static final BlockEntityIndex<Container> CONTAINERS = BlockEntityIndex.create(Container.class,
			container -> !((BlockEntity) container).getLevel().isClientSide());
	public static final LevelStore<ManaSparkIndex> SPARKS = LevelStore.create(ManaSparkIndex::new);

	private BotaniaBlockEntityIndexes() {}

	public static void init() {}
}
