/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.entity;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkAttachable;
import vazkii.botania.api.mana.spark.SparkHelper;
import vazkii.botania.api.mana.spark.SparkUpgradeType;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.client.fx.SparkFlowEffects;
import vazkii.botania.common.helper.ColorHelper;
import vazkii.botania.common.helper.PlayerHelper;
import vazkii.botania.common.helper.VecHelper;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.common.item.SparkAugmentItem;
import vazkii.botania.common.item.WandOfTheForestItem;
import vazkii.botania.network.EffectType;
import vazkii.botania.network.clientbound.BotaniaEffectPacket;
import vazkii.botania.network.clientbound.SparkFlowPacket;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.*;

public class ManaSparkEntity extends SparkBaseEntity implements ManaSpark {
	private static final int TRANSFER_INTERVAL = 20;
	private static final int TRANSFER_RATE = 1000 * TRANSFER_INTERVAL;
	private static final String TAG_UPGRADE = "upgrade";
	private static final EntityDataAccessor<Integer> UPGRADE = SynchedEntityData.defineId(ManaSparkEntity.class, EntityDataSerializers.INT);
	private static final int[] NO_FLOWS = new int[0];
	private static final IntArrayList FLOW_OUT = new IntArrayList();
	private static final IntArrayList FLOW_IN = new IntArrayList();
	private static final Comparator<ManaSpark> BY_CURRENT_MANA = Comparator.comparingInt(ManaSparkEntity::currentManaOf);

	private final Set<ManaSpark> outgoingTransfers = new ReferenceLinkedOpenHashSet<>();

	private final ArrayList<ManaSpark> inboundTransfers = new ArrayList<>();

	private boolean shouldFilterTransfers = true;
	private boolean receiverWasFull = true;
	private boolean firstTick = true;
	private SparkUpgradeType upgrade = SparkUpgradeType.NONE;

	long indexKey;
	int indexColor;
	boolean indexed;

	private BlockEntity attachedBe;
	private long attachedTick = Long.MIN_VALUE;
	private SparkAttachable attachedTile;
	private ManaReceiver attachedReceiver;
	private ItemStack sparkStack;
	private int[] sentOutgoing = NO_FLOWS;
	private int[] sentIncoming = NO_FLOWS;

	public ManaSparkEntity(EntityType<ManaSparkEntity> type, Level world) {
		super(type, world);
	}

	public ManaSparkEntity(Level world) {
		this(BotaniaEntities.SPARK, world);
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		entityData.define(UPGRADE, 0);
	}

	@NotNull
	@Override
	public ItemStack getPickResult() {
		return new ItemStack(getSparkItem());
	}

	@Override
	public void setLevelCallback(EntityInLevelCallback callback) {
		super.setLevelCallback(callback);
		if (callback == EntityInLevelCallback.NULL) {
			ManaSparkIndex.untrack(this);
		} else {
			ManaSparkIndex.track(this);
		}
	}

