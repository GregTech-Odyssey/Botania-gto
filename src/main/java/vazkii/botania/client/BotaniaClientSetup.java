package vazkii.botania.client;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import vazkii.botania.client.core.handler.ClientTickHandler;
import vazkii.botania.client.model.FloatingFlowerGeometry;
import vazkii.botania.client.model.PylonModelLayers;
import vazkii.botania.client.model.armor.ArmorModelLayers;
import vazkii.botania.client.render.block_entity.*;
import vazkii.botania.common.alfheim.AlfheimPortalHandler;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntities;

/**
 * 复刻内容的客户端接线：方块实体渲染器与模型层。
 */
public final class BotaniaClientSetup {

    private BotaniaClientSetup() {}

    public static void init(IEventBus bus) {
        vazkii.botania.forge.integration.ae2.client.BotaniaAE2Client.init(bus);
        bus.addListener(BotaniaClientSetup::registerRenderers);
        bus.addListener(BotaniaClientSetup::registerLayerDefinitions);
        bus.addListener(BotaniaClientSetup::registerGeometryLoaders);
        bus.addListener(BotaniaColorHandlers::registerBlockColors);
        bus.addListener(BotaniaColorHandlers::registerItemColors);
        bus.addListener(BotaniaClientSetup::clientSetup);
        bus.addListener(BotaniaClientSetup::registerScreens);
        bus.addListener(BotaniaShaders::register);
        bus.addListener(vazkii.botania.client.fx.BotaniaParticles::registerProviders);
    }

    private static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                vazkii.botania.common.BotaniaMenus.FLOWER_BAG.get(),
                vazkii.botania.client.gui.bag.FlowerPouchGui::new));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BotaniaBlockEntities.PYLON.get(), PylonBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(BotaniaBlockEntities.RETURN_PORTAL.get(), RenderReturnPortal::new);
        event.registerBlockEntityRenderer(BotaniaBlockEntities.ALF_PORTAL.get(), AlfheimPortalBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(BotaniaBlockEntities.MANA_POOL.get(), ManaPoolBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(BotaniaBlockEntities.CORPOREA_INDEX.get(), CorporeaIndexBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(BotaniaBlockEntities.LARGE_MANA_POOL.get(), LargeManaPoolBlockEntityRenderer::new);
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        PylonModelLayers.register(event);
        vazkii.botania.client.model.BotaniaModelLayers.register(event);
        ArmorModelLayers.register(event);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(BotaniaRenderLayers::init);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ClientTickEvent tickEvent) -> {
                    if (tickEvent.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                        ClientTickHandler.clientTickEnd(net.minecraft.client.Minecraft.getInstance());
                    }
                });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.RenderTickEvent renderEvent) -> {
                    if (renderEvent.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                        ClientTickHandler.renderTick(renderEvent.renderTickTime);
                    }
                });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (RenderGuiEvent.Post guiEvent) -> AlfheimPortalHandler.renderOverlay(guiEvent.getGuiGraphics()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ClientTickEvent tickEvent) -> {
                    if (tickEvent.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        AlfheimPortalHandler.clientTick(mc.level, mc.player);
                    }
                });
    }

    private static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("floating_flower", FloatingFlowerGeometry.Loader.INSTANCE);
    }
}
