package vazkii.botania.common.block.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import vazkii.botania.common.block.PylonBlock;

/**
 * 水晶方块实体。
 *
 * <p>
 * 保留原模组的激活状态与中心坐标，供盖亚召唤与传送门联动使用；渲染动画由客户端渲染器驱动。
 * </p>
 */
public class PylonBlockEntity extends BotaniaBlockEntity {

    @com.gto.datasynclib.annotations.SaveToDisk
    @com.gto.datasynclib.annotations.SyncToClient
    private boolean activated = false;
    @com.gto.datasynclib.annotations.SaveToDisk
    @com.gto.datasynclib.annotations.SyncToClient
    private BlockPos centerPos = BlockPos.ZERO;
    private int ticks = 0;

    public PylonBlockEntity(BlockPos pos, BlockState state) {
        this(BotaniaBlockEntities.PYLON.get(), pos, state);
    }

    public PylonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.0, 2.0, 1.0);
    }

    public PylonBlock.Variant variant() {
        return getBlockState().getBlock() instanceof PylonBlock pylon ? pylon.variant : PylonBlock.Variant.MANA;
    }

    public boolean isActivated() {
        return activated;
    }

    public BlockPos getCenterPos() {
        return centerPos;
    }

    public void setActivated(boolean activated, BlockPos centerPos) {
        this.activated = activated;
        if (centerPos != null) {
            this.centerPos = centerPos;
        }
        markDispatchable();
    }

    public static void commonTick(Level level, BlockPos pos, BlockState state, PylonBlockEntity self) {
        ++self.ticks;
    }
}
