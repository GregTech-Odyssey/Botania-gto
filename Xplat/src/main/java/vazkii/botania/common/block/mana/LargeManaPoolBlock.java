/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.mana;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import vazkii.botania.common.block.BotaniaBlock;
import vazkii.botania.common.block.block_entity.mana.LargeManaPoolBlockEntity;
import vazkii.botania.common.block.decor.BotaniaMushroomBlock;
import vazkii.botania.common.entity.ManaBurstEntity;
import vazkii.botania.common.item.material.MysticalPetalItem;

import java.util.Optional;
import java.util.function.BiFunction;

public class LargeManaPoolBlock extends BotaniaBlock implements EntityBlock {
	public static final BooleanProperty CORE = BooleanProperty.create("core");
	public static final BooleanProperty NORTH = BooleanProperty.create("north");
	public static final BooleanProperty EAST = BooleanProperty.create("east");
	public static final BooleanProperty SOUTH = BooleanProperty.create("south");
	public static final BooleanProperty WEST = BooleanProperty.create("west");
	public static final BooleanProperty NORTH_EAST = BooleanProperty.create("north_east");
	public static final BooleanProperty SOUTH_EAST = BooleanProperty.create("south_east");
	public static final BooleanProperty SOUTH_WEST = BooleanProperty.create("south_west");
	public static final BooleanProperty NORTH_WEST = BooleanProperty.create("north_west");

	public static final int FLOOR = 4;
	public static final int RIM = 4;

	private static final VoxelShape[] SHAPES = new VoxelShape[256];

