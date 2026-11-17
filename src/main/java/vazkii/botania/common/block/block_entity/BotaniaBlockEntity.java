package vazkii.botania.common.block.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.gto.datasynclib.blockentity.FieldDataHolderBlockEntity;
import com.gto.datasynclib.network.DataSyncNetwork;

/**
 * 植物魔法方块实体的公共基类。
 *
 * <p>
 * 持久化与同步交给 DataSyncLib：子类用 {@code @SaveToDisk} / {@code @SyncToClient} 标注字段，
 * {@link #markDispatchable()} 负责标脏并立即向客户端推送。
 * </p>
 */
public class BotaniaBlockEntity extends FieldDataHolderBlockEntity {

    public BotaniaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 标记内容已改动，并立即向周围客户端推送同步字段。 */
    public void markDispatchable() {
        setChanged();
        if (level != null && !level.isClientSide) {
            DataSyncNetwork.syncBlockEntityToClient(this);
        }
    }
}
