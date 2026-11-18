package vazkii.botania.api.mana;

public interface ManaCollector extends ManaReceiver {

    /**
     * Gets the maximum amount of mana this collector can have.
     */
    int getMaxMana();
}
