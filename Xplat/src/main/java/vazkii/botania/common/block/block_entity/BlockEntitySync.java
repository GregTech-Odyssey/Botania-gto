/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.block_entity;

import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LazyFieldDataManager;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.util.ByteBufCodecExtends;

import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.xplat.XplatAbstractions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Server -> client sync of Botania block entities over DataSyncLib: requests are merged per tick and skipped while
// nobody
// tracks the chunk; annotated fields go field by field, and block entities still on NBT send only the keys that
// changed.
public final class BlockEntitySync {
	private static final String FULL_KEY = "botania_sync";
	private static final byte NONE = 0;
	private static final byte FULL = 1;
	private static final byte DELTA = 2;
	private static final int MAX_KEYS = 4096;
	private static final ReferenceLinkedOpenHashSet<BlockEntity> PENDING = new ReferenceLinkedOpenHashSet<>();

	public interface Holder extends IFieldDataHolder {
		BlockEntitySync botania$sync();
	}

	private final Holder owner;
	private final LazyFieldDataManager manager;
	@Nullable
	private final Consumer<CompoundTag> tagWriter;
	@Nullable
	private final Consumer<CompoundTag> tagReader;
	private CompoundTag baseline;
	private CompoundTag changed;
	private List<String> removed;

	public BlockEntitySync(Holder owner, @Nullable Consumer<CompoundTag> tagWriter, @Nullable Consumer<CompoundTag> tagReader) {
		this.owner = owner;
		this.manager = new LazyFieldDataManager(owner);
		this.tagWriter = tagWriter;
		this.tagReader = tagReader;
	}

	public LazyFieldDataManager manager() {
		return manager;
	}

	public static void request(BlockEntity be) {
		if (be.getLevel() instanceof ServerLevel && be instanceof Holder) {
			PENDING.add(be);
		}
	}

	public static void flush(ServerLevel level) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<BlockEntity> due = null;
		var it = PENDING.iterator();
		while (it.hasNext()) {
			var be = it.next();
			if (be.getLevel() == level || be.getLevel() == null) {
				it.remove();
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(be);
			}
		}
		if (due != null) {
			for (var be : due) {
				send(be);
			}
		}
	}

	private static void send(BlockEntity be) {
		if (be.isRemoved() || !(be.getLevel() instanceof ServerLevel level) || !(be instanceof Holder holder)) {
			return;
		}
		var players = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(be.getBlockPos()), false);
		if (players.isEmpty()) {
			return;
		}
		var sync = holder.botania$sync();
		var manager = sync.manager.get();
		if (sync.prepareDelta()) {
			manager.markAsChanged();
		}
		if (!manager.updateFieldDirtyFlags(LogicalSide.SERVER, true)) {
			return;
		}
		byte[] data = manager.writeToNetworkBuffer(LogicalSide.SERVER, false);
		if (data.length > 0) {
			XplatAbstractions.INSTANCE.sendBlockEntitySync(be, data);
		}
	}

	public CompoundTag writeFull() {
		var be = (BlockEntity) owner;
		if (PENDING.remove(be)) {
			send(be);
		}
		var tag = new CompoundTag();
		tag.putByteArray(FULL_KEY, manager.get().writeToNetworkBuffer(LogicalSide.SERVER, true));
		return tag;
	}

	public static boolean isFullSync(CompoundTag tag) {
		return tag.get(FULL_KEY) instanceof ByteArrayTag;
	}

	public void readFull(CompoundTag tag) {
		if (tag.get(FULL_KEY) instanceof ByteArrayTag data) {
			manager.get().readFromNetworkBuffer(LogicalSide.CLIENT, data.getAsByteArray());
		}
	}

	private boolean prepareDelta() {
		if (tagWriter == null) {
			return false;
		}
		var current = new CompoundTag();
		tagWriter.accept(current);
		var base = baseline;
		if (base == null) {
			baseline = current;
			changed = current;
			removed = null;
			return true;
		}
		CompoundTag diff = null;
		for (String key : current.getAllKeys()) {
			Tag now = current.get(key);
			if (!now.equals(base.get(key))) {
				if (diff == null) {
					diff = new CompoundTag();
				}
				diff.put(key, now);
			}
		}
		List<String> gone = null;
		for (String key : base.getAllKeys()) {
			if (!current.contains(key)) {
				if (gone == null) {
					gone = new ArrayList<>();
				}
				gone.add(key);
			}
		}
		changed = diff;
		removed = gone;
		if (diff == null && gone == null) {
			return false;
		}
		baseline = current;
		return true;
	}

	public void writeCustom(FriendlyByteBuf buf, boolean writeAll) {
		if (tagWriter == null) {
			buf.writeByte(NONE);
			return;
		}
		if (writeAll) {
			var current = new CompoundTag();
			tagWriter.accept(current);
			buf.writeByte(FULL);
			ByteBufCodecExtends.COMPOUND_TAG_CODEC.encode(buf, current);
			return;
		}
		var diff = changed;
		var gone = removed;
		changed = null;
		removed = null;
		if (diff == null && gone == null) {
			buf.writeByte(NONE);
			return;
		}
		buf.writeByte(DELTA);
		var keys = diff == null ? List.<String>of() : new ArrayList<>(diff.getAllKeys());
		buf.writeVarInt(keys.size());
		for (String key : keys) {
			buf.writeUtf(key);
			ByteBufCodecExtends.TAG_CODEC.encode(buf, diff.get(key));
		}
		var removedKeys = gone == null ? List.<String>of() : gone;
		buf.writeVarInt(removedKeys.size());
		for (String key : removedKeys) {
			buf.writeUtf(key);
		}
	}

	public void readCustom(FriendlyByteBuf buf) {
		byte mode = buf.readByte();
		if (mode == NONE) {
			return;
		}
		CompoundTag merged;
		if (mode == FULL) {
			merged = ByteBufCodecExtends.COMPOUND_TAG_CODEC.decode(buf);
		} else {
			merged = baseline == null ? new CompoundTag() : baseline.copy();
			int count = buf.readVarInt();
			checkCount(count);
			for (int i = 0; i < count; i++) {
				String key = buf.readUtf();
				merged.put(key, ByteBufCodecExtends.TAG_CODEC.decode(buf));
			}
			int gone = buf.readVarInt();
			checkCount(gone);
			for (int i = 0; i < gone; i++) {
				merged.remove(buf.readUtf());
			}
		}
		baseline = merged;
		if (tagReader != null) {
			tagReader.accept(merged.copy());
		}
	}

	private static void checkCount(int count) {
		if (count < 0 || count > MAX_KEYS) {
			throw new IllegalArgumentException("Too many synced keys: " + count);
		}
	}
}
