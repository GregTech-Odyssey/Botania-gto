/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.block_entity.mana;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.item.ManaDissolvable;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.recipe.ManaInfusionRecipe;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.common.helper.EntityHelper;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// Item infusion for all mana pools: each pool tick infuses up to the spec's infusion rate per held item.
// Server-side lookup uses an index by input item, rebuilt whenever the recipe map is replaced.
public final class ManaInfusion {
	private static volatile Index serverIndex;

	private ManaInfusion() {}

	public static int process(Level level, ManaPool pool, ItemEntity item, BlockState catalyst, int rate, BlockPos outputPos) {
		ItemStack stack = item.getItem();
		if (stack.getItem() instanceof ManaDissolvable dissolvable) {
			for (int i = 0; i < rate && item.isAlive() && !item.getItem().isEmpty(); i++) {
				dissolvable.onDissolveTick(pool, item);
			}
			stack = item.getItem();
		}
		if (!item.isAlive() || stack.isEmpty() || XplatAbstractions.INSTANCE.itemFlagsComponent(item).manaInfusionSpawned) {
			return 0;
		}
		ManaInfusionRecipe recipe = find(level, stack, catalyst);
		if (recipe == null) {
			return 0;
		}
		long cost = Math.max(0, recipe.getManaToConsume());
		int count = Math.min(rate, stack.getCount());
		if (cost > 0) {
			count = (int) Math.min(count, pool.getCurrentManaLong() / cost);
		}
		if (count <= 0) {
			return 0;
		}
		if (cost > 0) {
			long taken = pool.removeMana(cost * count, false);
			count = (int) (taken / cost);
			if (taken > cost * count) {
				pool.addMana(taken - cost * count, false);
			}
			if (count <= 0) {
				return 0;
			}
		}
		ItemStack output = recipe.getRecipeOutput(level.registryAccess(), stack);
		stack.shrink(count);
		EntityHelper.syncItem(item);
		int total = output.getCount() * count;
		boolean first = true;
		while (total > 0) {
			ItemStack part = output.copyWithCount(Math.min(total, output.getMaxStackSize()));
			total -= part.getCount();
			if (first && item.getOwner() instanceof Player player) {
				player.triggerRecipeCrafted(recipe, List.of(part));
				part.onCraftedBy(level, player, output.getCount() * count);
			}
			first = false;
			ItemEntity outputItem = new ItemEntity(level, outputPos.getX() + 0.5, outputPos.getY() + 1.5, outputPos.getZ() + 0.5, part);
			XplatAbstractions.INSTANCE.itemFlagsComponent(outputItem).manaInfusionSpawned = true;
			level.addFreshEntity(outputItem);
		}
		return count;
	}

	@Nullable
	public static ManaInfusionRecipe find(Level level, @NotNull ItemStack stack, @NotNull BlockState catalyst) {
		Map<ResourceLocation, ManaInfusionRecipe> recipes = BotaniaRecipeTypes.getRecipes(level, BotaniaRecipeTypes.MANA_INFUSION_TYPE);
		if (level.isClientSide) {
			return select(recipes.values(), stack, catalyst);
		}
		Index index = serverIndex;
		if (index == null || index.source != recipes) {
			index = Index.build(recipes);
			serverIndex = index;
		}
		List<ManaInfusionRecipe> candidates = index.byItem.get(stack.getItem());
		return select(candidates != null ? candidates : index.any, stack, catalyst);
	}

	@Nullable
	private static ManaInfusionRecipe select(Collection<ManaInfusionRecipe> recipes, ItemStack stack, BlockState catalyst) {
		ManaInfusionRecipe plain = null;
		for (var recipe : recipes) {
			if (!recipe.matches(stack)) {
				continue;
			}
			var recipeCatalyst = recipe.getRecipeCatalyst();
			if (recipeCatalyst == null) {
				if (plain == null) {
					plain = recipe;
				}
			} else if (recipeCatalyst.test(catalyst)) {
				return recipe;
			}
		}
		return plain;
	}

	private record Index(Map<ResourceLocation, ManaInfusionRecipe> source, Reference2ObjectOpenHashMap<Item, List<ManaInfusionRecipe>> byItem, List<ManaInfusionRecipe> any) {
		static Index build(Map<ResourceLocation, ManaInfusionRecipe> source) {
			List<ManaInfusionRecipe> recipes = new ArrayList<>(source.values());
			List<Item[]> keys = new ArrayList<>(recipes.size());
			Reference2ObjectOpenHashMap<Item, List<ManaInfusionRecipe>> byItem = new Reference2ObjectOpenHashMap<>();
			for (var recipe : recipes) {
				Item[] items = inputItems(recipe);
				keys.add(items);
				if (items != null) {
					for (Item item : items) {
						byItem.computeIfAbsent(item, i -> new ArrayList<>());
					}
				}
			}
			List<ManaInfusionRecipe> any = new ArrayList<>();
			for (int i = 0; i < recipes.size(); i++) {
				var recipe = recipes.get(i);
				Item[] items = keys.get(i);
				if (items == null) {
					any.add(recipe);
					for (var list : byItem.values()) {
						list.add(recipe);
					}
				} else {
					for (Item item : items) {
						List<ManaInfusionRecipe> list = byItem.get(item);
						if (list.isEmpty() || list.get(list.size() - 1) != recipe) {
							list.add(recipe);
						}
					}
				}
			}
			return new Index(source, byItem, any);
		}

		@Nullable
		private static Item[] inputItems(ManaInfusionRecipe recipe) {
			if (!(recipe instanceof vazkii.botania.common.crafting.ManaInfusionRecipe) || recipe.getIngredients().size() != 1) {
				return null;
			}
			Ingredient input = recipe.getIngredients().get(0);
			if (input.getClass() != Ingredient.class) {
				return null;
			}
			ItemStack[] stacks = input.getItems();
			if (stacks.length == 0) {
				return null;
			}
			Item[] items = new Item[stacks.length];
			for (int i = 0; i < stacks.length; i++) {
				items[i] = stacks[i].getItem();
			}
			return items;
		}
	}
}
