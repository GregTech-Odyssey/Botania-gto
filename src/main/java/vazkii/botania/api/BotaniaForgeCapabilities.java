package vazkii.botania.api;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.mana.ManaReceiver;

public final class BotaniaForgeCapabilities {

    public static final Capability<ManaItem> MANA_ITEM = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<ManaReceiver> MANA_RECEIVER = CapabilityManager.get(new CapabilityToken<>() {});
    private BotaniaForgeCapabilities() {}
}
