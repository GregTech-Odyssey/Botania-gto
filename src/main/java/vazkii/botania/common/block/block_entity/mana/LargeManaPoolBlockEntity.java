/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.block_entity.mana;

import com.gto.datasynclib.annotations.SyncToClient;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.BotaniaAPIClient;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.api.block.Wandable;
import vazkii.botania.api.mana.ManaBlockType;
import vazkii.botania.api.mana.ManaNetworkAction;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkAttachable;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.client.gui.HUDHandler;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntities;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntity;
import vazkii.botania.common.block.mana.LargeManaPoolBlock;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.common.handler.ManaNetworkHandler;
import vazkii.botania.common.helper.EntityHelper;
import vazkii.botania.common.item.BlackLotusItem;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.common.item.ManaTabletItem;
import vazkii.botania.xplat.BotaniaConfig;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.Optional;

public class LargeManaPoolBlockEntity extends BotaniaBlockEntity implements ManaPool, SparkAttachable, ThrottledPacket, Wandable, ManaPoolSpec.Holder {
	private static final int CRAFT_EFFECT_EVENT = 0;
	private static final String TAG_CORE_X = "coreX";
	private static final String TAG_CORE_Z = "coreZ";
	private static final String TAG_MANA = "mana";
	private static final String TAG_OUTPUTTING = "outputting";
	private static final String TAG_COLOR = "color";

	@SyncToClient
	private int coreDx;
	@SyncToClient
	private int coreDz;
	@SyncToClient
	private long leaderPos;
	@SyncToClient
	private long shownMana;
	@SyncToClient
	private long shownCapacity;
	@SyncToClient
	private int shownUnits = 1;
	@SyncToClient
	private boolean shownOutputting;
	@SyncToClient
	private int shownColor = -1;

	private final ManaPoolSpec spec;
	long mana;
	boolean outputting;
	int color = -1;
	@Nullable
	LargeManaPoolNetwork network;
	int networkIndex;
	private int ticks;
	private int renderedColor = -2;
	@Nullable
	private LargeManaPoolBlockEntity coreCache;
	@Nullable
	private LargeManaPoolBlockEntity leaderCache;

	public LargeManaPoolBlockEntity(BlockPos pos, BlockState state) {
		this(BotaniaBlockEntities.LARGE_POOL, pos, state);
	}