	@Override
	public void tick() {
		if (level().isClientSide) {
			return;
		}

		// When loaded, initialize transfers
		if (firstTick) {
			updateTransfers();
		} else if (level().getGameTime() % TRANSFER_INTERVAL != 8) {
			return;
		}
		ManaSparkIndex.refresh(this);

		SparkAttachable tile = getAttachedTile();
		var receiver = getAttachedManaReceiver();
		if (tile == null || receiver == null) {
			dropAndKill();
			return;
		}

		FLOW_OUT.clear();
		FLOW_IN.clear();
		SparkUpgradeType upgrade = getUpgrade();
		Collection<ManaSpark> outgoingTransfers = getOutgoingTransfers();

		switch (upgrade) {
			case DISPERSIVE -> {
				if (receiver.getCurrentMana() > 0) {
					chargePlayers(receiver);
				}

				if (receiverWasFull && !receiver.isFull()) {
					notifyOthers(getNetwork());
				}
			}
			case DOMINANT -> {
				if (!receiver.isFull()) {
					updateTransfers();
				}
			}
			// Recessive does not need to be handled because recessive sparks get notified in all relevant cases
			default -> {
				if (receiverWasFull && !receiver.isFull()) {
					notifyOthers(getNetwork());
				}
			}
		}

		checkReceiverFull();

		if (!outgoingTransfers.isEmpty()) {
			int manaTotal = Math.min(TRANSFER_RATE * outgoingTransfers.size(), receiver.getCurrentMana());
			int count = outgoingTransfers.size();
			int manaSpent = 0;

			if (manaTotal > 0) {
				if (shouldFilterTransfers) {
					filterTransfers();
					shouldFilterTransfers = false;
				}

				for (ManaSpark spark : outgoingTransfers) {
					count--;
					SparkAttachable attached = spark.entity().isRemoved() ? null : spark.getAttachedTile();
					var attachedReceiver = attached == null ? null : spark.getAttachedManaReceiver();
					if (attached == null || attachedReceiver == null || attachedReceiver.isFull() || spark.areIncomingTransfersDone()) {
						shouldFilterTransfers = true;
						continue;
					}

					int spend = Math.min(attached.getAvailableSpaceForMana(), (manaTotal - manaSpent) / (count + 1));
					attachedReceiver.receiveMana(spend);
					manaSpent += spend;
					spark.checkReceiverFull();

					if (spend > 0) {
						FLOW_OUT.add(spark.entity().getId());
					}
				}
				receiver.receiveMana(-manaSpent);
			}
		}
		if (!inboundTransfers.isEmpty()) {
			int manaNeeded = Math.min(TRANSFER_RATE * inboundTransfers.size(), tile.getAvailableSpaceForMana());
			int count = inboundTransfers.size();
			int manaRecieved = 0;

			if (manaNeeded > 0) {
				if (shouldFilterTransfers) {
					filterTransfers();
					shouldFilterTransfers = false;
				}

				inboundTransfers.sort(BY_CURRENT_MANA);
				for (ManaSpark spark : inboundTransfers) {
					count--;
					SparkAttachable attached = spark.entity().isRemoved() ? null : spark.getAttachedTile();
					var attachedReceiver = attached == null ? null : spark.getAttachedManaReceiver();
					if (attached == null || attachedReceiver == null) {
						shouldFilterTransfers = true;
						continue;
					}

					int gained = Math.min(attachedReceiver.getCurrentMana(), (manaNeeded - manaRecieved) / (count + 1));
					attachedReceiver.receiveMana(-gained);
					manaRecieved += gained;

					if (gained > 0) {
						FLOW_IN.add(spark.entity().getId());
					}
				}
				receiver.receiveMana(manaRecieved);
			}
		}

		syncFlows();
		firstTick = false;
	}

	private static int currentManaOf(ManaSpark spark) {
		if (spark.entity().isRemoved()) {
			return 0;
		}
		var receiver = spark.getAttachedManaReceiver();
		return receiver == null ? 0 : receiver.getCurrentMana();
	}

	private void chargePlayers(ManaReceiver receiver) {
		AABB aabb = VecHelper.boxForRange(
				this.position().with(Direction.Axis.Y, getY() + (getBbHeight() / 2.0)),
				SparkHelper.SPARK_SCAN_RANGE);
		ItemStack input = sparkStack();
		Player target = null;
		ManaItem targetItem = null;
		int candidates = 0;
		for (Player player : level().players()) {
			if (player.isAlive() && player.getBoundingBox().intersects(aabb)) {
				ManaItem item = findChargeable(player, input);
				if (item != null && level().random.nextInt(++candidates) == 0) {
					target = player;
					targetItem = item;
				}
			}
		}

		if (target != null) {
			int manaToPut = Math.min(receiver.getCurrentMana(), Math.min(TRANSFER_RATE, targetItem.getMaxMana() - targetItem.getMana()));
			targetItem.addMana(manaToPut);
			receiver.receiveMana(-manaToPut);
			FLOW_OUT.add(target.getId());
		}
	}

	@Nullable
	private static ManaItem findChargeable(Player player, ItemStack input) {
		var inventory = player.getInventory();
		ManaItem item = findChargeable(inventory.items, input);
		if (item == null) {
			item = findChargeable(inventory.armor, input);
		}
		if (item == null) {
			Container inv = BotaniaAPI.instance().getAccessoriesInventory(player);
			for (int i = 0; i < inv.getContainerSize() && item == null; i++) {
				item = chargeable(inv.getItem(i), input);
			}
		}
		return item;
	}

	@Nullable
	private static ManaItem findChargeable(List<ItemStack> stacks, ItemStack input) {
		for (int i = 0, n = stacks.size(); i < n; i++) {
			ManaItem item = chargeable(stacks.get(i), input);
			if (item != null) {
				return item;
			}
		}
		return null;
	}

