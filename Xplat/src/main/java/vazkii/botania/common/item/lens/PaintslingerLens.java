/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.lens;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.internal.ManaBurst;
import vazkii.botania.api.item.SparkEntity;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.common.helper.ColorHelper;

import java.util.*;
import java.util.function.Function;

public class PaintslingerLens extends Lens {

	@Override
	public boolean collideBurst(ManaBurst burst, HitResult pos, boolean isManaBlock, boolean shouldKill, ItemStack stack) {
		Entity entity = burst.entity();
		int storedColor = LensItem.getStoredColor(stack);
		if (!entity.level().isClientSide && !burst.isFake() && storedColor > -1 && storedColor < 17) {
			if (pos.getType() == HitResult.Type.ENTITY) {
				Entity collidedWith = ((EntityHitResult) pos).getEntity();
				if (collidedWith instanceof Sheep sheep) {
					int r = 20;
					DyeColor sheepColor = sheep.getColor();
					List<Sheep> sheepList = entity.level().getEntitiesOfClass(Sheep.class,
							new AABB(sheep.getX() - r, sheep.getY() - r, sheep.getZ() - r,
									sheep.getX() + r, sheep.getY() + r, sheep.getZ() + r));
					for (Sheep other : sheepList) {
						if (other.getColor() == sheepColor) {
							other.setColor(DyeColor.byId(storedColor == 16 ? other.level().random.nextInt(16) : storedColor));
						}
					}
					shouldKill = true;
				} else if (collidedWith instanceof SparkEntity spark) {
					spark.setNetwork(DyeColor.byId(storedColor == 16 ? collidedWith.level().random.nextInt(16) : storedColor));
				}
			} else if (pos.getType() == HitResult.Type.BLOCK) {
				BlockPos hitPos = ((BlockHitResult) pos).getBlockPos();
				Block hitBlock = entity.level().getBlockState(hitPos).getBlock();
				ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(hitBlock);

				if (BotaniaAPI.instance().getPaintableBlocks().containsKey(blockId)
						&& entity.mayInteract(entity.level(), hitPos)) {
					List<BlockPos> coordsToPaint = new ArrayList<>();
					Queue<BlockPos> coordsToCheck = new ArrayDeque<>();
					Set<BlockPos> checkedCoords = new HashSet<>();
					coordsToCheck.add(hitPos);
					checkedCoords.add(hitPos);

					while (!coordsToCheck.isEmpty() && coordsToPaint.size() < 1000) {
						BlockPos coords = coordsToCheck.remove();
						coordsToPaint.add(coords);

						for (Direction dir : Direction.values()) {
							BlockPos candidatePos = coords.relative(dir);
							if (checkedCoords.add(candidatePos)
									&& entity.level().getBlockState(candidatePos).is(hitBlock)
									&& entity.mayInteract(entity.level(), hitPos)) {
								coordsToCheck.add(candidatePos);
							}
						}
					}
					for (BlockPos coords : coordsToPaint) {
						DyeColor placeColor = DyeColor.byId(storedColor == 16 ? entity.level().random.nextInt(16) : storedColor);
						BlockState stateThere = entity.level().getBlockState(coords);

						Function<DyeColor, Block> f = BotaniaAPI.instance().getPaintableBlocks().get(blockId);
						Block newBlock = f.apply(placeColor);
						if (newBlock != stateThere.getBlock()) {
							entity.level().setBlockAndUpdate(coords, newBlock.withPropertiesOf(stateThere));
						}
					}
				}
			}
		}

		return shouldKill;
	}

	@Override
	public void onClientBurstEnd(ManaBurst burst, BlockHitResult hit, ItemStack stack) {
		int storedColor = LensItem.getStoredColor(stack);
		if (burst.isFake() || storedColor < 0 || storedColor > 16) {
			return;
		}
		Level level = burst.entity().level();
		BlockPos hitPos = hit.getBlockPos();
		BlockState hitState = level.getBlockState(hitPos);
		ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(hitState.getBlock());
		Function<DyeColor, Block> f = BotaniaAPI.instance().getPaintableBlocks().get(blockId);
		if (f == null) {
			return;
		}
		Queue<BlockPos> coordsToCheck = new ArrayDeque<>();
		Set<BlockPos> checkedCoords = new HashSet<>();
		coordsToCheck.add(hitPos);
		checkedCoords.add(hitPos);
		int painted = 0;
		while (!coordsToCheck.isEmpty() && painted < 1000) {
			BlockPos coords = coordsToCheck.remove();
			painted++;
			DyeColor placeColor = DyeColor.byId(storedColor == 16 ? level.random.nextInt(16) : storedColor);
			if (f.apply(placeColor) != level.getBlockState(coords).getBlock()) {
				spawnPaintSparkles(level, coords, placeColor);
			}
			for (Direction dir : Direction.values()) {
				BlockPos candidatePos = coords.relative(dir);
				if (checkedCoords.add(candidatePos) && level.getBlockState(candidatePos).is(hitState.getBlock())) {
					coordsToCheck.add(candidatePos);
				}
			}
		}
	}

	private static void spawnPaintSparkles(Level level, BlockPos at, DyeColor placeColor) {
		int hex = ColorHelper.getColorValue(placeColor);
		int r = (hex & 0xFF0000) >> 16;
		int g = (hex & 0xFF00) >> 8;
		int b = hex & 0xFF;
		for (int i = 0; i < 10; i++) {
			BlockPos pos = at.relative(Direction.getRandom(level.random));
			SparkleParticleData data = SparkleParticleData.sparkle(0.6F + (float) Math.random() * 0.5F, r / 255F, g / 255F, b / 255F, 5);
			level.addParticle(data, pos.getX() + (float) Math.random(), pos.getY() + (float) Math.random(), pos.getZ() + (float) Math.random(), 0, 0, 0);
		}
	}

}
