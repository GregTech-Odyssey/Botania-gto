/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.entity;

import com.suried.gtoutils.spatial.BlockIndex;
import com.suried.gtoutils.spatial.BlockRange;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.common.handler.BotaniaBlockEntityIndexes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Per-level octree index of mana sparks by block position and network colour, kept in sync from the entity level
// callback,
// so range lookups only visit nearby sparks of the requested colour instead of every entity in a 25-block box.
public final class ManaSparkIndex {
	private static final double MARGIN = 1.0;

	private final BlockIndex<ArrayList<ManaSparkEntity>> all = new BlockIndex<>();
	private final List<BlockIndex<ArrayList<ManaSparkEntity>>> byColor = new ArrayList<>(DyeColor.values().length);

	public ManaSparkIndex() {
		for (int i = 0; i < DyeColor.values().length; i++) {
			byColor.add(new BlockIndex<>());
		}
	}

	@Nullable
	public static ManaSparkIndex of(Level level) {
		return level instanceof ServerLevel ? BotaniaBlockEntityIndexes.SPARKS.get(level) : null;
	}

	static void track(ManaSparkEntity spark) {
		var index = of(spark.level());
		if (index != null) {
			index.add(spark);
		}
	}

	static void untrack(ManaSparkEntity spark) {
		var index = of(spark.level());
		if (index != null) {
			index.remove(spark);
		}
	}

	static void refresh(ManaSparkEntity spark) {
		if (spark.indexed && (spark.indexKey != keyOf(spark) || spark.indexColor != spark.getNetwork().getId())) {
			var index = of(spark.level());
			if (index != null) {
				index.add(spark);
			}
		}
	}

	private static long keyOf(ManaSparkEntity spark) {
		return BlockPos.asLong(Mth.floor(spark.getX()), Mth.floor(spark.getY()), Mth.floor(spark.getZ()));
	}

	private void add(ManaSparkEntity spark) {
		if (spark.indexed) {
			remove(spark);
		}
		long key = keyOf(spark);
		int color = spark.getNetwork().getId();
		addTo(all, key, spark);
		addTo(byColor.get(color), key, spark);
		spark.indexKey = key;
		spark.indexColor = color;
		spark.indexed = true;
	}

	private void remove(ManaSparkEntity spark) {
		if (!spark.indexed) {
			return;
		}
		spark.indexed = false;
		removeFrom(all, spark.indexKey, spark);
		removeFrom(byColor.get(spark.indexColor), spark.indexKey, spark);
	}

	private static void addTo(BlockIndex<ArrayList<ManaSparkEntity>> index, long key, ManaSparkEntity spark) {
		int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
		var list = index.get(x, y, z);
		if (list == null) {
			list = new ArrayList<>(2);
			if (!index.put(x, y, z, list)) {
				return;
			}
		}
		list.add(spark);
	}

	private static void removeFrom(BlockIndex<ArrayList<ManaSparkEntity>> index, long key, ManaSparkEntity spark) {
		int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
		var list = index.get(x, y, z);
		if (list != null) {
			list.remove(spark);
			if (list.isEmpty()) {
				index.remove(x, y, z, list);
			}
		}
	}

	private static void visit(BlockIndex<ArrayList<ManaSparkEntity>> index, AABB box, Consumer<ArrayList<ManaSparkEntity>> sink) {
		int minX = Mth.floor(box.minX - MARGIN), minY = Mth.floor(box.minY - MARGIN), minZ = Mth.floor(box.minZ - MARGIN);
		int maxX = Mth.floor(box.maxX + MARGIN), maxY = Mth.floor(box.maxY + MARGIN), maxZ = Mth.floor(box.maxZ + MARGIN);
		int cx = (minX + maxX) >> 1, cy = (minY + maxY) >> 1, cz = (minZ + maxZ) >> 1;
		index.query(cx, cy, cz, BlockRange.box(maxX - cx, maxY - cy, maxZ - cz), null, sink);
	}

	public void collect(AABB box, @Nullable DyeColor color, List<ManaSpark> out) {
		var index = color == null ? all : byColor.get(color.getId());
		if (index.isEmpty()) {
			return;
		}
		visit(index, box, list -> {
			for (int i = 0, n = list.size(); i < n; i++) {
				var spark = list.get(i);
				if (spark.getBoundingBox().intersects(box) && !spark.isRemoved()) {
					out.add(spark);
				}
			}
		});
	}

	@Nullable
	public ManaSpark findSingle(AABB box) {
		if (all.isEmpty()) {
			return null;
		}
		List<ManaSpark> found = new ArrayList<>(2);
		visit(all, box, list -> {
			for (int i = 0, n = list.size(); i < n && found.size() < 2; i++) {
				var spark = list.get(i);
				if (spark.getBoundingBox().intersects(box) && !spark.isRemoved()) {
					found.add(spark);
				}
			}
		});
		return found.size() == 1 ? found.get(0) : null;
	}
}
