package vazkii.botania.api.mana;

import net.minecraft.world.item.DyeColor;

import java.util.Optional;

public interface ManaPool extends ManaReceiver {

    /**
     * Returns false if the mana pool is accepting power from other power items,
     * true if it's sending power into them.
     */
    boolean isOutputtingPower();

    /**
     * @return Maximum amount of storable mana
     */
    int getMaxMana();

    default long getMaxManaLong() {
        return getMaxMana();
    }

    /**
     * @return The color of this pool.
     */
    Optional<DyeColor> getColor();

    /**
     * Sets the color of this pool.
     * 
     * @param color The color to set.
     */
    void setColor(Optional<DyeColor> color);
}
