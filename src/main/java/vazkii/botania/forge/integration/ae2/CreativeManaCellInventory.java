package vazkii.botania.forge.integration.ae2;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;

final class CreativeManaCellInventory implements StorageCell {
	private static final long VERSION = MEStorage.nextChangeVersion();

	private final ItemStack stack;

	CreativeManaCellInventory(ItemStack stack) {
		this.stack = stack;
	}

	@Override
	public CellState getStatus() {
		return CellState.TYPES_FULL;
	}

	@Override
	public double getIdleDrain() {
		return 0;
	}

	@Override
	public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
		return what == ManaKey.KEY;
	}

	@Override
	public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
		return what == ManaKey.KEY ? Math.max(0, amount) : 0;
	}

	@Override
	public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
		return what == ManaKey.KEY ? Math.max(0, amount) : 0;
	}

	@Override
	public void getAvailableStacks(KeyCounter out) {
		out.add(ManaKey.KEY, Integer.MAX_VALUE);
	}

	@Override
	public long getChangeVersion() {
		return VERSION;
	}

	@Override
	public void persist() {}

	@Override
	public Component getDescription() {
		return stack.getHoverName();
	}
}