	@Nullable
	private static ManaItem chargeable(ItemStack stack, ItemStack input) {
		if (stack.isEmpty()) {
			return null;
		}
		var manaItem = XplatAbstractions.INSTANCE.findManaItem(stack);
		if (manaItem == null || !manaItem.canReceiveManaFromItem(input)) {
			return null;
		}
		return Math.min(TRANSFER_RATE, manaItem.getMaxMana() - manaItem.getMana()) > 0 ? manaItem : null;
	}

	private ItemStack sparkStack() {
		var stack = sparkStack;
		if (stack == null) {
			sparkStack = stack = new ItemStack(getSparkItem());
		}
		return stack;
	}

	private void syncFlows() {
		int[] outgoing = flowsOrSame(FLOW_OUT, sentOutgoing);
		int[] incoming = flowsOrSame(FLOW_IN, sentIncoming);
		if (outgoing != sentOutgoing || incoming != sentIncoming) {
			sentOutgoing = outgoing;
			sentIncoming = incoming;
			XplatAbstractions.INSTANCE.sendToTracking(this, new SparkFlowPacket(getId(), outgoing, incoming));
		}
	}

	private static int[] flowsOrSame(IntArrayList ids, int[] previous) {
		int n = ids.size();
		if (n == 0) {
			return previous.length == 0 ? previous : NO_FLOWS;
		}
		int[] elements = ids.elements();
		Arrays.sort(elements, 0, n);
		if (n == previous.length && Arrays.equals(elements, 0, n, previous, 0, n)) {
			return previous;
		}
		return Arrays.copyOf(elements, n);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (sentOutgoing.length > 0 || sentIncoming.length > 0) {
			XplatAbstractions.INSTANCE.sendToPlayer(player, new SparkFlowPacket(getId(), sentOutgoing, sentIncoming));
		}
	}

	@Override
	public void checkReceiverFull() {
		var receiver = getAttachedManaReceiver();
		if (receiver != null) {
			receiverWasFull = receiver.isFull();
		} else {
			receiverWasFull = true;
		}
	}

	@Override
	public void updateTransfers() {
		inboundTransfers.clear();
		outgoingTransfers.clear();
		if (isRemoved()) {
			return;
		}
		switch (getUpgrade()) {
			case RECESSIVE -> {
				var otherSparks = SparkHelper.getSparksAround(level(), getX(), getY() + (getBbHeight() / 2), getZ(), getNetwork());
				Collections.shuffle(otherSparks);
				for (var otherSpark : otherSparks) {
					SparkUpgradeType otherUpgrade = otherSpark.getUpgrade();
					if (otherSpark != this
							&& otherUpgrade != SparkUpgradeType.DOMINANT
							&& otherUpgrade != SparkUpgradeType.RECESSIVE
							&& otherUpgrade != SparkUpgradeType.ISOLATED) {
						outgoingTransfers.add(otherSpark);
					}
				}
			}
			case DOMINANT -> {
				List<ManaSpark> validSparks = SparkHelper.getSparksAround(level(), getX(), getY() + (getBbHeight() / 2), getZ(), getNetwork());
				Collections.shuffle(validSparks);
				for (var spark : validSparks) {
					SparkUpgradeType otherUpgrade = spark.getUpgrade();
					if (spark != this && otherUpgrade == SparkUpgradeType.NONE && spark.getAttachedManaReceiver() instanceof ManaPool) {
						inboundTransfers.add(spark);
					}
				}
			}
		}
		filterTransfers();
	}

	public static void particleBeam(Player player, Entity e1, Entity e2) {
		if (e1 != null && e2 != null && !e1.level().isClientSide) {
			XplatAbstractions.INSTANCE.sendToPlayer(player, new BotaniaEffectPacket(EffectType.SPARK_NET_INDICATOR,
					e1.getX(), e1.getY(), e1.getZ(),
					e1.getId(), e2.getId()));
		}
	}

	protected Item getSparkItem() {
		return BotaniaItems.spark;
	}

	private void dropAndKill() {
		SparkUpgradeType upgrade = getUpgrade();
		spawnAtLocation(new ItemStack(getSparkItem()), 0F);
		if (upgrade != SparkUpgradeType.NONE) {
			spawnAtLocation(SparkAugmentItem.getByType(upgrade), 0F);
		}
		discard();
	}

