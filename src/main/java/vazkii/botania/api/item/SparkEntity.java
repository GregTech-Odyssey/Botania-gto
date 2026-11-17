package vazkii.botania.api.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;

/**
 * An Entity that implements this is considered a Spark.
 */
public interface SparkEntity {

    /**
     * Gets the network that this spark is on, or the color it's displaying. Sparks may only connect to others
     * of the same network.
     */
    DyeColor getNetwork();

    void setNetwork(DyeColor color);

    /**
     * @return this spark as an Entity
     */
    default Entity entity() {
        return (Entity) this;
    }
}
