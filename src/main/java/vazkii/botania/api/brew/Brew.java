package vazkii.botania.api.brew;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;

import vazkii.botania.api.BotaniaAPI;

import java.util.function.Supplier;

/**
 * The class for a Brew definition, each one is a singleton.
 */
public class Brew {

    private final Supplier<Integer> color;
    private final int cost;

    /**
     * @param color   The color for the potion to be rendered in the bottle, note that it will get
     *                changed a bit when it renders (for more or less brightness) to give a fancy effect.
     *                See {@link net.minecraft.world.item.alchemy.PotionUtils#getColor} for a method
     *                to calculate this automatically.
     * @param cost    The cost, in Mana for this brew.
     * @param effects A list of effects to apply to the player when they drink it.
     */
    public Brew(int color, int cost, MobEffectInstance... effects) {
        this.color = () -> color;
        this.cost = cost;
    }

    /**
     * Gets the insensitive unlocalized name. This is used for the lexicon.
     */
    public String getTranslationKey() {
        ResourceLocation id = BotaniaAPI.instance().getBrewRegistry().getKey(this);
        return String.format("%s.brew.%s", id.getNamespace(), id.getPath());
    }

    /**
     * Gets the unlocalized name for the ItemStack passed in.
     */
    public String getTranslationKey(ItemStack stack) {
        return getTranslationKey();
    }

    /**
     * Gets the display color for the ItemStack passed in. Note that for
     * the lexicon, this passes in a botania Managlass Vial or an
     * Alfglass Flask at all times.
     */
    public int getColor(ItemStack stack) {
        return color.get();
    }
}
