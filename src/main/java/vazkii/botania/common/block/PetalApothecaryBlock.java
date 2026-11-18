package vazkii.botania.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.NotNull;

/** 花药台方块：模型固定为装有水的外观，不再带方块实体。 */
public class PetalApothecaryBlock extends Block {

    private static final VoxelShape BASE = box(2, 0, 2, 14, 2, 14);
    private static final VoxelShape PILLAR = box(4, 2, 4, 12, 11, 12);
    private static final VoxelShape TOP = box(2, 11, 2, 14, 16, 14);
    private static final VoxelShape TOP_CUTOUT = box(3, 12, 3, 13, 16, 13);
    private static final VoxelShape SHAPE_INTERACT = Shapes.or(Shapes.or(BASE, PILLAR), TOP);
    private static final VoxelShape SHAPE = Shapes.join(SHAPE_INTERACT, TOP_CUTOUT, BooleanOp.ONLY_FIRST);

    public PetalApothecaryBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
                               @NotNull CollisionContext context) {
        return SHAPE;
    }

    @NotNull
    @Override
    public VoxelShape getInteractionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return SHAPE_INTERACT;
    }
}
