/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.SectionPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.spark.ManaSpark;

import java.util.ArrayList;
import java.util.List;

// Per-level index of mana sparks by chunk column and network colour, kept in sync from the entity level callback,
// so range lookups only visit nearby sparks of the requested colour instead of every entity in a 25-block box.
public final class ManaSparkIndex {
	private static final double MARGIN = 1.0;

	public interface Holder {
		ManaSparkIndex botania$getSparkIndex();
	}

	private static final class Buckets extends Long2ObjectOpenHashMap<ArrayList<ManaSparkEntity>> {
	}

	private final Buckets all = new Buckets();
	private final Buckets[] byColor = new Buckets[DyeColor.values().length];

	public ManaSparkIndex() {
		for (int i = 0; i < byColor.length; i++) {
			byColor[i] = new Buckets();
		}
	}

	@Nullable
	public static ManaSparkIndex of(Level level) {
		return level instanceof Holder holder ? holder.botania$getSparkIndex() : null;
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
		return ChunkPos.asLong(SectionPos.posToSectionCoord(spark.getX()), SectionPos.posToSectionCoord(spark.getZ()));
	}

	private void add(ManaSparkEntity spark) {
		if (spark.indexed) {
			remove(spark);
		}
		long key = keyOf(spark);
		int color = spark.getNetwork().getId();
		all.computeIfAbsent(key, k -> new ArrayList<>(4)).add(spark);
		byColor[color].computeIfAbsent(key, k -> new ArrayList<>(4)).add(spark);
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
		removeFrom(byColor[spark.indexColor], spark.indexKey, spark);
	}

	private static void removeFrom(Buckets map, long key, ManaSparkEntity spark) {
		var list = map.get(key);
		if (list != null) {
			list.remove(spark);
			if (list.isEmpty()) {
				map.remove(key);
			}
		}
	}

	public void collect(AABB box, @Nullable DyeColor color, List<ManaSpark> out) {
		var map = color == null ? all : byColor[color.getId()];
		if (map.isEmpty()) {
			return;
		}
		int minX = SectionPos.posToSectionCoord(box.minX - MARGIN);
		int minZ = SectionPos.posToSectionCoord(box.minZ - MARGIN);
		int maxX = SectionPos.posToSectionCoord(box.maxX + MARGIN);
		int maxZ = SectionPos.posToSectionCoord(box.maxZ + MARGIN);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				var list = map.get(ChunkPos.asLong(x, z));
				if (list == null) {
					continue;
				}
				for (int i = 0, n = list.size(); i < n; i++) {
					var spark = list.get(i);
					if (spark.getBoundingBox().intersects(box) && !spark.isRemoved()) {
						out.add(spark);
					}
				}
			}
		}
	}

	@Nullable
	public ManaSpark findSingle(AABB box) {
		if (all.isEmpty()) {
			return null;
		}
		ManaSpark found = null;
		int minX = SectionPos.posToSectionCoord(box.minX - MARGIN);
		int minZ = SectionPos.posToSectionCoord(box.minZ - MARGIN);
		int maxX = SectionPos.posToSectionCoord(box.maxX + MARGIN);
		int maxZ = SectionPos.posToSectionCoord(box.maxZ + MARGIN);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				var list = all.get(ChunkPos.asLong(x, z));
				if (list == null) {
					continue;
				}
				for (int i = 0, n = list.size(); i < n; i++) {
					var spark = list.get(i);
					if (spark.getBoundingBox().intersects(box) && !spark.isRemoved()) {
						if (found != null) {
							return null;
						}
						found = spark;
					}
				}
			}
		}
		return found;
	}
}
