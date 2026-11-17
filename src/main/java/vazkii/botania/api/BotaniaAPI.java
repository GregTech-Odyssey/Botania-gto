package vazkii.botania.api;

import net.minecraft.core.Registry;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vazkii.botania.api.brew.Brew;

public interface BotaniaAPI {

    String MODID = "botania";
    String GOG_MODID = "gardenofglass";
    Logger LOGGER = LoggerFactory.getLogger(MODID);

    BotaniaAPI INSTANCE = ServiceUtil.findService(BotaniaAPI.class, () -> new BotaniaAPI() {});

    static BotaniaAPI instance() {
        return INSTANCE;
    }

    @Nullable
    default Registry<Brew> getBrewRegistry() {
        return null;
    }
}
