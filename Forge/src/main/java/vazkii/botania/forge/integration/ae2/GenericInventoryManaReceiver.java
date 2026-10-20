package vazkii.botania.forge.integration.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.ManaReceiver;


import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.capabilities.Capabilities;

record GenericInventoryManaReceiver(GenericInternalInventory inventory, BlockEntity owner) implements ManaReceiver {
	@Nullable
	static ManaReceiver find(BlockEntity be, @Nullable Direction side) {
		var inventory = be.getCapability(Capabilities.GENERIC_INTERNAL_INV, side).orElse(null);
		return inventory == null || !inventory.canInsert() || !inventory.isAllowed(ManaKey.KEY) ? null : new GenericInventoryManaReceiver(inventory, be);
	}

	private long insert(long amount, Actionable mode) {
		long remaining = amount;
		for (int slot = 0, size = inventory.size(); slot < size && remaining > 0; slot++) {
			var key = inventory.getKey(slot);
			if (key == null || key == ManaKey.KEY) {
				remaining -= inventory.insert(slot, ManaKey.KEY, remaining, mode);
			}
		}
		return amount - remaining;
	}

	@Override
	public Level getManaReceiverLevel() {
		return owner.getLevel();
	}

	@Override
	public BlockPos getManaReceiverPos() {
		return owner.getBlockPos();
	}

	@Override
	public int getCurrentMana() {
		return (int) Math.min(Integer.MAX_VALUE, getCurrentManaLong());
	}

	@Override
	public long getCurrentManaLong() {
		long total = 0;
		for (int slot = 0, size = inventory.size(); slot < size; slot++) {
			if (inventory.getKey(slot) == ManaKey.KEY) {
				total += inventory.getAmount(slot);
			}
		}
		return total;
	}

	@Override
	public boolean isFull() {
		return insert(1, Actionable.SIMULATE) <= 0;
	}

	@Override
	public void receiveMana(int mana) {
		if (mana > 0) {
			insert(mana, Actionable.MODULATE);
		}
	}

	@Override
	public void receiveManaLong(long mana) {
		if (mana > 0) {
			insert(mana, Actionable.MODULATE);
		}
	}

	@Override
	public boolean canReceiveManaFromBursts() {
		return true;
	}
}
