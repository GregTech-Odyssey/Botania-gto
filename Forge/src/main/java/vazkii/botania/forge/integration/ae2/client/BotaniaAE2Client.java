package vazkii.botania.forge.integration.ae2.client;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import vazkii.botania.api.BotaniaForgeClientCapabilities;
import vazkii.botania.api.state.BotaniaStateProperties;
import vazkii.botania.client.render.block_entity.ManaPoolBlockEntityRenderer;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.item.material.MysticalPetalItem;
import vazkii.botania.forge.CapabilityUtil;
import vazkii.botania.forge.integration.ae2.BotaniaAE2;
import vazkii.botania.forge.integration.ae2.FluixManaPoolBlockEntity;
import vazkii.botania.forge.integration.ae2.ManaKey;
import vazkii.botania.forge.integration.ae2.ManaKeyType;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

import appeng.api.client.AEKeyRendering;

public final class BotaniaAE2Client {
	private BotaniaAE2Client() {}

	public static void init(IEventBus modBus) {
		modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(
				() -> AEKeyRendering.register(ManaKeyType.TYPE, ManaKey.class, new ManaKeyRenderer())));
		modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerBlockEntityRenderer(BotaniaAE2.fluixManaPoolType, ManaPoolBlockEntityRenderer::new));
		modBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register((state, level, pos, tintIndex) -> tintIndex != 0 ? -1
				: state.getValue(BotaniaStateProperties.OPTIONAL_DYE_COLOR).toDyeColor().map(MysticalPetalItem::getPetalLikeColor).orElse(-1),
				BotaniaAE2.fluixManaPool));
		MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, BotaniaAE2Client::attachCapabilities);
	}

	private static void attachCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
		if (event.getObject() instanceof FluixManaPoolBlockEntity pool) {
			event.addCapability(prefix("wand_hud"), CapabilityUtil.makeProvider(BotaniaForgeClientCapabilities.WAND_HUD, new ManaPoolBlockEntity.WandHud(pool)));
		}
	}
}