	protected LargeManaPoolBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		spec = ManaPoolSpec.of(state.getBlock(), ManaPoolSpec.LARGE);
		leaderPos = pos.asLong();
		shownCapacity = spec.capacity();
	}

	@Override
	public ManaPoolSpec poolSpec() {
		return spec;
	}

	public static boolean isSamePool(@Nullable ManaReceiver a, @Nullable ManaReceiver b) {
		if (a == b) {
			return true;
		}
		if (a instanceof LargeManaPoolBlockEntity la && b instanceof LargeManaPoolBlockEntity lb) {
			var net = la.network();
			return net != null && net == lb.network();
		}
		return false;
	}

	public void setCoreOffset(int dx, int dz) {
		coreDx = dx;
		coreDz = dz;
		coreCache = null;
		setChanged();
	}

	public boolean isCore() {
		return coreDx == 0 && coreDz == 0;
	}

	private AABB unitRenderBox;
	private AABB cellRenderBox;

	@Override
	public AABB getRenderBoundingBox() {
		if (!isCore()) {
			if (cellRenderBox == null) {
				cellRenderBox = new AABB(worldPosition);
			}
			return cellRenderBox;
		}
		if (unitRenderBox == null) {
			unitRenderBox = new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
					worldPosition.getX() + 2, worldPosition.getY() + 1, worldPosition.getZ() + 2);
		}
		return unitRenderBox;
	}

	public BlockPos getCorePos() {
		return worldPosition.offset(coreDx, 0, coreDz);
	}

	@Nullable
	public LargeManaPoolBlockEntity getCore() {
		if (isCore()) {
			return this;
		}
		BlockPos corePos = getCorePos();
		var cached = coreCache;
		if (cached != null && !cached.isRemoved() && cached.level == level && cached.worldPosition.equals(corePos)) {
			return cached;
		}
		cached = level != null && level.isLoaded(corePos) && level.getBlockEntity(corePos) instanceof LargeManaPoolBlockEntity core && core.isCore() ? core : null;
		coreCache = cached;
		return cached;
	}

	@Nullable
	public LargeManaPoolNetwork network() {
		if (level == null || level.isClientSide) {
			return null;
		}
		var core = getCore();
		if (core == null || core.isRemoved()) {
			return null;
		}
		var net = core.network;
		if (net == null || net.isDissolved()) {
			net = LargeManaPoolNetwork.form(core);
		}
		return net;
	}

	private LargeManaPoolBlockEntity view() {
		var core = getCore();
		if (core == null) {
			return this;
		}
		long packed = core.leaderPos;
		if (packed == core.worldPosition.asLong()) {
			return core;
		}
		var cached = core.leaderCache;
		if (cached != null && !cached.isRemoved() && cached.worldPosition.asLong() == packed) {
			return cached;
		}
		BlockPos leaderPos = BlockPos.of(packed);
		if (level.isLoaded(leaderPos) && level.getBlockEntity(leaderPos) instanceof LargeManaPoolBlockEntity leader && leader.isCore()) {
			core.leaderCache = leader;
			return leader;
		}
		return core;
	}

	void showGroup(LargeManaPoolNetwork net, boolean leaderChanged) {
		long newLeader = net.leader().worldPosition.asLong();
		boolean changed = leaderChanged || newLeader != leaderPos
				|| shownMana != net.getMana() || shownCapacity != net.getCapacity() || shownUnits != net.getUnits()
				|| shownOutputting != net.isOutputting() || shownColor != net.getColorId();
		leaderPos = newLeader;
		shownMana = net.getMana();
		shownCapacity = net.getCapacity();
		shownUnits = net.getUnits();
		shownOutputting = net.isOutputting();
		shownColor = net.getColorId();
		if (changed) {
			syncToClients();
		}
	}

	public long getStoredMana() {
		var net = network();
		return net != null ? net.getMana() : level != null && level.isClientSide ? view().shownMana : 0;
	}

	public long getStoredCapacity() {
		var net = network();
		return net != null ? net.getCapacity() : level != null && level.isClientSide ? view().shownCapacity : 0;
	}

	public int getUnits() {
		var net = network();
		return net != null ? net.getUnits() : view().shownUnits;
	}

	public float getFillFraction() {
		long cap = getStoredCapacity();
		return cap <= 0 ? 0 : (float) Math.min(1D, (double) getStoredMana() / cap);
	}

	public int getComparatorLevel() {
		long cap = getStoredCapacity();
		long stored = getStoredMana();
		if (cap <= 0) {
			return 0;
		}
		int val = (int) ((double) stored / cap * 15.0);
		if (stored > 0) {
			val = Math.max(val, 1);
		}
		return Math.min(val, 15);
	}

	@Override
	public Level getManaReceiverLevel() {
		return level;
	}

	@Override
	public BlockPos getManaReceiverPos() {
		return worldPosition;
	}

	@Override
	public int getCurrentMana() {
		return (int) Math.min(Integer.MAX_VALUE, getStoredMana());
	}

	@Override
	public int getMaxMana() {
		return (int) Math.min(Integer.MAX_VALUE, getStoredCapacity());
	}

	@Override
	public long getCurrentManaLong() {
		return getStoredMana();
	}

	@Override
	public long getMaxManaLong() {
		return getStoredCapacity();
	}

	@Override
	public void receiveManaLong(long amount) {
		var net = network();
		if (net != null) {
			net.receive(amount);
		}
	}

	@Override
	public long getAvailableSpaceForManaLong() {
		var net = network();
		return net == null ? 0 : Math.max(0, net.getCapacity() - net.getMana());
	}

	@Override
	public boolean isFull() {
		var net = network();
		if (net != null) {
			return net.getMana() >= net.getCapacity();
		}
		if (level != null && level.isClientSide) {
			var view = view();
			return view.shownMana >= view.shownCapacity;
		}
		return true;
	}

	@Override
	public void receiveMana(int amount) {
		var net = network();
		if (net != null) {
			net.receive(amount);
		}
	}

	@Override
	public boolean canReceiveManaFromBursts() {
		return true;
	}

	@Override
	public boolean isOutputtingPower() {
		var net = network();
		return net != null ? net.isOutputting() : view().shownOutputting;
	}

	@Override
	public Optional<DyeColor> getColor() {
		var net = network();
		int id = net != null ? net.getColorId() : view().shownColor;
		return id < 0 ? Optional.empty() : Optional.of(DyeColor.byId(id));
	}

	@Override
	public void setColor(Optional<DyeColor> color) {
		var net = network();
		if (net != null) {
			net.setColorId(color.map(DyeColor::getId).orElse(-1));
		}
	}

	@Override
	public int getAvailableSpaceForMana() {
		var net = network();
		return net == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0, net.getCapacity() - net.getMana()));
	}

	@Override
	public boolean canAttachSpark(ItemStack stack) {
		return true;
	}

	@Override
	public void attachSpark(ManaSpark entity) {
		var net = network();
		if (net != null) {
			net.preferSpark(entity);
		}
	}

	@Override
	public ManaSpark getAttachedSpark() {
		var net = network();
		return net == null ? null : net.getSpark();
	}

	public void refreshSparks() {
		var net = network();
		if (net != null) {
			net.resolveSparksNow();
		}
	}

	@Override
	public boolean areIncomingTranfersDone() {
		return false;
	}

	@Override
	public void markDispatchable() {
		var net = network();
		if (net != null) {
			net.markChanged();
		}
	}

	@Override
	public boolean onUsedByWand(@Nullable Player player, ItemStack stack, Direction side) {
		if (player == null || player.isShiftKeyDown()) {
			var net = network();
			if (net != null) {
				net.setOutputting(!net.isOutputting());
			}
		}
		return true;
	}

	public void collideEntityItem(ItemEntity item) {
		if (!item.isAlive() || item.getItem().isEmpty()) {
			return;
		}
		if (level.isClientSide) {
			if (item.getItem().getItem() instanceof BlackLotusItem lotus) {
				lotus.onDissolveTick(this, item);
			}
			return;
		}
		if (network() == null
				|| ManaInfusion.process(level, this, item, level.getBlockState(getCorePos().below()), spec.infusionRate(), getCorePos()) <= 0) {
			return;
		}
		if (network().claimCraftSound(level.getGameTime())) {
			level.playSound(null, worldPosition, BotaniaSounds.manaPoolCraft, SoundSource.BLOCKS, 1F, 1F);
		}
		craftingEffect();
	}

	public void craftingEffect() {
		level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, worldPosition);
		level.blockEvent(worldPosition, getBlockState().getBlock(), CRAFT_EFFECT_EVENT, 0);
	}

	@Override
	public boolean triggerEvent(int event, int param) {
		if (event == CRAFT_EFFECT_EVENT) {
			if (level.isClientSide) {
				for (int i = 0; i < 25; i++) {
					SparkleParticleData data = SparkleParticleData.sparkle((float) Math.random(), (float) Math.random(), (float) Math.random(), (float) Math.random(), 10);
					level.addParticle(data, worldPosition.getX() + 0.5 + Math.random() * 0.4 - 0.2, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5 + Math.random() * 0.4 - 0.2, 0, 0, 0);
				}
			}
			return true;
		}
		return super.triggerEvent(event, param);
	}

	private AABB unitArea() {
		return new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
				worldPosition.getX() + 2, worldPosition.getY() + 1, worldPosition.getZ() + 2);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, LargeManaPoolBlockEntity self) {
		var net = self.network();
		if (net == null) {
			return;
		}
		if (!ManaNetworkHandler.instance.isPoolIn(level, self)) {
			BotaniaAPI.instance().getManaNetworkInstance().fireManaNetworkEvent(self, ManaBlockType.POOL, ManaNetworkAction.ADD);
		}
		net.tick();
		self.chargeItems(net);
		self.ticks++;
	}

	private boolean ownsItem(ItemEntity item) {
		int dx = Mth.floor(item.getX()) - worldPosition.getX();
		int dz = Mth.floor(item.getZ()) - worldPosition.getZ();
		return dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1;
	}

	private void chargeItems(LargeManaPoolNetwork net) {
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, unitArea())) {
			if (!ownsItem(item)) {
				continue;
			}
			collideEntityItem(item);
			if (!item.isAlive()) {
				continue;
			}
			ItemStack stack = item.getItem();
			var manaItem = XplatAbstractions.INSTANCE.findManaItem(stack);
			if (stack.isEmpty() || manaItem == null) {
				continue;
			}
			boolean outputting = net.isOutputting();
			if (outputting ? !manaItem.canReceiveManaFromPool(this) : !manaItem.canExportManaToPool(this)) {
				continue;
			}
			int moved;
			if (outputting) {
				moved = (int) Math.min(spec.itemTransferRate(), Math.min(net.getMana(), manaItem.getMaxMana() - manaItem.getMana()));
				if (moved > 0) {
					manaItem.addMana(moved);
					net.receive(-moved);
				}
			} else {
				moved = (int) Math.min(spec.itemTransferRate(), Math.min(net.getCapacity() - net.getMana(), manaItem.getMana()));
				if (moved > 0) {
					manaItem.addMana(-moved);
					net.receive(moved);
				}
			}
			if (moved > 0 && ticks % 10 == 0) {
				EntityHelper.syncItem(item);
			}
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, LargeManaPoolBlockEntity self) {
		var view = self.view();
		float fill = view.shownCapacity <= 0 ? 0 : (float) Math.min(1D, (double) view.shownMana / view.shownCapacity);
		if (view.shownColor != self.renderedColor) {
			self.renderedColor = view.shownColor;
			for (BlockPos part : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
				level.sendBlockUpdated(part, level.getBlockState(part), level.getBlockState(part), Block.UPDATE_CLIENTS);
			}
		}
		double surface = surfaceHeight(fill);
		if (Math.random() < fill * 0.3) {
			WispParticleData data = WispParticleData.wisp((float) Math.random() / 3F,
					ManaPoolBlockEntity.PARTICLE_COLOR_RED, ManaPoolBlockEntity.PARTICLE_COLOR_GREEN, ManaPoolBlockEntity.PARTICLE_COLOR_BLUE, 2F);
			level.addParticle(data, pos.getX() - 0.6 + Math.random() * 2.2, pos.getY() + surface + Math.random() * 0.15,
					pos.getZ() - 0.6 + Math.random() * 2.2, 0, (float) Math.random() / 25F, 0);
		}
		if (BotaniaConfig.common().chargingAnimationEnabled() && ++self.ticks % 4 == 0) {
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, self.unitArea())) {
				ItemStack stack = item.getItem();
				var manaItem = item.isAlive() && !stack.isEmpty() ? XplatAbstractions.INSTANCE.findManaItem(stack) : null;
				if (manaItem == null) {
					continue;
				}
				boolean active = view.shownOutputting
						? manaItem.canReceiveManaFromPool(self) && view.shownMana > 0 && manaItem.getMana() < manaItem.getMaxMana()
						: manaItem.canExportManaToPool(self) && manaItem.getMana() > 0 && view.shownMana < view.shownCapacity;
				if (!active) {
					continue;
				}
				Vec3 itemRel = item.position().subtract(pos.getX(), pos.getY(), pos.getZ()).add(0, 0.3, 0);
				Vec3 poolRel = new Vec3(itemRel.x + Math.random() * 0.6 - 0.3, surface, itemRel.z + Math.random() * 0.6 - 0.3);
				if (view.shownOutputting) {
					ManaPoolBlockEntity.addManaFlowParticle(level, pos, poolRel, itemRel);
				} else {
					ManaPoolBlockEntity.addManaFlowParticle(level, pos, itemRel, poolRel);
				}
			}
		}
	}

	public static double surfaceHeight(float fill) {
		double bottom = LargeManaPoolBlock.FLOOR / 16D + 0.001;
		double top = 15 / 16D;
		return bottom + (top - bottom) * fill;
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (isCore() && level != null && !level.isClientSide) {
			if (network != null) {
				network.dissolve();
			}
			BotaniaAPI.instance().getManaNetworkInstance().fireManaNetworkEvent(this, ManaBlockType.POOL, ManaNetworkAction.REMOVE);
		}
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide) {
			LargeManaPoolNetwork.onPartLoaded(this);
		}
	}

	@Override
	public void writePacketNBT(CompoundTag tag) {
		tag.putInt(TAG_CORE_X, coreDx);
		tag.putInt(TAG_CORE_Z, coreDz);
		if (isCore()) {
			var net = network != null && !network.isDissolved() ? network : null;
			tag.putLong(TAG_MANA, net != null ? net.shareOf(this) : mana);
			tag.putBoolean(TAG_OUTPUTTING, net != null ? net.isOutputting() : outputting);
			tag.putInt(TAG_COLOR, net != null ? net.getColorId() : color);
		}
	}

	@Override
	public void readPacketNBT(CompoundTag tag) {
		coreDx = tag.getInt(TAG_CORE_X);
		coreDz = tag.getInt(TAG_CORE_Z);
		mana = Math.max(0, Math.min(spec.capacity(), tag.getLong(TAG_MANA)));
		outputting = tag.getBoolean(TAG_OUTPUTTING);
		color = tag.contains(TAG_COLOR) ? tag.getInt(TAG_COLOR) : -1;
		shownMana = mana;
		shownOutputting = outputting;
		shownColor = color;
	}

	@Override
	protected boolean syncsByFields() {
		return true;
	}

	public static class WandHud implements WandHUD {
		private final LargeManaPoolBlockEntity pool;

		public WandHud(LargeManaPoolBlockEntity pool) {
			this.pool = pool;
		}

		@Override
		public void renderHUD(GuiGraphics gui, Minecraft mc) {
			ItemStack poolStack = new ItemStack(pool.getBlockState().getBlock());
			int units = pool.getUnits();
			String name = poolStack.getHoverName().getString() + (units > 1 ? " ×" + units : "");

			int centerX = mc.getWindow().getGuiScaledWidth() / 2;
			int centerY = mc.getWindow().getGuiScaledHeight() / 2;
			int width = Math.max(102, mc.font.width(name)) + 4;

			RenderHelper.renderHUDBox(gui, centerX - width / 2, centerY + 8, centerX + width / 2, centerY + 48);
			BotaniaAPIClient.instance().drawSimpleManaHUD(gui, 0x0095FF, pool.getCurrentManaLong(), pool.getMaxManaLong(), name);

			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			RenderHelper.drawTexturedModalRect(gui, HUDHandler.manaBar, centerX - 11, centerY + 30, pool.isOutputtingPower() ? 22 : 0, 38, 22, 15);
			RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

			ItemStack tablet = new ItemStack(BotaniaItems.manaTablet);
			ManaTabletItem.setStackCreative(tablet);
			gui.renderItem(tablet, centerX - 31, centerY + 30);
			gui.renderItem(poolStack, centerX + 15, centerY + 30);
			RenderSystem.disableBlend();
		}
	}
}
