package vazkii.botania.forge.integration.ae2;

import vazkii.botania.api.mana.ManaCollector;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.SparkAttachable;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.block.mana.ManaPoolBlock;

final class ManaAccess {
	private ManaAccess() {}

	static boolean canExtract(ManaReceiver receiver) {
		return receiver instanceof ManaPool || receiver instanceof ManaCollector;
	}

	private static boolean isCreative(ManaReceiver receiver) {
		return receiver instanceof ManaPoolBlockEntity pool && pool.getBlockState().getBlock() instanceof ManaPoolBlock block
				&& block.variant == ManaPoolBlock.Variant.CREATIVE;
	}

	static long space(ManaReceiver receiver) {
		if (receiver.isFull()) {
			return 0;
		}
		if (receiver instanceof SparkAttachable attachable) {
			return Math.max(0, attachable.getAvailableSpaceForManaLong());
		}
		if (receiver instanceof ManaPool pool) {
			return Math.max(0, pool.getMaxManaLong() - pool.getCurrentManaLong());
		}
		if (receiver instanceof ManaCollector collector) {
			return Math.max(0, (long) collector.getMaxMana() - collector.getCurrentMana());
		}
		return Integer.MAX_VALUE;
	}

	static long insert(ManaReceiver receiver, long amount, boolean simulate) {
		if (amount <= 0 || isCreative(receiver)) {
			return 0;
		}
		long accept = Math.min(amount, space(receiver));
		if (accept <= 0 || simulate) {
			return Math.max(0, accept);
		}
		long before = receiver.getCurrentManaLong();
		receiver.receiveManaLong(accept);
		return Math.max(0, Math.min(accept, receiver.getCurrentManaLong() - before));
	}

	static long extract(ManaReceiver receiver, long amount, boolean simulate) {
		if (amount <= 0 || !canExtract(receiver)) {
			return 0;
		}
		long take = Math.min(amount, receiver.getCurrentManaLong());
		if (take <= 0 || simulate || isCreative(receiver)) {
			return Math.max(0, take);
		}
		long before = receiver.getCurrentManaLong();
		receiver.receiveManaLong(-take);
		return Math.max(0, Math.min(take, before - receiver.getCurrentManaLong()));
	}

	static long stored(ManaReceiver receiver) {
		return canExtract(receiver) ? Math.max(0, receiver.getCurrentManaLong()) : 0;
	}
}
