/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.block_entity;

import com.gto.datasynclib.FieldDataManager;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BotaniaBlockEntity extends BlockEntity implements BlockEntitySync.Holder {
	private BlockEntitySync sync;

	public BotaniaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		writePacketNBT(tag);
	}

	@NotNull
	@Override
	public final CompoundTag getUpdateTag() {
		return botania$sync().writeFull();
	}

	@Override
	public void load(@NotNull CompoundTag tag) {
		super.load(tag);
		if (BlockEntitySync.isFullSync(tag)) {
			botania$sync().readFull(tag);
		} else {
			readPacketNBT(tag);
		}
	}

	public void writePacketNBT(CompoundTag cmp) {}

	public void readPacketNBT(CompoundTag cmp) {}

	protected void syncToClients() {
		if (level != null && !level.isClientSide) {
			BlockEntitySync.request(this);
		}
	}

	protected boolean syncsByFields() {
		return false;
	}

	@Override
	public BlockEntitySync botania$sync() {
		var s = sync;
		if (s == null) {
			boolean byFields = syncsByFields();
			sync = s = new BlockEntitySync(this, byFields ? null : this::writePacketNBT, byFields ? null : this::readPacketNBT);
		}
		return s;
	}

	@Override
	public FieldDataManager getFieldDataManager() {
		return botania$sync().manager().get();
	}

	@Override
	public void writeCustomSyncData(FriendlyByteBuf buf, boolean writeAll) {
		botania$sync().writeCustom(buf, writeAll);
	}

	@Override
	public void readCustomSyncData(FriendlyByteBuf buf) {
		botania$sync().readCustom(buf);
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
