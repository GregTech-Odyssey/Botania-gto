/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.block_entity.mana;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.common.block.mana.LargeManaPoolBlock;
import vazkii.botania.common.entity.ManaSparkEntity;
import vazkii.botania.common.entity.ManaSparkIndex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class LargeManaPoolNetwork {
	private static final List<LargeManaPoolBlockEntity> LOADED_PARTS = new ArrayList<>();

	private final Level level;
	private final List<LargeManaPoolBlockEntity> cores;
	private final LongOpenHashSet parts;
	private final AABB sparkArea;
	private final long capacity;
	private long mana;
	private boolean outputting;
	private int colorId;
	private boolean dissolved;
	private boolean changed;
	private long lastCraftSound = -1000;
	@Nullable
	private ManaSpark spark;
	private long sparkCheckedTick = -1000;
	private int comparatorLevel = -1;
	private long lastTick = -1000;
	private int ticks;

	private LargeManaPoolNetwork(Level level, List<LargeManaPoolBlockEntity> cores, LongOpenHashSet parts) {
		this.level = level;
		this.cores = cores;
		this.parts = parts;
		long total = 0;
		for (var core : cores) {
			total += core.poolSpec().capacity();
		}
		this.capacity = total;
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		int y = cores.get(0).getBlockPos().getY();
		for (long packed : parts) {
			int x = BlockPos.getX(packed), z = BlockPos.getZ(packed);
			minX = Math.min(minX, x);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxZ = Math.max(maxZ, z);
		}
		this.sparkArea = new AABB(minX, y + 1, minZ, maxX + 1, y + 2, maxZ + 1);
	}

	static LargeManaPoolNetwork form(LargeManaPoolBlockEntity start) {
		Level level = start.getLevel();
		Block block = start.getBlockState().getBlock();
		LongOpenHashSet visited = new LongOpenHashSet();
		LongOpenHashSet parts = new LongOpenHashSet();
		ReferenceLinkedOpenHashSet<LargeManaPoolBlockEntity> found = new ReferenceLinkedOpenHashSet<>();
		ArrayDeque<LargeManaPoolBlockEntity> queue = new ArrayDeque<>();
		queue.add(start);
		visited.add(start.getBlockPos().asLong());
		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		while (!queue.isEmpty()) {
			var part = queue.poll();
			var core = part.getCore();
			if (core == null || core.isRemoved()) {
				continue;
			}
			BlockPos pos = part.getBlockPos();
			parts.add(pos.asLong());
			found.add(core);
			BlockPos corePos = core.getBlockPos();
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				next.setWithOffset(pos, dir);
				long key = next.asLong();
				if (visited.contains(key) || !level.isLoaded(next)
						|| !(level.getBlockEntity(next) instanceof LargeManaPoolBlockEntity neighbor)
						|| !neighbor.getBlockState().is(block)
						|| !LargeManaPoolBlock.linked(corePos, neighbor.getCorePos())) {
					continue;
				}
				visited.add(key);
				queue.add(neighbor);
			}
		}
		found.add(start);

		Set<LargeManaPoolNetwork> previous = new ReferenceOpenHashSet<>();
		for (var core : found) {
			if (core.network != null && !core.network.dissolved) {
				previous.add(core.network);
			}
		}
		LargeManaPoolNetwork primary = null;
		for (var net : previous) {
			if (primary == null || net.cores.size() > primary.cores.size()
					|| net.cores.size() == primary.cores.size() && net.mana > primary.mana) {
				primary = net;
			}
		}
		ManaSpark preferred = primary != null ? primary.spark : null;
		for (var net : previous) {
			net.dissolve();
		}

		List<LargeManaPoolBlockEntity> sorted = new ArrayList<>(found);
		sorted.sort(Comparator.comparingLong(be -> be.getBlockPos().asLong()));
		var result = new LargeManaPoolNetwork(level, sorted, parts);
		var settings = primary != null ? null : sorted.get(0);
		result.outputting = primary != null ? primary.outputting : settings.outputting;
		result.colorId = primary != null ? primary.colorId : settings.color;
		long total = 0;
		for (int i = 0; i < sorted.size(); i++) {
			var core = sorted.get(i);
			total += core.mana;
			core.network = result;
			core.networkIndex = i;
		}
		result.mana = Math.min(total, result.capacity);
		result.spark = preferred;
		for (var core : sorted) {
			core.showGroup(result, true);
		}
		return result;
	}

	static void onPartLoaded(LargeManaPoolBlockEntity part) {
		LOADED_PARTS.add(part);
	}

	public static void processLoadedParts(Level level) {
		if (LOADED_PARTS.isEmpty()) {
			return;
		}
		List<LargeManaPoolBlockEntity> due = null;
		var it = LOADED_PARTS.iterator();
		while (it.hasNext()) {
			var part = it.next();
			if (part.getLevel() == level || part.getLevel() == null || part.isRemoved()) {
				it.remove();
				if (part.getLevel() == level && !part.isRemoved()) {
					if (due == null) {
						due = new ArrayList<>();
					}
					due.add(part);
				}
			}
		}
		if (due == null) {
			return;
		}
		for (var part : due) {
			Set<LargeManaPoolNetwork> touching = new ReferenceOpenHashSet<>();
			addNetworkOf(part, touching);
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos next = part.getBlockPos().relative(dir);
				if (level.isLoaded(next) && level.getBlockEntity(next) instanceof LargeManaPoolBlockEntity neighbor
						&& neighbor.getBlockState().is(part.getBlockState().getBlock())
						&& LargeManaPoolBlock.linked(part.getCorePos(), neighbor.getCorePos())) {
					addNetworkOf(neighbor, touching);
				}
			}
			if (touching.size() > 1) {
				for (var net : touching) {
					net.dissolve();
				}
			}
		}
	}

	private static void addNetworkOf(LargeManaPoolBlockEntity part, Set<LargeManaPoolNetwork> out) {
		var core = part.getCore();
		if (core != null && core.network != null && !core.network.dissolved) {
			out.add(core.network);
		}
	}

	void dissolve() {
		if (dissolved) {
			return;
		}
		dissolved = true;
		for (int i = 0; i < cores.size(); i++) {
			var core = cores.get(i);
			core.mana = shareOf(i);
			core.outputting = outputting;
			core.color = colorId;
			if (core.network == this) {
				core.network = null;
			}
			core.setChanged();
		}
	}

	private long shareOf(int index) {
		int n = cores.size();
		return mana / n + (index < mana % n ? 1 : 0);
	}

	long shareOf(LargeManaPoolBlockEntity core) {
		int index = core.network == this ? core.networkIndex : cores.indexOf(core);
		return index < 0 ? core.mana : shareOf(index);
	}

	public boolean isDissolved() {
		return dissolved;
	}

	public LargeManaPoolBlockEntity leader() {
		return cores.get(0);
	}

	public long getMana() {
		return mana;
	}

	public long getCapacity() {
		return capacity;
	}

	public int getUnits() {
		return cores.size();
	}

	public boolean isOutputting() {
		return outputting;
	}

	public int getColorId() {
		return colorId;
	}

	void receive(long delta) {
		long next = Math.max(0, Math.min(capacity, mana + delta));
		if (next != mana) {
			mana = next;
			changed = true;
		}
	}

	void markChanged() {
		changed = true;
	}

	void setOutputting(boolean value) {
		outputting = value;
		publishSettings();
	}

	void setColorId(int value) {
		colorId = value;
		publishSettings();
	}

	private void publishSettings() {
		for (var core : cores) {
			core.outputting = outputting;
			core.color = colorId;
			core.setChanged();
			core.showGroup(this, false);
		}
	}

	boolean claimCraftSound(long gameTime) {
		if (gameTime - lastCraftSound < 6) {
			return false;
		}
		lastCraftSound = gameTime;
		return true;
	}

	void tick() {
		long now = level.getGameTime();
		if (now == lastTick) {
			return;
		}
		lastTick = now;
		ticks++;
		if (changed && ticks % 10 == 0) {
			changed = false;
			leader().showGroup(this, false);
			for (var core : cores) {
				level.blockEntityChanged(core.getBlockPos());
			}
			int comparator = comparatorLevel();
			if (comparator != comparatorLevel) {
				comparatorLevel = comparator;
				Block block = leader().getBlockState().getBlock();
				BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
				for (long packed : parts) {
					pos.set(packed);
					if (level.isLoaded(pos)) {
						level.updateNeighbourForOutputSignal(pos, block);
					}
				}
			}
		}
		if (level.getGameTime() - sparkCheckedTick >= 20) {
			resolveSparks();
		}
	}

	private int comparatorLevel() {
		int val = (int) ((double) mana / capacity * 15.0);
		return mana > 0 ? Math.max(1, Math.min(15, val)) : 0;
	}

	void preferSpark(ManaSpark entity) {
		spark = entity;
		sparkCheckedTick = -1000;
	}

	@Nullable
	ManaSpark getSpark() {
		if (level.getGameTime() != sparkCheckedTick) {
			resolveSparks();
		}
		return spark;
	}

	void resolveSparksNow() {
		if (!dissolved) {
			resolveSparks();
		}
	}

	private void resolveSparks() {
		sparkCheckedTick = level.getGameTime();
		var index = ManaSparkIndex.of(level);
		if (index == null) {
			return;
		}
		List<ManaSpark> nearby = new ArrayList<>();
		index.collect(sparkArea, null, nearby);
		List<ManaSpark> attached = new ArrayList<>();
		List<ManaSparkEntity> mirrors = new ArrayList<>();
		for (var candidate : nearby) {
			if (candidate.entity().isRemoved() || !parts.contains(candidate.entity().blockPosition().below().asLong())) {
				continue;
			}
			if (candidate instanceof ManaSparkEntity entity && entity.isMirror()) {
				mirrors.add(entity);
			} else {
				attached.add(candidate);
			}
		}
		ManaSpark keep = spark != null && attached.contains(spark) ? spark : null;
		if (keep == null && !attached.isEmpty()) {
			attached.sort(Comparator.comparingLong(s -> s.entity().blockPosition().asLong()));
			keep = attached.get(0);
		}
		for (var other : attached) {
			if (other != keep && other instanceof ManaSparkEntity entity) {
				entity.dropAndKill();
			}
		}
		spark = keep;
		syncMirrors(keep, mirrors);
	}

	private void syncMirrors(@Nullable ManaSpark source, List<ManaSparkEntity> mirrors) {
		if (!(source instanceof ManaSparkEntity master)) {
			for (var mirror : mirrors) {
				mirror.discard();
			}
			return;
		}
		BlockPos masterPart = master.blockPosition().below();
		long masterCore = level.getBlockEntity(masterPart) instanceof LargeManaPoolBlockEntity part ? part.getCorePos().asLong() : masterPart.asLong();
		LongOpenHashSet wanted = new LongOpenHashSet(cores.size());
		for (var core : cores) {
			long key = core.getBlockPos().asLong();
			if (key != masterCore) {
				wanted.add(key);
			}
		}
		for (var mirror : mirrors) {
			if (wanted.remove(mirror.blockPosition().below().asLong())) {
				mirror.copyMirrorState(master);
			} else {
				mirror.discard();
			}
		}
		var it = wanted.iterator();
		while (it.hasNext()) {
			BlockPos core = BlockPos.of(it.nextLong());
			ManaSparkEntity mirror = new ManaSparkEntity(level);
			mirror.setMirror(true);
			mirror.setPos(core.getX() + 0.5, core.getY() + 1.25, core.getZ() + 0.5);
			mirror.copyMirrorState(master);
			level.addFreshEntity(mirror);
		}
	}
}
