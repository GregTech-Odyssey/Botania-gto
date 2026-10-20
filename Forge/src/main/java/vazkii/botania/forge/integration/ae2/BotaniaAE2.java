package vazkii.botania.forge.integration.ae2;

import com.google.common.collect.ImmutableSet;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegisterEvent;

import org.jetbrains.annotations.ApiStatus;

import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.BotaniaRegistries;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntities;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.forge.CapabilityUtil;
import vazkii.botania.forge.xplat.ForgeXplatImpl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.client.StorageCellModels;
import appeng.api.features.P2PTunnelAttunement;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartItem;
import appeng.items.parts.PartModelsHelper;

/**
 * Optional Applied Energistics 2 integration, only loaded when ae2 is present: mana key type, mana cells, fluix mana
 * pool, mana P2P tunnel, bus strategies, and aliases that migrate the IDs of the retired Applied Botanics addon.
 */
public final class BotaniaAE2 {
	public static final ResourceKey<Registry<AEKeyType>> KEY_TYPES = ResourceKey.createRegistryKey(new ResourceLocation("ae2", "keytypes"));
	@Deprecated(since = "457.2610.7", forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "2027.6.1")
	private static final String LEGACY = "appbot";
	private static final int[] TIERS = { 1, 4, 16, 64, 256 };
	private static final double[] IDLE_DRAIN = { 0.5, 1.0, 1.5, 2.0, 2.5 };

	public static Block fluixManaPool;
	public static BlockEntityType<FluixManaPoolBlockEntity> fluixManaPoolType;
	public static Item fluixManaPoolItem;
	public static Item manaCellHousing;
	public static Item creativeManaCell;
	public static Item manaP2PTunnel;
	public static final List<ManaCellItem> MANA_CELLS = new ArrayList<>();
	@Deprecated(since = "457.2610.7", forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "2027.6.1")
	private static final Map<String, Item> LEGACY_ITEMS = new HashMap<>();

	private BotaniaAE2() {}