	static {
		for (int i = 0; i < SHAPES.length; i++) {
			boolean n = (i & 1) != 0, e = (i & 2) != 0, s = (i & 4) != 0, w = (i & 8) != 0;
			boolean ne = (i & 16) != 0, se = (i & 32) != 0, sw = (i & 64) != 0, nw = (i & 128) != 0;
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

	private final BiFunction<BlockPos, BlockState, ? extends LargeManaPoolBlockEntity> factory;

	public LargeManaPoolBlock(Properties builder) {
		this(builder, LargeManaPoolBlockEntity::new);
	}

	public LargeManaPoolBlock(Properties builder, BiFunction<BlockPos, BlockState, ? extends LargeManaPoolBlockEntity> factory) {
		super(builder);
		this.factory = factory;
		registerDefaultState(defaultBlockState().setValue(CORE, false)
				.setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
				.setValue(NORTH_EAST, false).setValue(SOUTH_EAST, false).setValue(SOUTH_WEST, false).setValue(NORTH_WEST, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CORE, NORTH, EAST, SOUTH, WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST);
	}

	private static int shapeIndex(BlockState state) {
		return (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0)
				| (state.getValue(WEST) ? 8 : 0) | (state.getValue(NORTH_EAST) ? 16 : 0) | (state.getValue(SOUTH_EAST) ? 32 : 0)
				| (state.getValue(SOUTH_WEST) ? 64 : 0) | (state.getValue(NORTH_WEST) ? 128 : 0);
	}

	public BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
		BlockPos n = pos.north(), e = pos.east(), s = pos.south(), w = pos.west();
		BlockPos ne = n.east(), se = s.east(), sw = s.west(), nw = n.west();
		if (!loaded(level, n) || !loaded(level, e) || !loaded(level, s) || !loaded(level, w)
				|| !loaded(level, ne) || !loaded(level, se) || !loaded(level, sw) || !loaded(level, nw)) {
			return state;
		}
		BlockPos self = coreOf(level, pos);
		BlockPos cn = coreOf(level, n), ce = coreOf(level, e), cs = coreOf(level, s), cw = coreOf(level, w);
		BlockPos cne = coreOf(level, ne), cse = coreOf(level, se), csw = coreOf(level, sw), cnw = coreOf(level, nw);
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
		return level.getBlockEntity(pos) instanceof LargeManaPoolBlockEntity part && part.getBlockState().is(this) ? part.getCorePos() : null;
	}

	public static boolean linked(@Nullable BlockPos coreA, @Nullable BlockPos coreB) {
		if (coreA == null || coreB == null || coreA.getY() != coreB.getY()) {
			return false;
		}
		int dx = Math.abs(coreB.getX() - coreA.getX()), dz = Math.abs(coreB.getZ() - coreA.getZ());
		return dx == 0 && dz == 0 || dx == 3 && dz == 0 || dx == 0 && dz == 3;
	}

	public void refreshConnections(Level level, BlockPos center, int radius) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, 0, radius))) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(this)) {
				BlockState updated = withConnections(level, pos, state);
				if (updated != state) {
					level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	@NotNull
	@Override
	public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighborState,
			@NotNull LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
		return direction.getAxis().isHorizontal() ? withConnections(level, pos, state) : state;
	}

	@NotNull
	@Override
	public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
		return SHAPES[shapeIndex(state)];
	}

	@NotNull
	@Override
	public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
		if (context instanceof EntityCollisionContext econtext && econtext.getEntity() instanceof ManaBurstEntity) {
			return Shapes.block();
		}
		return SHAPES[shapeIndex(state)];
	}

	@NotNull
	@Override
	public VoxelShape getInteractionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
		return Shapes.block();
	}

	@NotNull
	@Override
	public InteractionResult use(@NotNull BlockState state, Level level, @NotNull BlockPos pos, Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		Optional<DyeColor> itemColor = Optional.empty();
		if (stack.getItem() instanceof MysticalPetalItem petalItem) {
			itemColor = Optional.of(petalItem.color);
		}
		if (Block.byItem(stack.getItem()) instanceof BotaniaMushroomBlock mushroomBlock) {
			itemColor = Optional.of(mushroomBlock.color);
		}
		if (!(level.getBlockEntity(pos) instanceof LargeManaPoolBlockEntity pool)) {
			return InteractionResult.PASS;
		}
		if (itemColor.isPresent() && !itemColor.equals(pool.getColor())
				|| stack.is(Items.CLAY_BALL) && pool.getColor().isPresent()) {
			if (!level.isClientSide) {
				pool.setColor(itemColor);
			}
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return InteractionResult.PASS;
	}

	@Override
	public void playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
		if (!level.isClientSide && !state.getValue(CORE) && level.getBlockEntity(pos) instanceof LargeManaPoolBlockEntity part) {
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

	@Override
	public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moved) {
		if (!newState.is(this) && !level.isClientSide && level.getBlockEntity(pos) instanceof LargeManaPoolBlockEntity part) {
			BlockPos core = part.getCorePos();
			if (state.getValue(CORE)) {
				for (BlockPos other : BlockPos.betweenClosed(core.offset(-1, 0, -1), core.offset(1, 0, 1))) {
					if (!other.equals(pos) && level.isLoaded(other) && level.getBlockEntity(other) instanceof LargeManaPoolBlockEntity otherPart
							&& otherPart.getCorePos().equals(core)) {
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

	@Override
	public void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Entity entity) {
		if (entity instanceof ItemEntity item && level.getBlockEntity(pos) instanceof LargeManaPoolBlockEntity pool) {
			pool.collideEntityItem(item);
		}
	}

	@Override
	public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
		return level.getBlockEntity(pos) instanceof LargeManaPoolBlockEntity pool ? pool.getComparatorLevel() : 0;
	}

	@NotNull
	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
		return factory.apply(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
		if (!state.getValue(CORE) || !type.isValid(state)) {
			return null;
		}
		return level.isClientSide
				? (l, p, s, be) -> LargeManaPoolBlockEntity.clientTick(l, p, s, (LargeManaPoolBlockEntity) be)
				: (l, p, s, be) -> LargeManaPoolBlockEntity.serverTick(l, p, s, (LargeManaPoolBlockEntity) be);
	}
}
