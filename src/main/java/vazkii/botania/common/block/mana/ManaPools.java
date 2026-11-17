package vazkii.botania.common.block.mana;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.state.BotaniaStateProperties.OptionalDyeColor;
import vazkii.botania.common.BotaniaReplica;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntities;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntity;
import vazkii.botania.common.block.block_entity.mana.ManaPoolSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static vazkii.botania.api.state.BotaniaStateProperties.OPTIONAL_DYE_COLOR;

/**
 * 魔力池系列：单个魔力池与 3×3 大型魔力池共用同一套方块/方块实体实现。
 *
 * <p>
 * 不再区分 {@code ManaPoolBlock}/{@code LargeManaPoolBlock} 与两套方块实体，参照
 * {@code LaurerilManaPools} 的写法集中在一个类里；容量差异由 {@code ManaPoolSpec} 决定。
 * </p>
 */
public final class ManaPools {

    private ManaPools() {}

    /**
     * 魔力池方块。四种变体共用一套方块实体，容量由 {@link ManaPoolSpec} 决定。
     */
    public static class PoolBlock extends Block implements EntityBlock, ManaPoolSpec.Holder {

        private static final VoxelShape NORMAL_SHAPE;
        private static final VoxelShape DILUTED_SHAPE;
        private static final VoxelShape CREATIVE_SHAPE;
        private static final VoxelShape NORMAL_SHAPE_INTERACT;
        private static final VoxelShape DILUTED_SHAPE_INTERACT;
        private static final VoxelShape CREATIVE_SHAPE_INTERACT;

        static {
            NORMAL_SHAPE_INTERACT = box(0, 0, 0, 16, 8, 16);
            DILUTED_SHAPE_INTERACT = box(0, 0, 0, 16, 6, 16);
            CREATIVE_SHAPE_INTERACT = box(0, 0, 0, 16, 10, 16);

            VoxelShape cutout = box(2, 2, 2, 14, 16, 14);
            VoxelShape dilutedCutout = box(1, 1, 1, 15, 6, 15);

            NORMAL_SHAPE = Shapes.join(NORMAL_SHAPE_INTERACT, cutout, BooleanOp.ONLY_FIRST);
            DILUTED_SHAPE = Shapes.join(DILUTED_SHAPE_INTERACT, dilutedCutout, BooleanOp.ONLY_FIRST);
            CREATIVE_SHAPE = Shapes.join(CREATIVE_SHAPE_INTERACT, cutout, BooleanOp.ONLY_FIRST);
        }

        public enum Variant {
            DEFAULT,
            CREATIVE,
            DILUTED,
            FABULOUS
        }

        public final Variant variant;
        private final ManaPoolSpec spec;

        public PoolBlock(Variant variant, Properties properties) {
            this(variant, properties, variant == Variant.DILUTED ? ManaPoolSpec.DILUTED : ManaPoolSpec.POOL);
        }

        public PoolBlock(Variant variant, Properties properties, ManaPoolSpec spec) {
            super(properties);
            this.variant = variant;
            this.spec = spec;
            registerDefaultState(defaultBlockState().setValue(OPTIONAL_DYE_COLOR, OptionalDyeColor.NONE));
        }

        @Override
        public ManaPoolSpec poolSpec() {
            return spec;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(OPTIONAL_DYE_COLOR);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            if (variant == Variant.CREATIVE) {
                for (int i = 0; i < 2; i++) {
                    tooltip.add(Component.translatable("botaniamisc.creativePool" + i).withStyle(ChatFormatting.GRAY));
                }
            } else if (variant == Variant.FABULOUS) {
                tooltip.add(Component.translatable("botaniamisc.fabulousPool0").withStyle(ChatFormatting.GRAY));
            }
        }

        @NotNull
        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return switch (variant) {
                case DILUTED -> DILUTED_SHAPE;
                case CREATIVE -> CREATIVE_SHAPE;
                case DEFAULT, FABULOUS -> NORMAL_SHAPE;
            };
        }

