package vazkii.botania.api.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.hollingsworth.arsnouveau.common.network.BlockEntitySync;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.block.FloatingFlower;
import vazkii.botania.api.block.FloatingFlowerImpl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Common superclass of all magical flower block entities
 */
public abstract class SpecialFlowerBlockEntity extends BlockEntity {

    public static final int PODZOL_DELAY = 5;
    public static final int MYCELIUM_DELAY = 10;

    private static final List<Predicate<SpecialFlowerBlockEntity>> SLOW_TICK_CONDITIONS = new CopyOnWriteArrayList<>();

    private final FloatingFlower floatingData = new FloatingFlowerImpl() {

        @Override
        public ItemStack getDisplayStack() {
            ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType());
            return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(super.getDisplayStack());
        }
    };

    public int ticksExisted = 0;

    /** true if this flower is working on Enchanted Soil **/
    public boolean overgrowth = false;
    /** true if this flower is working on Enchanted Soil and this is the second tick **/
    public boolean overgrowthBoost = false;
    private BlockPos positionOverride;
    private boolean isFloating;
    private boolean slowTicking;
    private boolean observed;
    private int lastSync;
    private BlockEntitySync blockEntitySync;

    public static final String TAG_TICKS_EXISTED = "ticksExisted";
    private static final String TAG_FLOATING_DATA = "floating";

    public SpecialFlowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerSlowTickCondition(Predicate<SpecialFlowerBlockEntity> condition) {
        SLOW_TICK_CONDITIONS.add(condition);
    }

    public final boolean isFloating() {
        return this.isFloating;
    }

    /**
     * WARNING: This should only be called during or soon after construction.
     * Switching between nonfloating/floating at play time is not supported and a fresh
     * Block Entity should be created instead.
     */
    public final void setFloating(boolean floating) {
        this.isFloating = floating;
    }

    @Override
    public final void load(CompoundTag cmp) {
        super.load(cmp);
        if (cmp.contains(TAG_TICKS_EXISTED)) {
            ticksExisted = cmp.getInt(TAG_TICKS_EXISTED);
        }
    }

    @Override
    public final void saveAdditional(CompoundTag cmp) {
        super.saveAdditional(cmp);
        cmp.putInt(TAG_TICKS_EXISTED, ticksExisted);
        writeToPacketNBT(cmp);
    }

    /**
     * Writes some extra data to a network packet. This data is read
     * by readFromPacketNBT on the client that receives the packet.
     * Note: This method is also used to write to the world NBT.
     */
    public void writeToPacketNBT(CompoundTag cmp) {
        if (isFloating()) {
            cmp.put(TAG_FLOATING_DATA, floatingData.writeNBT());
        }
    }

    public void markObserved() {
        observed = true;
    }

    /**
     * Returns a descriptor for the radius of this sub tile. This is called while a player
     * is looking at the block with a Manaseer Monocle.
     */
    @Nullable
    public abstract RadiusDescriptor getRadius();
}