	@Override
	public void remove(RemovalReason removalReason) {
		super.remove(removalReason);
		notifyOthers(getNetwork());
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (isAlive() && !stack.isEmpty()) {
			SparkUpgradeType upgrade = getUpgrade();
			if (stack.getItem() instanceof WandOfTheForestItem) {
				if (level().isClientSide) {
					if (!player.isShiftKeyDown() && PlayerHelper.isTruePlayer(player)) {
						for (var spark : SparkHelper.getSparksAround(level(), getX(), getY() + (getBbHeight() / 2), getZ(), getNetwork())) {
							SparkFlowEffects.beam(level(), this, spark.entity());
						}
					}
				} else {
					if (player.isShiftKeyDown() || !PlayerHelper.isTruePlayer(player)) {
						if (upgrade != SparkUpgradeType.NONE) {
							spawnAtLocation(SparkAugmentItem.getByType(upgrade), 0F);
							setUpgrade(SparkUpgradeType.NONE);

							// Recalculate transfers, recessive and dominant will register the proper transfers
							outgoingTransfers.clear();
							inboundTransfers.clear();
							notifyOthers(getNetwork());
						} else {
							dropAndKill();
						}
					}
				}

				return InteractionResult.sidedSuccess(level().isClientSide);
			} else if (stack.getItem() instanceof SparkAugmentItem newUpgrade && upgrade == SparkUpgradeType.NONE) {
				if (!level().isClientSide) {
					setUpgrade(newUpgrade.type);
					stack.shrink(1);
				}
				return InteractionResult.sidedSuccess(level().isClientSide);
			} else if (stack.is(BotaniaItems.phantomInk)) {
				if (!level().isClientSide) {
					setInvisible(true);
				}
				return InteractionResult.sidedSuccess(level().isClientSide);
			} else if (stack.getItem() instanceof DyeItem dye) {
				DyeColor color = dye.getDyeColor();
				if (color != getNetwork()) {
					if (!level().isClientSide) {
						setNetwork(color);
						stack.shrink(1);
					}
					return InteractionResult.sidedSuccess(level().isClientSide);
				}
			}
		}

		return InteractionResult.PASS;
	}

	@Override
	protected void readAdditionalSaveData(@NotNull CompoundTag cmp) {
		super.readAdditionalSaveData(cmp);
		setUpgrade(SparkUpgradeType.values()[cmp.getInt(TAG_UPGRADE)]);
	}

	@Override
	protected void addAdditionalSaveData(@NotNull CompoundTag cmp) {
		super.addAdditionalSaveData(cmp);
		cmp.putInt(TAG_UPGRADE, getUpgrade().ordinal());
	}

	@Override
	public SparkAttachable getAttachedTile() {
		resolveAttachment();
		return attachedTile;
	}

	@Nullable
	@Override
	public ManaReceiver getAttachedManaReceiver() {
		resolveAttachment();
		return attachedReceiver;
	}

	private void resolveAttachment() {
		Level level = level();
		long now = level.getGameTime();
		var be = attachedBe;
		if (now == attachedTick && (be == null || !be.isRemoved())) {
			return;
		}
		BlockPos pos = getAttachPos();
		BlockState state = level.getBlockState(pos);
		be = level.getBlockEntity(pos);
		attachedTile = XplatAbstractions.INSTANCE.findSparkAttachable(level, pos, state, be, Direction.UP);
		attachedReceiver = XplatAbstractions.INSTANCE.findManaReceiver(level, pos, state, be, Direction.UP);
		attachedBe = be;
		attachedTick = now;
	}

