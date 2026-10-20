/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.api.mana;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Any Block or Block Entity with this capability can hold and receive mana from mana bursts.
 */
public interface ManaReceiver {
	Level getManaReceiverLevel();

	BlockPos getManaReceiverPos();

	/**
	 * Gets the amount of mana currently in this block.
	 */
	int getCurrentMana();

	default long getCurrentManaLong() {
		return getCurrentMana();
	}

	/**
	 * Is this Mana Receiver is full? Being full means no mana bursts will be sent.
	 */
	boolean isFull();

	/**
	 * Called when this receiver receives mana.
	 */
	void receiveMana(int mana);

	default void receiveManaLong(long mana) {
		while (mana != 0) {
			int step = (int) Math.max(-Integer.MAX_VALUE, Math.min(Integer.MAX_VALUE, mana));
			receiveMana(step);
			mana -= step;
		}
	}

	/**
	 * Can this tile receive mana from bursts? Generally set to false for
	 * implementations of ManaCollector.
	 */
	boolean canReceiveManaFromBursts();

}
