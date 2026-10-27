package vazkii.botania.forge.integration.ae2;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;

final class ManaCellHandler implements ICellHandler {
	static final ManaCellHandler INSTANCE = new ManaCellHandler();

	private ManaCellHandler() {}

	@Override
	public boolean isCell(ItemStack is) {
		return is.getItem() instanceof ManaCellItem || is.is(BotaniaAE2.creativeManaCell);
	}

	@Nullable
	@Override
	public StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
		if (is.getItem() instanceof ManaCellItem cell) {
			return new ManaCellInventory(is, cell, host);
		}
		return is.is(BotaniaAE2.creativeManaCell) ? new CreativeManaCellInventory(is) : null;
	}
}
