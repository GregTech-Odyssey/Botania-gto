/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.api.mana.spark;

/**
 * A block entity with this capability can have a mana spark attached to it.
 * For the Spark to be allowed to have upgrades, the same block position must also have an ManaPool capability
 */
public interface SparkAttachable {

    /**
     * Returns how much space for mana is available in this block, normally the total - the current.
     * Should NEVER return negative values. Make sure to check against that.
     */
    int getAvailableSpaceForMana();

    default long getAvailableSpaceForManaLong() {
        return getAvailableSpaceForMana();
    }
}
