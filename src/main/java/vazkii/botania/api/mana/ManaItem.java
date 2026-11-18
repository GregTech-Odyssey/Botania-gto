package vazkii.botania.api.mana;

/**
 * An item that has this capability can contain mana.
 */
public interface ManaItem extends ManaEndpoint {

    /**
     * Gets the amount of mana this item contains
     */
    int getMana();

    /**
     * Gets the max amount of mana this item can hold.
     */
    int getMaxMana();

    /**
     * Adds mana to this item.
     */
    void addMana(int mana);

    /**
     * If this item simply does not export mana at all, set this to true. This is
     * used to skip items that contain mana but can't export it when drawing the
     * mana bar above the XP bar.
     */
    boolean isNoExport();

    @Override
    default long addMana(long amount, boolean simulate) {
        long change = Math.min(amount, (long) getMaxMana() - getMana());
        if (change <= 0) {
            return 0;
        }
        if (!simulate) {
            addMana((int) change);
        }
        return change;
    }

    @Override
    default long removeMana(long amount, boolean simulate) {
        long change = Math.min(amount, getMana());
        if (change <= 0) {
            return 0;
        }
        if (!simulate) {
            addMana((int) -change);
        }
        return change;
    }
}
