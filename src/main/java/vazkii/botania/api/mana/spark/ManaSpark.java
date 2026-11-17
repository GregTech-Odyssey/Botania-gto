/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.api.mana.spark;

import vazkii.botania.api.item.SparkEntity;

/**
 * An Entity that implements this is considered a Mana Spark.
 */
public interface ManaSpark extends SparkEntity {

    SparkUpgradeType getUpgrade();

    void setUpgrade(SparkUpgradeType upgrade);
}