	public static void init(IEventBus modBus) {
		modBus.addListener(BotaniaAE2::onRegister);
		modBus.addListener(BotaniaAE2::setup);
		modBus.addListener(BotaniaAE2::fillCreativeTab);
		MinecraftForge.EVENT_BUS.addListener(BotaniaAE2::remapLegacy);
		MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, BotaniaAE2::attachCapabilities);
		ForgeXplatImpl.manaReceiverFallback = GenericInventoryManaReceiver::find;
	}

	@Deprecated(since = "457.2610.7", forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "2027.6.1")
	private static void alias(RegisterEvent event, String legacyPath, ResourceLocation id) {
		if (event.getForgeRegistry() instanceof ForgeRegistry<?>registry) {
			registry.addAlias(new ResourceLocation(LEGACY, legacyPath), id);
		}
	}

	private static <T> void register(RegisterEvent event, ResourceKey<Registry<T>> registry, String path, T value) {
		event.register(registry, prefix(path), () -> value);
		alias(event, path, prefix(path));
	}

	private static void registerItem(RegisterEvent event, String path, Item item) {
		register(event, Registries.ITEM, path, item);
		LEGACY_ITEMS.put(path, item);
	}

	private static void onRegister(RegisterEvent event) {
		var key = event.getRegistryKey();
		if (key.equals(Registries.BLOCK)) {
			fluixManaPool = new FluixManaPoolBlock(BlockBehaviour.Properties.copy(BotaniaBlocks.manaPool));
			register(event, Registries.BLOCK, "fluix_mana_pool", fluixManaPool);
		} else if (key.equals(Registries.ITEM)) {
			fluixManaPoolItem = new BlockItem(fluixManaPool, BotaniaItems.defaultBuilder());
			registerItem(event, "fluix_mana_pool", fluixManaPoolItem);
			manaCellHousing = new Item(BotaniaItems.defaultBuilder());
			registerItem(event, "mana_cell_housing", manaCellHousing);
			List<Supplier<? extends ItemLike>> components = List.of(() -> AEItems.CELL_COMPONENT_1K, () -> AEItems.CELL_COMPONENT_4K,
					() -> AEItems.CELL_COMPONENT_16K, () -> AEItems.CELL_COMPONENT_64K, () -> AEItems.CELL_COMPONENT_256K);
			for (int i = 0; i < TIERS.length; i++) {
				var cell = new ManaCellItem(BotaniaItems.defaultBuilder().stacksTo(1), components.get(i), () -> manaCellHousing, TIERS[i], IDLE_DRAIN[i]);
				String path = "mana_storage_cell_" + TIERS[i] + "k";
				registerItem(event, path, cell);
				alias(event, "portable_" + path, prefix(path));
				LEGACY_ITEMS.put("portable_" + path, cell);
				MANA_CELLS.add(cell);
			}
			creativeManaCell = new Item(BotaniaItems.defaultBuilder().stacksTo(1).rarity(Rarity.EPIC));
			registerItem(event, "creative_mana_cell", creativeManaCell);
			manaP2PTunnel = new PartItem<>(BotaniaItems.defaultBuilder(), ManaP2PTunnelPart.class, ManaP2PTunnelPart::new);
			registerItem(event, "mana_p2p_tunnel", manaP2PTunnel);
			PartModels.registerModels(PartModelsHelper.createModels(ManaP2PTunnelPart.class));
		} else if (key.equals(Registries.BLOCK_ENTITY_TYPE)) {
			fluixManaPoolType = BlockEntityType.Builder.of(FluixManaPoolBlockEntity::new, fluixManaPool).build(null);
			register(event, Registries.BLOCK_ENTITY_TYPE, "fluix_mana_pool", fluixManaPoolType);
			acceptLegacyPoolEntities();
		} else if (key.equals(KEY_TYPES)) {
			event.register(KEY_TYPES, ManaKeyType.ID, () -> ManaKeyType.TYPE);
			if (event.getForgeRegistry() instanceof ForgeRegistry<?>registry) {
				registry.addAlias(ManaKeyType.LEGACY_ID, ManaKeyType.ID);
			}
		}
	}

	private static void setup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			StorageCells.addCellHandler(ManaCellHandler.INSTANCE);
			ExternalStorageStrategy.register(ManaKeyType.TYPE, ManaStorageStrategies::external);
			StackImportStrategy.register(ManaKeyType.TYPE, ManaStorageStrategies::importer);
			StackExportStrategy.register(ManaKeyType.TYPE, ManaStorageStrategies::exporter);
			ContainerItemStrategy.register(ManaKeyType.TYPE, ManaKey.class, new ManaContainerItemStrategy());
			GenericSlotCapacities.register(ManaKeyType.TYPE, 1_000_000L);
			P2PTunnelAttunement.registerAttunementTag(manaP2PTunnel);
			for (int i = 0; i < MANA_CELLS.size(); i++) {
				StorageCellModels.registerModel(MANA_CELLS.get(i), prefix("block/drive/mana_cell_" + TIERS[i] + "k"));
			}
			StorageCellModels.registerModel(creativeManaCell, prefix("block/drive/creative_mana_cell"));
		});
	}

	private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == BotaniaRegistries.BOTANIA_TAB_KEY) {
			event.accept(fluixManaPoolItem);
			event.accept(manaCellHousing);
			MANA_CELLS.forEach(event::accept);
			event.accept(creativeManaCell);
			event.accept(manaP2PTunnel);
		}
	}

	@Deprecated(since = "457.2610.7", forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "2027.6.1")
	@SuppressWarnings("unchecked")
	private static void acceptLegacyPoolEntities() {
		try {
			Field field = ObfuscationReflectionHelper.findField(BlockEntityType.class, "f_58915_");
			var valid = (Set<Block>) field.get(BotaniaBlockEntities.POOL);
			field.set(BotaniaBlockEntities.POOL, ImmutableSet.<Block>builder().addAll(valid).add(fluixManaPool).build());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@Deprecated(since = "457.2610.7", forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "2027.6.1")
	private static void remapLegacy(MissingMappingsEvent event) {
		for (var mapping : event.getMappings(Registries.ITEM, LEGACY)) {
			var item = LEGACY_ITEMS.get(mapping.getKey().getPath());
			if (item != null) {
				mapping.remap(item);
			}
		}
		for (var mapping : event.getMappings(Registries.BLOCK, LEGACY)) {
			if (mapping.getKey().getPath().equals("fluix_mana_pool")) {
				mapping.remap(fluixManaPool);
			}
		}
		for (var mapping : event.getMappings(Registries.BLOCK_ENTITY_TYPE, LEGACY)) {
			if (mapping.getKey().getPath().equals("fluix_mana_pool")) {
				mapping.remap(fluixManaPoolType);
			}
		}
	}

	private static void attachCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
		if (event.getObject() instanceof FluixManaPoolBlockEntity pool) {
			event.addCapability(prefix("mana_receiver"), CapabilityUtil.makeProvider(BotaniaForgeCapabilities.MANA_RECEIVER, pool));
			event.addCapability(prefix("spark_attachable"), CapabilityUtil.makeProvider(BotaniaForgeCapabilities.SPARK_ATTACHABLE, pool));
			event.addCapability(prefix("wandable"), CapabilityUtil.makeProvider(BotaniaForgeCapabilities.WANDABLE, pool));
		}
	}
}
