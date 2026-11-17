package vazkii.botania.common.block.block_entity.mana;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import vazkii.botania.common.block.block_entity.BotaniaBlockEntities;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntity;

/**
 * 魔力发射器方块实体。
 *
 * <p>
 * 仅保留方块模型与渲染所需的视觉数据；不再接收、存储或发射魔力。
 * </p>
 */
public class ManaSpreaderBlockEntity extends BotaniaBlockEntity {

    public ManaSpreaderBlockEntity(BlockPos pos, BlockState state) {
        this(BotaniaBlockEntities.MANA_SPREADER.get(), pos, state);
    }

    public ManaSpreaderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 渲染用的固定填充量，使发射器核心保持点亮。 */
    public long getMana() {
        return 1000L;
    }
}
