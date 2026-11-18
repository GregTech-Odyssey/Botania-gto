package vazkii.botania.common.block.block_entity.mana;

import net.minecraft.world.level.block.Block;

import vazkii.botania.common.block.mana.ManaPools;

/**
 * 魔力池的容量与尺寸参数。
 */
public final class ManaPoolSpec {

    public static final ManaPoolSpec POOL = builder().capacity(ManaPools.Pool.MAX_MANA).build();
    public static final ManaPoolSpec DILUTED = builder().capacity(10_000).build();
    public static final ManaPoolSpec LARGE = builder().footprint(3, 3).capacity(36_000_000L).build();

    private final int width;
    private final int depth;
    private final long capacity;

    private ManaPoolSpec(int width, int depth, long capacity) {
        this.width = width;
        this.depth = depth;
        this.capacity = capacity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int cells() {
        return width * depth;
    }

    public long capacity() {
        return capacity;
    }

    public static ManaPoolSpec of(Block block, ManaPoolSpec fallback) {
        return block instanceof Holder holder ? holder.poolSpec() : fallback;
    }

    public interface Holder {

        ManaPoolSpec poolSpec();
    }

    public static final class Builder {

        private int width = 1;
        private int depth = 1;
        private long capacity;

        private Builder() {}

        public Builder footprint(int width, int depth) {
            this.width = width;
            this.depth = depth;
            return this;
        }

        public Builder capacity(long capacity) {
            this.capacity = capacity;
            return this;
        }

        public ManaPoolSpec build() {
            if (width <= 0 || depth <= 0 || capacity <= 0) {
                throw new IllegalArgumentException("Invalid mana pool spec");
            }
            return new ManaPoolSpec(width, depth, capacity);
        }
    }
}