	private void filterTransfers() {
		SparkUpgradeType upgr = getUpgrade();
		Iterator<ManaSpark> iter = outgoingTransfers.iterator();
		while (iter.hasNext()) {
			ManaSpark spark = iter.next();
			SparkUpgradeType supgr = spark.getUpgrade();
			ManaReceiver arecv = ((Entity) spark).isAlive() ? spark.getAttachedManaReceiver() : null;

			if (spark == this
					|| !((Entity) spark).isAlive()
					|| spark.areIncomingTransfersDone()
					|| getNetwork() != spark.getNetwork()
					|| arecv == null
					|| arecv.isFull()
					|| !(upgr == SparkUpgradeType.NONE && supgr == SparkUpgradeType.DOMINANT
							|| upgr == SparkUpgradeType.RECESSIVE && (supgr == SparkUpgradeType.NONE || supgr == SparkUpgradeType.DISPERSIVE)
							|| !(arecv instanceof ManaPool))) {
				iter.remove();
			}
		}

		Iterator<ManaSpark> iter2 = inboundTransfers.iterator();
		while (iter2.hasNext()) {
			ManaSpark spark = iter2.next();
			SparkUpgradeType supgr = spark.getUpgrade();
			ManaReceiver otherManaReceiver = ((Entity) spark).isAlive() ? spark.getAttachedManaReceiver() : null;
			ManaReceiver myManaReceiver = getAttachedManaReceiver();

			if (spark == this
					|| !((Entity) spark).isAlive()
					|| getNetwork() != spark.getNetwork()
					|| otherManaReceiver == null
					|| otherManaReceiver.getCurrentMana() == 0
					|| myManaReceiver == null
					|| myManaReceiver.isFull()
					|| !(upgr == SparkUpgradeType.DOMINANT && supgr == SparkUpgradeType.NONE
							|| !(otherManaReceiver instanceof ManaPool))) {
				iter2.remove();
			}
		}
	}

	@Override
	public Collection<ManaSpark> getOutgoingTransfers() {
		return outgoingTransfers;
	}

	private boolean hasTransfer(ManaSpark entity) {
		return outgoingTransfers.contains(entity);
	}

	@Override
	public void registerTransfer(ManaSpark entity) {
		if (isRemoved() || hasTransfer(entity)) {
			return;
		}
		outgoingTransfers.add(entity);
		filterTransfers();
	}

	private void notifyOthers(DyeColor network) {
		for (var spark : SparkHelper.getSparksAround(level(), getX(), getY() + (getBbHeight() / 2), getZ(), network)) {
			spark.updateTransfers();
		}
	}

	@Override
	public SparkUpgradeType getUpgrade() {
		return upgrade;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (UPGRADE.equals(key)) {
			upgrade = SparkUpgradeType.values()[entityData.get(UPGRADE)];
		}
	}

	@Override
	public void setUpgrade(SparkUpgradeType upgrade) {
		entityData.set(UPGRADE, upgrade.ordinal());
		updateTransfers();
		notifyOthers(getNetwork());
	}

	@Override
	public void setNetwork(DyeColor color) {
		// The previous network needs to filter this spark out
		var previousNetwork = getNetwork();
		super.setNetwork(color);
		ManaSparkIndex.refresh(this);
		updateTransfers();
		notifyOthers(color);
		notifyOthers(previousNetwork);
	}

	@Override
	public boolean areIncomingTransfersDone() {
		if (getAttachedManaReceiver() instanceof ManaPool) {
			return false;
		}

		SparkAttachable attachable = getAttachedTile();
		return attachable != null && attachable.areIncomingTranfersDone();
	}

	public record WandHud(ManaSparkEntity entity) implements WandHUD {
		@Override
		public void renderHUD(GuiGraphics gui, Minecraft mc) {
			ItemStack sparkStack = new ItemStack(entity.getSparkItem());
			ItemStack augmentStack = SparkAugmentItem.getByType(entity.getUpgrade());
			DyeColor networkColor = entity.getNetwork();
			Component networkColorName = Component.translatable("color.minecraft." + networkColor.getName())
					.withStyle(ChatFormatting.ITALIC);
			int textColor = ColorHelper.getColorLegibleOnGrayBackground(networkColor);

			int width = 4 + Collections.max(Arrays.asList(
					mc.font.width(networkColorName),
					RenderHelper.itemWithNameWidth(mc, sparkStack),
					RenderHelper.itemWithNameWidth(mc, augmentStack)
			));
			int height = augmentStack.isEmpty() ? 30 : 50;
			int networkColorTextStart = mc.font.width(networkColorName) / 2;

			int centerX = mc.getWindow().getGuiScaledWidth() / 2;
			int centerY = mc.getWindow().getGuiScaledHeight() / 2;

			RenderHelper.renderHUDBox(gui, centerX - width / 2, centerY + 8, centerX + width / 2, centerY + 8 + height);

			RenderHelper.renderItemWithNameCentered(gui, mc, sparkStack, centerY + 10, textColor);
			RenderHelper.renderItemWithNameCentered(gui, mc, augmentStack, centerY + 28, textColor);
			gui.drawString(mc.font, networkColorName, centerX - networkColorTextStart, centerY + (augmentStack.isEmpty() ? 28 : 46), textColor);
		}
	}
}
