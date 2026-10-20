package vazkii.botania.forge.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import vazkii.botania.common.lib.LibMisc;
import vazkii.botania.data.*;
import vazkii.botania.data.recipes.*;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static vazkii.botania.common.BotaniaDamageTypes.*;

@Mod.EventBusSubscriber(modid = LibMisc.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeDatagenInitializer {
	@SubscribeEvent
	public static void configureForgeDatagen(GatherDataEvent evt) {
		if (System.getProperty("botania.xplat_datagen") != null) {
			configureXplatDatagen(evt);
			return;
		}
		var generator = evt.getGenerator();
		var output = generator.getPackOutput();
		var disabledHelper = new ExistingFileHelper(Collections.emptyList(), Collections.emptySet(), false, null, null);
		var blockTagProvider = new ForgeBlockTagProvider(output, evt.getLookupProvider(), disabledHelper);
		generator.addProvider(evt.includeServer(), blockTagProvider);
		generator.addProvider(evt.includeServer(), new ForgeItemTagProvider(output, evt.getLookupProvider(),
				blockTagProvider.contentsGetter(), disabledHelper));
		generator.addProvider(evt.includeServer(), new ForgeRecipeProvider(output));
		generator.addProvider(evt.includeServer(), new ForgeBlockLootProvider(generator));
		generator.addProvider(evt.includeServer(), new ForgeBiomeTagProvider(output, evt.getLookupProvider(), disabledHelper));
		generator.addProvider(evt.includeServer(), new ForgeEntityTagProvider(output, evt.getLookupProvider(), disabledHelper));
	}

	private static void configureXplatDatagen(GatherDataEvent evt) {
		DataGenerator generator = evt.getGenerator();
		PackOutput output = generator.getPackOutput();
		CompletableFuture<HolderLookup.Provider> lookup = evt.getLookupProvider().thenApply(ForgeDatagenInitializer::withBotaniaEntries);
		boolean server = evt.includeServer();
		boolean client = evt.includeClient();

		generator.addProvider(server, new BlockLootProvider(output));
		generator.addProvider(server, new LooniumStructureLootProvider(output));
		generator.addProvider(server, new LooniumStructureConfigurationProvider(output));
		generator.addProvider(server, new LooniumEquipmentLootProvider(output, lookup));
		var blockTagProvider = new BlockTagProvider(output, lookup);
		generator.addProvider(server, blockTagProvider);
		generator.addProvider(server, new ItemTagProvider(output, lookup, blockTagProvider.contentsGetter()));
		generator.addProvider(server, new EntityTagProvider(output, lookup));
		generator.addProvider(server, new BannerPatternTagsProvider(output, lookup));
		generator.addProvider(server, new BiomeTagProvider(output, lookup));
		generator.addProvider(server, new DatapackBuiltinEntriesProvider(output, evt.getLookupProvider(), botaniaEntries(), Set.of(LibMisc.MOD_ID)));
		generator.addProvider(server, new DamageTypeTagProvider(output, lookup));
		generator.addProvider(server, new StonecuttingProvider(output));
		generator.addProvider(server, new CraftingRecipeProvider(output));
		generator.addProvider(server, new SmeltingProvider(output));
		generator.addProvider(server, new ElvenTradeProvider(output));
		generator.addProvider(server, new ManaInfusionProvider(output));
		generator.addProvider(server, new PureDaisyProvider(output));
		generator.addProvider(server, new BrewProvider(output));
		generator.addProvider(server, new PetalApothecaryProvider(output));
		generator.addProvider(server, new RunicAltarProvider(output));
		generator.addProvider(server, new TerrestrialAgglomerationProvider(output));
		generator.addProvider(server, new OrechidProvider(output));
		generator.addProvider(client, new BlockstateProvider(output));
		generator.addProvider(client, new FloatingFlowerModelProvider(output));
		generator.addProvider(client, new ItemModelProvider(output));
		generator.addProvider(client, new PottedPlantModelProvider(output));
		generator.addProvider(server, AdvancementProvider.create(output, lookup));
	}

	private static RegistrySetBuilder botaniaEntries() {
		return new RegistrySetBuilder().add(Registries.DAMAGE_TYPE, ForgeDatagenInitializer::damageTypes);
	}

	private static HolderLookup.Provider withBotaniaEntries(HolderLookup.Provider original) {
		HolderLookup.Provider patch = botaniaEntries().buildPatch(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), original);
		return new HolderLookup.Provider() {
			@Override
			public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
				Optional<HolderLookup.RegistryLookup<T>> patched = patch.lookup(key);
				return patched.isPresent() ? patched : original.lookup(key);
			}
		};
	}

	private static void damageTypes(BootstapContext<DamageType> context) {
		DamageTypes.bootstrap(context);
		context.register(RELIC_DAMAGE, RELIC);
		context.register(PLAYER_ATTACK_ARMOR_PIERCING, PLAYER_AP);
		context.register(KEY_EXPLOSION, KEY);
	}
}
