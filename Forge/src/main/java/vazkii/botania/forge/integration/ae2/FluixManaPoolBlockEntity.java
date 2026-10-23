package vazkii.botania.forge.integration.ae2;

import com.gto.datasynclib.annotations.SyncToClient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import vazkii.botania.common.block.block_entity.BlockEntitySync;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;


import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;

/**
 * Mana pool whose contents are the mana of the attached ME network; every read asks the network so that nested reads
 * through a storage bus hit AE's reentrancy guard instead of a stale copy, and the display copy is synced at most every 10 ticks.
 */
public class FluixManaPoolBlockEntity extends ManaPoolBlockEntity implements IInWorldGridNodeHost, IActionHost {
	private static final IGridNodeListener<FluixManaPoolBlockEntity> LISTENER = (owner, node) -> owner.setChanged();

	private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, LISTENER)
			.setFlags()
			.setIdlePowerUsage(1)
			.setInWorldNode(true)
			.setVisualRepresentation(new ItemStack(BotaniaAE2.fluixManaPool));
	private final IActionSource source = IActionSource.ofMachine(this);

	@SyncToClient
	private long shownMana;
	@SyncToClient
	private long shownCapacity;

	private boolean comparatorDirty;
	private int ticks;

	public FluixManaPoolBlockEntity(BlockPos pos, BlockState state) {
		super(BotaniaAE2.fluixManaPoolType, pos, state);
	}

	public void setOwner(Player player) {
		mainNode.setOwningPlayer(player);
	}

	@Nullable
	private MEStorage storage() {
		var grid = mainNode.getGrid();
		return grid != null && mainNode.isActive() ? grid.getStorageService().getInventory() : null;
	}

	private long stored() {
		var inventory = storage();
		return inventory == null ? 0 : inventory.extract(ManaKey.KEY, Long.MAX_VALUE, Actionable.SIMULATE, source);
	}

	private long space() {
		var inventory = storage();
		return inventory == null ? 0 : inventory.insert(ManaKey.KEY, Long.MAX_VALUE, Actionable.SIMULATE, source);
	}

	private static long capacity(long stored, long space) {
		long total = stored + space;
		return total < 0 ? Long.MAX_VALUE : total;
	}

	boolean isOnGrid(@Nullable IGrid grid) {
		return grid != null && mainNode.getGrid() == grid;
	}

	private boolean client() {
		return level != null && level.isClientSide;
	}

	@Override
	public long getCurrentManaLong() {
		if (client()) {
			return shownMana;
		}
		return stored();
	}

	@Override
	public long getMaxManaLong() {
		if (client()) {
			return shownCapacity;
		}
		return capacity(stored(), space());
	}

	@Override
	public long getAvailableSpaceForManaLong() {
		if (client()) {
			return Math.max(0, shownCapacity - shownMana);
		}
		return space();
	}

	@Override
	public int getCurrentMana() {
		return (int) Math.min(Integer.MAX_VALUE, getCurrentManaLong());
	}

	@Override
	public int getMaxMana() {
		return (int) Math.min(Integer.MAX_VALUE, getMaxManaLong());
	}

	@Override
	public int getAvailableSpaceForMana() {
		return (int) Math.min(Integer.MAX_VALUE, getAvailableSpaceForManaLong());
	}

	@Override
	public boolean isFull() {
		return getAvailableSpaceForManaLong() <= 0;
	}

	@Override
	public void receiveMana(int mana) {
		receiveManaLong(mana);
	}

	@Override
	public void receiveManaLong(long mana) {
		if (mana > 0) {
			addMana(mana, false);
		} else if (mana < 0) {
			removeMana(-mana, false);
		}
	}

	@Override
	public long addMana(long amount, boolean simulate) {
		return move(amount, simulate, true);
	}

	@Override
	public long removeMana(long amount, boolean simulate) {
		return move(amount, simulate, false);
	}

	private long move(long amount, boolean simulate, boolean insert) {
		if (amount <= 0 || client()) {
			return 0;
		}
		var grid = mainNode.getGrid();
		var inventory = storage();
		if (grid == null || inventory == null) {
			return 0;
		}
		var mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
		long moved = insert
				? StorageHelper.poweredInsert(grid.getEnergyService(), inventory, ManaKey.KEY, amount, source, mode)
				: StorageHelper.poweredExtraction(grid.getEnergyService(), inventory, ManaKey.KEY, amount, source, mode);
		if (moved > 0 && !simulate) {
			comparatorDirty = true;
			markDispatchable();
		}
		return Math.max(0, moved);
	}

	public long getComparatorSignal() {
		long max = getMaxManaLong();
		long current = getCurrentManaLong();
		if (max <= 0 || current <= 0) {
			return 0;
		}
		return Math.max(1, Math.min(15, (long) ((double) current / max * 15.0)));
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FluixManaPoolBlockEntity self) {
		ManaPoolBlockEntity.serverTick(level, pos, state, self);
		if (++self.ticks % 10 != 0) {
			return;
		}
		long stored = self.stored();
		long capacity = capacity(stored, self.space());
		if (capacity != self.shownCapacity || stored != self.shownMana) {
			self.shownMana = stored;
			self.shownCapacity = capacity;
			self.comparatorDirty = true;
			self.syncToClients();
		}
		if (self.comparatorDirty) {
			self.comparatorDirty = false;
			self.setChanged();
		}
	}

	@Deprecated(since = "457.2610.7", forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "2027.6.1")
	public static void replaceLegacy(Level level, BlockPos pos, BlockState state, BlockEntity legacy) {
		var tag = legacy.saveWithoutMetadata();
		var pool = new FluixManaPoolBlockEntity(pos, state);
		pool.load(tag);
		level.setBlockEntity(pool);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		GridHelper.onFirstTick(this, be -> be.mainNode.create(be.level, be.worldPosition));
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		mainNode.destroy();
	}

	@Override
	public void onChunkUnloaded() {
		super.onChunkUnloaded();
		mainNode.destroy();
	}

	@Override
	public void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		mainNode.saveToNBT(tag);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		if (!BlockEntitySync.isFullSync(tag)) {
			mainNode.loadFromNBT(tag);
		}
	}

	@Nullable
	@Override
	public IGridNode getGridNode(Direction dir) {
		return mainNode.getNode();
	}

	@Override
	public AECableType getCableConnectionType(Direction dir) {
		return AECableType.SMART;
	}

	@Nullable
	@Override
	public IGridNode getActionableNode() {
		return mainNode.getNode();
	}
}
