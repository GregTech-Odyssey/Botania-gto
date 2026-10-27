package vazkii.botania.forge.integration.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.forge.CapabilityUtil;

final class ManaReceiverLookup {
	private final ServerLevel level;
	private final BlockPos pos;
	private final Direction side;
	@Nullable
	private BlockEntity cachedEntity;
	@Nullable
	private ManaReceiver cachedReceiver;

	ManaReceiverLookup(ServerLevel level, BlockPos pos, Direction side) {
		this.level = level;
		this.pos = pos.immutable();
		this.side = side;
	}

	@Nullable
	ManaReceiver find() {
		var entity = cachedEntity;
		if (entity != null && !entity.isRemoved()) {
			return cachedReceiver;
		}
		cachedEntity = null;
		cachedReceiver = null;
		if (!level.isLoaded(pos)) {
			return null;
		}
		var be = level.getBlockEntity(pos);
		var receiver = CapabilityUtil.findCapability(BotaniaForgeCapabilities.MANA_RECEIVER, level, pos, level.getBlockState(pos), be, side);
		if (be != null && receiver == be) {
			cachedEntity = be;
			cachedReceiver = receiver;
		}
		return receiver;
	}
}
