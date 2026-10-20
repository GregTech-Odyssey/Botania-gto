/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.handler;

import com.suried.gtoutils.spatial.BlockIndex;
import com.suried.gtoutils.spatial.BlockRange;
import com.suried.gtoutils.spatial.LevelBlockIndex;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.internal.ManaNetwork;
import vazkii.botania.api.mana.*;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.*;

public final class ManaNetworkHandler implements ManaNetwork {

	public static final ManaNetworkHandler instance = new ManaNetworkHandler();

	private final Map<Level, Set<ManaPool>> manaPools = new WeakHashMap<>();
	private final Map<Level, Set<ManaCollector>> manaCollectors = new WeakHashMap<>();
	private final LevelBlockIndex<ManaPool> poolIndex = LevelBlockIndex.create();
	private final LevelBlockIndex<ManaCollector> collectorIndex = LevelBlockIndex.create();

	public void onNetworkEvent(ManaReceiver thing, ManaBlockType type, ManaNetworkAction action) {
		switch (type) {
			case COLLECTOR -> {
				if (action == ManaNetworkAction.ADD) {
					add(manaCollectors, thing.getManaReceiverLevel(), (ManaCollector) thing);
					collectorIndex.get(thing.getManaReceiverLevel()).put(thing.getManaReceiverPos(), (ManaCollector) thing);
				} else {
					remove(manaCollectors, thing.getManaReceiverLevel(), (ManaCollector) thing);
					unindex(collectorIndex, thing.getManaReceiverLevel(), thing.getManaReceiverPos(), (ManaCollector) thing);
				}
			}
			case POOL -> {
				if (action == ManaNetworkAction.ADD) {
					add(manaPools, thing.getManaReceiverLevel(), (ManaPool) thing);
					poolIndex.get(thing.getManaReceiverLevel()).put(thing.getManaReceiverPos(), (ManaPool) thing);
				} else {
					remove(manaPools, thing.getManaReceiverLevel(), (ManaPool) thing);
					unindex(poolIndex, thing.getManaReceiverLevel(), thing.getManaReceiverPos(), (ManaPool) thing);
				}
			}
		}
	}

	@Override
	public void clear() {
		for (Level level : manaPools.keySet()) {
			poolIndex.release(level);
		}
		for (Level level : manaCollectors.keySet()) {
			collectorIndex.release(level);
		}
		manaPools.clear();
		manaCollectors.clear();
	}

	@Override
	public ManaPool getClosestPool(BlockPos pos, Level world, int limit) {
		return getClosest(poolIndex.getIfPresent(world), pos, limit);
	}

	@Override
	public ManaCollector getClosestCollector(BlockPos pos, Level world, int limit) {
		return getClosest(collectorIndex.getIfPresent(world), pos, limit);
	}

	public boolean isCollectorIn(Level level, ManaCollector collector) {
		return manaCollectors.getOrDefault(level, Collections.emptySet()).contains(collector);
	}

	public boolean isPoolIn(Level level, ManaPool pool) {
		return manaPools.getOrDefault(level, Collections.emptySet()).contains(pool);
	}

	@Nullable
	private static <T extends ManaReceiver> T getClosest(@Nullable BlockIndex<T> index, BlockPos pos, int limit) {
		return index == null ? null : index.findNearest(pos, BlockRange.sphere(limit), null);
	}

	private static <T> void unindex(LevelBlockIndex<T> index, Level level, BlockPos pos, T thing) {
		var levelIndex = index.getIfPresent(level);
		if (levelIndex != null) {
			levelIndex.remove(pos, thing);
		}
	}

	private <T> void remove(Map<Level, Set<T>> map, Level level, T thing) {
		if (!map.containsKey(level)) {
			return;
		}

		var set = map.get(level);
		set.remove(thing);
		if (set.isEmpty()) {
			map.remove(level);
		}
	}

	private <T> void add(Map<Level, Set<T>> map, Level level, T thing) {
		map.computeIfAbsent(level, k -> Collections.newSetFromMap(new WeakHashMap<>())).add(thing);
	}

	@Override
	public Set<ManaCollector> getAllCollectorsInWorld(Level world) {
		return getAllInWorld(manaCollectors, world);
	}

	@Override
	public Set<ManaPool> getAllPoolsInWorld(Level world) {
		return getAllInWorld(manaPools, world);
	}

	@Override
	public void fireManaNetworkEvent(ManaReceiver thing, ManaBlockType type, ManaNetworkAction action) {
		XplatAbstractions.INSTANCE.fireManaNetworkEvent(thing, type, action);
	}

	private <T> Set<T> getAllInWorld(Map<Level, Set<T>> map, Level world) {
		Set<T> ret = map.get(world);
		if (ret == null) {
			return Collections.emptySet();
		} else {
			return Collections.unmodifiableSet(ret);
		}
	}

}