        @NotNull
        @Override
        public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
            return switch (variant) {
                case DILUTED -> DILUTED_SHAPE_INTERACT;
                case CREATIVE -> CREATIVE_SHAPE_INTERACT;
                case DEFAULT, FABULOUS -> NORMAL_SHAPE_INTERACT;
            };
        }

        @NotNull
        @Override
        public InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, Player player,
                                     @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Pool pool)) {
                return super.use(state, level, pos, player, hand, hit);
            }
            ItemStack stack = player.getItemInHand(hand);

            // 花瓣与蘑菇给池染色，黏土球清除颜色
            Optional<DyeColor> itemColor = colorOf(stack);
            if (itemColor.isPresent()) {
                if (!itemColor.equals(pool.getColor())) {
                    pool.setColor(itemColor);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide());
                }
                return InteractionResult.PASS;
            }
            if (stack.is(net.minecraft.world.item.Items.CLAY_BALL) && pool.getColor().isPresent()) {
                pool.setColor(Optional.empty());
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            return super.use(state, level, pos, player, hand, hit);
        }

        /** 依物品注册名推断颜色：{@code botania:<color>_petal} 与 {@code botania:<color>_mushroom}。 */
        private static Optional<DyeColor> colorOf(ItemStack stack) {
            if (stack.isEmpty()) return Optional.empty();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!"botania".equals(id.getNamespace())) return Optional.empty();
            String path = id.getPath();
            String colorName;
            if (path.endsWith("_petal")) {
                colorName = path.substring(0, path.length() - "_petal".length());
            } else if (path.endsWith("_mushroom")) {
                colorName = path.substring(0, path.length() - "_mushroom".length());
            } else {
                return Optional.empty();
            }
            return Optional.ofNullable(DyeColor.byName(colorName, null));
        }

        @NotNull
        @Override
        public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
            return new Pool(pos, state);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return null;
        }

        @NotNull
        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public boolean hasAnalogOutputSignal(BlockState state) {
            return true;
        }

        @Override
        public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
            return level.getBlockEntity(pos) instanceof Pool pool ? Pool.calculateComparatorLevel(pool.getCurrentMana(), pool.getMaxMana()) : 0;
        }
    }

    /**
     * 大型魔力池。
     *
     * <p>
     * 保持原模组的多单元外观：每个单元记录自己相对 3×3 核心的偏移，只有同一核心的单元互相连接，
     * 两个相邻的池不会被合并；核心与单元的关系决定围栏、碰撞形状、核心标记与掉落。
     * 容量来自 {@link ManaPoolSpec#LARGE}。
     * </p>
     */
    public static final class LargeBlock extends PoolBlock {

        public static final BooleanProperty CORE = BooleanProperty.create("core");
        public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
        public static final BooleanProperty EAST = BlockStateProperties.EAST;
        public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
        public static final BooleanProperty WEST = BlockStateProperties.WEST;
        public static final BooleanProperty NORTH_EAST = BooleanProperty.create("north_east");
        public static final BooleanProperty SOUTH_EAST = BooleanProperty.create("south_east");
        public static final BooleanProperty SOUTH_WEST = BooleanProperty.create("south_west");
        public static final BooleanProperty NORTH_WEST = BooleanProperty.create("north_west");

        public static final int FLOOR = 4;
        public static final int RIM = 4;

        private static final VoxelShape[] SHAPES = new VoxelShape[256];

        static {
            for (int i = 0; i < SHAPES.length; i++) {
                boolean n = (i & 1) != 0;
                boolean e = (i & 2) != 0;
                boolean s = (i & 4) != 0;
                boolean w = (i & 8) != 0;
                boolean ne = (i & 16) != 0;
                boolean se = (i & 32) != 0;
                boolean sw = (i & 64) != 0;
                boolean nw = (i & 128) != 0;
                VoxelShape shape = box(0, 0, 0, 16, FLOOR, 16);
                if (!n) {
                    shape = Shapes.or(shape, box(0, FLOOR, 0, 16, 16, RIM));
                }
                if (!s) {
                    shape = Shapes.or(shape, box(0, FLOOR, 16 - RIM, 16, 16, 16));
                }
                if (!w) {
                    shape = Shapes.or(shape, box(0, FLOOR, 0, RIM, 16, 16));
                }
                if (!e) {
                    shape = Shapes.or(shape, box(16 - RIM, FLOOR, 0, 16, 16, 16));
                }
                if (n && e && !ne) {
                    shape = Shapes.or(shape, box(16 - RIM, FLOOR, 0, 16, 16, RIM));
                }
                if (s && e && !se) {
                    shape = Shapes.or(shape, box(16 - RIM, FLOOR, 16 - RIM, 16, 16, 16));
                }
                if (s && w && !sw) {
                    shape = Shapes.or(shape, box(0, FLOOR, 16 - RIM, RIM, 16, 16));
                }
                if (n && w && !nw) {
                    shape = Shapes.or(shape, box(0, FLOOR, 0, RIM, 16, RIM));
                }
                SHAPES[i] = shape.optimize();
            }
        }

        private final BiFunction<BlockPos, BlockState, ? extends Large> factory;

        public LargeBlock(Properties properties) {
            this(properties, ManaPoolSpec.LARGE, Large::new);
        }

        public LargeBlock(Properties properties, ManaPoolSpec spec,
                          BiFunction<BlockPos, BlockState, ? extends Large> factory) {
            super(Variant.DEFAULT, properties, spec);
            this.factory = factory;
            registerDefaultState(defaultBlockState()
                    .setValue(CORE, false)
                    .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
                    .setValue(NORTH_EAST, false).setValue(SOUTH_EAST, false).setValue(SOUTH_WEST, false)
                    .setValue(NORTH_WEST, false));
        }

        @NotNull
        @Override
        public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
            return factory.apply(pos, state);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return null;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(CORE, NORTH, EAST, SOUTH, WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST);
        }

        private static int shapeIndex(BlockState state) {
            return (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0) | (state.getValue(NORTH_EAST) ? 16 : 0) | (state.getValue(SOUTH_EAST) ? 32 : 0) | (state.getValue(SOUTH_WEST) ? 64 : 0) | (state.getValue(NORTH_WEST) ? 128 : 0);
        }

        @NotNull
        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[shapeIndex(state)];
        }

        @NotNull
        @Override
        public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
            return SHAPES[shapeIndex(state)];
        }

        // ==================== 连接 ====================

        /** 按各单元记录的核心位置计算连接状态：同一核心相连，两个核心相距 3 格时视为两池相邻。 */
        public BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
            BlockPos n = pos.north();
            BlockPos e = pos.east();
            BlockPos s = pos.south();
            BlockPos w = pos.west();
            BlockPos ne = n.east();
            BlockPos se = s.east();
            BlockPos sw = s.west();
            BlockPos nw = n.west();
            if (!loaded(level, n) || !loaded(level, e) || !loaded(level, s) || !loaded(level, w) || !loaded(level, ne) || !loaded(level, se) || !loaded(level, sw) || !loaded(level, nw)) {
                return state;
            }
            BlockPos self = coreOf(level, pos);
            BlockPos cn = coreOf(level, n);
            BlockPos ce = coreOf(level, e);
            BlockPos cs = coreOf(level, s);
            BlockPos cw = coreOf(level, w);
            BlockPos cne = coreOf(level, ne);
            BlockPos cse = coreOf(level, se);
            BlockPos csw = coreOf(level, sw);
            BlockPos cnw = coreOf(level, nw);
            return state.setValue(NORTH, linked(self, cn))
                    .setValue(EAST, linked(self, ce))
                    .setValue(SOUTH, linked(self, cs))
                    .setValue(WEST, linked(self, cw))
                    .setValue(NORTH_EAST, linked(cn, cne) && linked(ce, cne))
                    .setValue(SOUTH_EAST, linked(cs, cse) && linked(ce, cse))
                    .setValue(SOUTH_WEST, linked(cs, csw) && linked(cw, csw))
                    .setValue(NORTH_WEST, linked(cn, cnw) && linked(cw, cnw));
        }

        private static boolean loaded(BlockGetter level, BlockPos pos) {
            return !(level instanceof LevelReader reader) || reader.hasChunkAt(pos);
        }

        @Nullable
        private BlockPos coreOf(BlockGetter level, BlockPos pos) {
            return level.getBlockEntity(pos) instanceof Large part && part.getBlockState().is(this) ? part.getCorePos() : null;
        }

        /** 两个单元是否属于同一池，或分属两个刚好相邻的池。 */
        public static boolean linked(@Nullable BlockPos coreA, @Nullable BlockPos coreB) {
            if (coreA == null || coreB == null || coreA.getY() != coreB.getY()) {
                return false;
            }
            int dx = Math.abs(coreB.getX() - coreA.getX());
            int dz = Math.abs(coreB.getZ() - coreA.getZ());
            return dx == 0 && dz == 0 || dx == 3 && dz == 0 || dx == 0 && dz == 3;
        }

        /** 刷新以某点为中心、给定半径内所有本方块单元的连接与核心标记。 */
        public void refreshConnections(Level level, BlockPos center, int radius) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, 0, radius))) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.is(this)) {
                    continue;
                }
                BlockState updated = withConnections(level, pos, state);
                if (level.getBlockEntity(pos) instanceof Large pool && updated.getValue(CORE) != pool.isCore()) {
                    updated = updated.setValue(CORE, pool.isCore());
                }
                if (updated != state) {
                    level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
                }
            }
        }

        @NotNull
        @Override
        public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighborState,
                                      @NotNull LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
            return direction.getAxis().isHorizontal() ? withConnections(level, pos, state) : state;
        }

        @Override
        public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
            super.onPlace(state, level, pos, oldState, isMoving);
            if (!level.isClientSide) {
                refreshConnections(level, pos, 2);
            }
        }

        @Override
        public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
            super.neighborChanged(state, level, pos, block, fromPos, isMoving);
            if (!level.isClientSide) {
                refreshConnections(level, pos, 2);
            }
        }

        // ==================== 破坏 ====================

        /** 只有核心掉落物品；非核心单元由 {@link #playerWillDestroy} 代为掉出核心的那一份。 */
        @NotNull
        @Override
        public java.util.List<net.minecraft.world.item.ItemStack> getDrops(@NotNull BlockState state, @NotNull net.minecraft.world.level.storage.loot.LootParams.Builder params) {
            return state.getValue(CORE) ? super.getDrops(state, params) : java.util.List.of();
        }

        /** 破坏非核心单元时，把核心的资源也一并掉出，并移除核心（随后由 onRemove 级联清掉其余单元）。 */
        @Override
        public void playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
            if (!level.isClientSide && !state.getValue(CORE) && level.getBlockEntity(pos) instanceof Large part) {
                BlockPos core = part.getCorePos();
                BlockState coreState = level.isLoaded(core) ? level.getBlockState(core) : null;
                if (coreState != null && coreState.is(this)) {
                    if (!player.isCreative() && player.hasCorrectToolForDrops(coreState)) {
                        Block.dropResources(coreState, level, core, level.getBlockEntity(core), player, player.getMainHandItem());
                    }
                    level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            super.playerWillDestroy(level, pos, state, player);
        }

        /** 核心被破坏时清掉同一池的其余单元；其他方式移除单元时连带破坏核心，保证整池一起消失。 */
        @Override
        public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moved) {
            if (!newState.is(this) && !level.isClientSide && level.getBlockEntity(pos) instanceof Large part) {
                BlockPos core = part.getCorePos();
                if (state.getValue(CORE)) {
                    for (BlockPos other : BlockPos.betweenClosed(core.offset(-1, 0, -1), core.offset(1, 0, 1))) {
                        if (!other.equals(pos) && level.isLoaded(other) && level.getBlockEntity(other) instanceof Large otherPart && otherPart.getCorePos().equals(core)) {
                            level.setBlock(other.immutable(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                    refreshConnections(level, core, 2);
                } else if (level.isLoaded(core) && level.getBlockState(core).is(this)) {
                    level.destroyBlock(core, true);
                }
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    /**
     * 魔力池方块实体。
     *
     * <p>
     * 移植原模组的魔力存储、注入物品传输、输出模式与钥匙锁定；火花附着与注入配方由后续类接入。
     * 客户端只同步储量与颜色，视觉由方块模型负责。
     * </p>
     */
    public static class Pool extends BotaniaBlockEntity
                             implements ManaPool, ManaPoolSpec.Holder {

        public static final int MAX_MANA = 1000000;

        private final ManaPoolSpec spec;
        /** 是否允许存储魔力；Botania 魔力池仅保留外观，GT 秘金池等子类开启。 */
        protected boolean storageEnabled = false;
        @SaveToDisk
        @SyncToClient
        protected long mana;
        @SaveToDisk
        @SyncToClient
        private int colorId = -1;

        public Pool(BlockPos pos, BlockState state) {
            this(BotaniaBlockEntities.MANA_POOL.get(), pos, state);
        }

        public Pool(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
            this.spec = ManaPoolSpec.of(state.getBlock(), ManaPoolSpec.POOL);
        }

        @Override
        public ManaPoolSpec poolSpec() {
            return spec;
        }

        // ==================== 储量 ====================

        @Override
        public int getCurrentMana() {
            return (int) Math.min(Integer.MAX_VALUE, getCurrentManaLong());
        }

        @Override
        public long getCurrentManaLong() {
            return storageEnabled ? mana : 0;
        }

        @Override
        public int getMaxMana() {
            return (int) Math.min(Integer.MAX_VALUE, spec.capacity());
        }

        @Override
        public long getMaxManaLong() {
            return spec.capacity();
        }

        /** 下方是魔力销毁器时视为永不装满。 */
        public boolean isVoidBelow() {
            return level != null && level.getBlockState(worldPosition.below()).getBlock() == BotaniaReplica.BOTANIA.getBlock("mana_void");
        }

        @Override
        public boolean isFull() {
            return !storageEnabled || (!isVoidBelow() && mana >= getMaxManaLong());
        }

        @Override
        public void receiveMana(int mana) {
            receiveManaLong(mana);
        }

        @Override
        public void receiveManaLong(long delta) {
            if (!storageEnabled) return;
            if (delta > 0) {
                long room = isVoidBelow() ? Long.MAX_VALUE : Math.max(0, getMaxManaLong() - mana);
                delta = Math.min(delta, room);
            }
            if (delta == 0) return;
            setMana(Math.max(0, mana + delta));
        }

        @Override
        public long addMana(long amount, boolean simulate) {
            if (!storageEnabled || amount <= 0) return 0;
            long room = isVoidBelow() ? amount : Math.max(0, getMaxManaLong() - mana);
            long change = Math.min(amount, room);
            if (change > 0 && !simulate) {
                setMana(mana + change);
            }
            return change;
        }

        @Override
        public long removeMana(long amount, boolean simulate) {
            if (!storageEnabled) return 0;
            long change = Math.min(amount, mana);
            if (change <= 0) return 0;
            if (!simulate) {
                setMana(mana - change);
            }
            return change;
        }

        protected void setMana(long value) {
            mana = value;
        }

        // ==================== 输出与传输 ====================

        @Override
        public boolean isOutputtingPower() {
            return false;
        }

        @Override
        public boolean canReceiveManaFromBursts() {
            return true;
        }

        @Override
        public Level getManaReceiverLevel() {
            return level;
        }

        @Override
        public BlockPos getManaReceiverPos() {
            return worldPosition;
        }

        // ==================== 颜色与钥匙 ====================

        @Override
        public Optional<DyeColor> getColor() {
            return colorId < 0 ? Optional.empty() : Optional.of(DyeColor.byId(colorId));
        }

        @Override
        public void setColor(Optional<DyeColor> color) {
            int id = color.map(DyeColor::getId).orElse(-1);
            if (id != colorId) {
                colorId = id;
                markDispatchable();
            }
        }

        public int getAvailableSpaceForMana() {
            return (int) Math.min(Integer.MAX_VALUE, getAvailableSpaceForManaLong());
        }

        public long getAvailableSpaceForManaLong() {
            if (isVoidBelow()) return Long.MAX_VALUE;
            return Math.max(0, getMaxManaLong() - mana);
        }

        // ==================== 比较器 ====================

        public static int calculateComparatorLevel(int mana, int max) {
            int val = (int) ((double) mana / (double) max * 15.0);
            if (mana > 0) {
                val = Math.max(val, 1);
            }
            return Math.min(val, 15);
        }

        // ==================== 存盘 ====================

        @NotNull
        @Override
        public String toString() {
            return "Pool{" + worldPosition + ", mana=" + mana + "}";
        }
    }

    /**
     * 大型魔力池方块实体。
     *
     * <p>
     * 每个单元只保存自己的魔力，对外则以整个并池网络为单位：储量、容量与剩余空间都按连通单元汇总，
     * 注入与抽取在本单元不足时按顺序在相邻单元之间搬运，等效于原模组的合并池。
     * </p>
     */
    public static class Large extends Pool {

        private List<Large> cachedNetwork;
        private long cachedTick = -1;

        public Large(BlockPos pos, BlockState state) {
            this(BotaniaBlockEntities.LARGE_MANA_POOL.get(), pos, state);
        }

        public Large(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        /** 本单元相对 3×3 核心的偏移。 */
        @SaveToDisk
        @SyncToClient
        private int coreDx;
        @SaveToDisk
        @SyncToClient
        private int coreDz;

        /** 记录本单元相对 3×3 核心的偏移，并同步给客户端用于并池读数。 */
        public void setCoreOffset(int dx, int dz) {
            this.coreDx = dx;
            this.coreDz = dz;
            setChanged();
            markDispatchable();
        }

        public boolean isCore() {
            return coreDx == 0 && coreDz == 0;
        }

        /** 本单元所属 3×3 的核心位置。 */
        public BlockPos getCorePos() {
            return worldPosition.offset(coreDx, 0, coreDz);
        }

        @Override
        public void onLoad() {
            super.onLoad();
            // 旧存档的单元没有记录核心偏移：若自身不是核心，就近认领一个完整的核心
            if (level != null && !level.isClientSide && coreDx == 0 && coreDz == 0 && !level.getBlockState(worldPosition).getValue(LargeBlock.CORE)) {
                repairCoreOffset();
            }
        }

        private void repairCoreOffset() {
            BlockState selfState = level.getBlockState(worldPosition);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos candidate = worldPosition.offset(dx, 0, dz);
                    if (!level.isLoaded(candidate)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(candidate);
                    if (state.is(selfState.getBlock()) && state.getValue(LargeBlock.CORE)) {
                        setCoreOffset(dx, dz);
                        return;
                    }
                }
            }
        }

        public long unitCapacity() {
            ManaPoolSpec spec = poolSpec();
            return Math.max(1L, spec.capacity() / Math.max(1, spec.cells()));
        }

        long unitStored() {
            return mana;
        }

        long unitRoom() {
            return Math.max(0, unitCapacity() - mana);
        }

        void unitAdd(long amount) {
            if (amount > 0) {
                setMana(mana + amount);
            }
        }

        long unitRemove(long amount) {
            long taken = Math.min(amount, mana);
            if (taken > 0) {
                setMana(mana - taken);
            }
            return taken;
        }

        /** 与本单元连通的并池单元，按世界时间缓存，避免每 tick 重复遍历。 */
        List<Large> network() {
            if (level == null) {
                return List.of(this);
            }
            long now = level.getGameTime();
            if (cachedNetwork == null || cachedTick != now) {
                cachedNetwork = LargeNetwork.collect(level, worldPosition);
                if (cachedNetwork.isEmpty()) {
                    cachedNetwork = List.of(this);
                }
                cachedTick = now;
            }
            return cachedNetwork;
        }

        /** 整个并池网络的储量。 */
        public long getStoredMana() {
            return storageEnabled ? LargeNetwork.stored(network()) : 0;
        }

        /** 整个并池网络的容量。 */
        public long getStoredCapacity() {
            return LargeNetwork.capacity(network());
        }

        /** 并池的单元数量。 */
        public int getUnits() {
            return LargeNetwork.units(network());
        }

        /** 储量占比，用于展示。 */
        public float getFillFraction() {
            long capacity = getStoredCapacity();
            return capacity <= 0 ? 0F : Math.min(1F, (float) getStoredMana() / capacity);
        }

        @Override
        public long getCurrentManaLong() {
            return storageEnabled ? LargeNetwork.stored(network()) : 0;
        }

        @Override
        public int getCurrentMana() {
            return (int) Math.min(Integer.MAX_VALUE, getCurrentManaLong());
        }

        @Override
        public long getMaxManaLong() {
            return LargeNetwork.capacity(network());
        }

        @Override
        public int getMaxMana() {
            return (int) Math.min(Integer.MAX_VALUE, getMaxManaLong());
        }

        @Override
        public boolean isFull() {
            return !storageEnabled || LargeNetwork.room(network()) <= 0;
        }

        @Override
        public long getAvailableSpaceForManaLong() {
            return storageEnabled ? LargeNetwork.room(network()) : 0;
        }

        @Override
        public int getAvailableSpaceForMana() {
            return (int) Math.min(Integer.MAX_VALUE, getAvailableSpaceForManaLong());
        }

        @Override
        public void receiveMana(int mana) {
            receiveManaLong(mana);
        }

        @Override
        public void receiveManaLong(long delta) {
            if (!storageEnabled || delta == 0) {
                return;
            }
            List<Large> units = network();
            if (delta > 0) {
                long remaining = Math.min(delta, LargeNetwork.room(units));
                for (int i = 0; i < units.size() && remaining > 0; i++) {
                    Large unit = units.get(i);
                    long add = Math.min(remaining, unit.unitRoom());
                    unit.unitAdd(add);
                    remaining -= add;
                }
            } else {
                long remaining = -delta;
                for (int i = 0; i < units.size() && remaining > 0; i++) {
                    remaining -= units.get(i).unitRemove(remaining);
                }
            }
        }

        @Override
        public long addMana(long amount, boolean simulate) {
            if (!storageEnabled || amount <= 0) {
                return 0;
            }
            long change = Math.min(amount, getAvailableSpaceForManaLong());
            if (change > 0 && !simulate) {
                receiveManaLong(change);
            }
            return change;
        }

        @Override
        public long removeMana(long amount, boolean simulate) {
            if (!storageEnabled || amount <= 0) {
                return 0;
            }
            long change = Math.min(amount, getCurrentManaLong());
            if (change > 0 && !simulate) {
                receiveManaLong(-change);
            }
            return change;
        }
    }

    /**
     * 相邻大型魔力池组成的并池网络。
     *
     * <p>
     * 按单元记录的核心位置收集同一 3×3 的单元，容量与储量按单元汇总；
     * 注入与抽取先在单元自己身上进行，不够时再在单元之间搬运。
     * 单个单元容量为整组规格除以单元数，合并后总容量与原模组一致。
     * </p>
     */
    static final class LargeNetwork {

        private LargeNetwork() {}

        /** 收集与 {@code start} 属于同一 3×3 的所有大型池单元。 */
        public static List<Large> collect(Level level, BlockPos start) {
            if (!(level.getBlockEntity(start) instanceof Large unit)) {
                return List.of();
            }
            BlockPos core = unit.getCorePos();
            List<Large> units = new ArrayList<>(9);
            for (BlockPos pos : BlockPos.betweenClosed(core.offset(-1, 0, -1), core.offset(1, 0, 1))) {
                if (level.getBlockEntity(pos) instanceof Large part && core.equals(part.getCorePos())) {
                    units.add(part);
                }
            }
            if (units.isEmpty()) {
                units.add(unit);
            }
            return units;
        }

        /** 单元数量。 */
        public static int units(List<Large> units) {
            return units.size();
        }

        /** 网络总容量。 */
        public static long capacity(List<Large> units) {
            long total = 0;
            for (int i = 0; i < units.size(); i++) {
                total += units.get(i).unitCapacity();
            }
            return total;
        }

        /** 网络已存魔力。 */
        public static long stored(List<Large> units) {
            long total = 0;
            for (int i = 0; i < units.size(); i++) {
                total += units.get(i).unitStored();
            }
            return total;
        }

        /** 网络剩余空间。 */
        public static long room(List<Large> units) {
            long total = 0;
            for (int i = 0; i < units.size(); i++) {
                Large unit = units.get(i);
                total += unit.unitRoom();
            }
            return total;
        }
    }
}
