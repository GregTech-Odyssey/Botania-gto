package vazkii.botania.common.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.common.BotaniaBlocks;
import vazkii.botania.common.BotaniaItems;
import vazkii.botania.common.BotaniaReplica;
import vazkii.botania.common.MythicBotanyItems;
import vazkii.botania.common.MythicBotanyPlayerData;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.common.item.relic.RelicItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 盖亚守护者。
 *
 * <p>
 * 迁移原模组的生成保护、波次召唤、传送、伤害上限、Boss 血条与掉落。
 * 竞技场校验由 Patchouli 多方块简化为「召唤点附近 12 格内的盖亚水晶与玩家」。
 * </p>
 */
public class GaiaGuardianEntity extends PathfinderMob {

    public static final float ARENA_RANGE = 12F;
    public static final int ARENA_HEIGHT = 5;
    public static final float MAX_HP = 320F;

    /** 子类可覆盖的最大生命值。 */
    public float getMaxHp() {
        return MAX_HP;
    }

    private static final int SPAWN_TICKS = 160;
    private static final int DAMAGE_CAP = 10000;

    private static final int MOB_SPAWN_START_TICKS = 20;
    private static final int MOB_SPAWN_END_TICKS = 80;
    private static final int MOB_SPAWN_BASE_TICKS = 800;
    private static final int MOB_SPAWN_TICKS = MOB_SPAWN_BASE_TICKS + MOB_SPAWN_START_TICKS + MOB_SPAWN_END_TICKS;
    private static final int MOB_SPAWN_WAVES = 10;
    private static final int MOB_SPAWN_WAVE_TIME = MOB_SPAWN_BASE_TICKS / MOB_SPAWN_WAVES;

    private static final String TAG_INVUL_TIME = "invulTime";
    private static final String TAG_AGGRO = "aggro";
    private static final String TAG_SOURCE_X = "sourceX";
    private static final String TAG_SOURCE_Y = "sourceY";
    private static final String TAG_SOURCE_Z = "sourcesZ";
    private static final String TAG_MOB_SPAWN_TICKS = "mobSpawnTicks";
    private static final String TAG_HARD_MODE = "hardMode";
    private static final String TAG_PLAYER_COUNT = "playerCount";

    private static final EntityDataAccessor<Integer> INVUL_TIME = SynchedEntityData.defineId(
            GaiaGuardianEntity.class, EntityDataSerializers.INT);

    private boolean aggro = false;
    private int tpDelay = 0;
    private boolean spawnLandmines = false;
    private boolean spawnPixies = false;
    private int mobSpawnTicks = MOB_SPAWN_TICKS;
    private int playerCount = 0;
    private boolean hardMode = false;
    private BlockPos source = BlockPos.ZERO;
    private final List<UUID> playersWhoAttacked = new ArrayList<>();
    @Nullable
    private Player trueKiller;
    private final ServerBossEvent bossInfo = (ServerBossEvent) new ServerBossEvent(
            Component.translatable("entity.botania.doppleganger"),
            BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS).setCreateWorldFog(true);

    public GaiaGuardianEntity(EntityType<? extends GaiaGuardianEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 1000;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.4)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ==================== 召唤 ====================

    /**
     * 在召唤点生成盖亚守护者。
     *
     * @param hard 使用盖亚之魂召唤时为困难模式
     */
    public static boolean spawn(Player player, ItemStack stack, Level level, BlockPos pos, boolean hard) {
        return spawn(player, stack, level, pos, hard, BotaniaEntities.DOPPLEGANGER.get());
    }

    public static boolean spawn(Player player, ItemStack stack, Level level, BlockPos pos, boolean hard,
                                net.minecraft.world.entity.EntityType<? extends GaiaGuardianEntity> type) {
        if (!(level instanceof ServerLevel server) || player == null) {
            return false;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            player.sendSystemMessage(Component.translatable("botaniamisc.peacefulNoob").withStyle(ChatFormatting.RED));
            return false;
        }
        if (countGaiaGuardiansAround(level, pos) > 0) {
            return false;
        }
        List<Player> players = getPlayersAround(level, pos);
        if (players.isEmpty()) {
            return false;
        }
        if (countPylonsAround(level, pos) < 4) {
            player.displayClientMessage(Component.translatable("botaniamisc.gaiaRitualPylons"), true);
            return false;
        }
        GaiaGuardianEntity guardian = type.create(server);
        if (guardian == null) {
            return false;
        }
        guardian.source = pos;
        guardian.hardMode = hard;
        guardian.playerCount = players.size();
        guardian.setInvulTime(SPAWN_TICKS);
        guardian.mobSpawnTicks = MOB_SPAWN_TICKS;
        float healthMultiplier = 1F;
        if (players.size() > 1) {
            healthMultiplier += players.size() * 0.25F;
        }
        float maxHp = guardian.getMaxHp();
        guardian.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                .setBaseValue(maxHp * healthMultiplier);
        guardian.setHealth(1F);
        if (hard) {
            guardian.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(15);
        }
        guardian.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
        server.addFreshEntity(guardian);
        server.playSound(null, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1F, 1F);
        server.playSound(null, pos, vazkii.botania.common.handler.BotaniaSounds.gaiaSummon.get(),
                SoundSource.HOSTILE, 1F, 1F);
        server.playSound(null, pos,
                hard ? vazkii.botania.common.handler.BotaniaSounds.gaiaMusic2.get() : vazkii.botania.common.handler.BotaniaSounds.gaiaMusic1.get(),
                SoundSource.RECORDS, 1F, 1F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return true;
    }

    private static int countGaiaGuardiansAround(Level level, BlockPos source) {
        return level.getEntitiesOfClass(GaiaGuardianEntity.class, getArenaBB(source)).size();
    }

    private static int countPylonsAround(Level level, BlockPos source) {
        Block gaiaPylon = BotaniaReplica.BOTANIA.getBlock("gaia_pylon");
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                source.offset(-(int) ARENA_RANGE, -ARENA_HEIGHT, -(int) ARENA_RANGE),
                source.offset((int) ARENA_RANGE, ARENA_HEIGHT, (int) ARENA_RANGE))) {
            if (level.getBlockState(pos).getBlock() == gaiaPylon) {
                count++;
            }
        }
        return count;
    }

    private static List<Player> getPlayersAround(Level level, BlockPos source) {
        return level.getEntitiesOfClass(Player.class, getArenaBB(source), LivingEntity::isAlive);
    }

    private static AABB getArenaBB(@NotNull BlockPos source) {
        return new AABB(source.getX() - ARENA_RANGE, source.getY() - ARENA_HEIGHT, source.getZ() - ARENA_RANGE,
                source.getX() + ARENA_RANGE, source.getY() + ARENA_HEIGHT, source.getZ() + ARENA_RANGE);
    }

    // ==================== 属性与 AI ====================

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 64.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        targetSelector.addGoal(0, new HurtByTargetGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(INVUL_TIME, 0);
    }

    public int getInvulTime() {
        return entityData.get(INVUL_TIME);
    }

    public void setInvulTime(int time) {
        entityData.set(INVUL_TIME, time);
    }

    public BlockPos getSource() {
        return source;
    }

    public boolean isHardMode() {
        return hardMode;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public UUID getBossInfoUuid() {
        return bossInfo.getId();
    }

    public List<Player> getPlayersAround() {
        return getPlayersAround(level(), source);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {}

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void heal(float amount) {
        if (getInvulTime() <= 0) {
            super.heal(amount);
        }
    }

    @Override
    public void kill() {
        bossInfo.removeAllPlayers();
        super.kill();
    }

    // ==================== 伤害 ====================

    /** 单次伤害上限；盖亚 III 会覆盖为更低的数值。 */
    public float getDamageCap() {
        return DAMAGE_CAP;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (getInvulTime() > 0) {
            return false;
        }
        if (!(source.getEntity() instanceof Player player) || player instanceof net.minecraftforge.common.util.FakePlayer) {
            return false;
        }
        if (!playersWhoAttacked.contains(player.getUUID())) {
            playersWhoAttacked.add(player.getUUID());
        }
        return super.hurt(source, Math.min(getDamageCap(), amount));
    }

    @Override
    protected void actuallyHurt(@NotNull DamageSource source, float amount) {
        super.actuallyHurt(source, Math.min(getDamageCap(), amount));
        aggro = true;
        Entity attacker = source.getDirectEntity();
        if (attacker != null) {
            Vec3 thisVec = new Vec3(getX(), getY() + getBbHeight() / 2, getZ());
            Vec3 attackerVec = new Vec3(attacker.getX(), attacker.getY() + attacker.getBbHeight() / 2, attacker.getZ());
            Vec3 motion = thisVec.subtract(attackerVec).normalize().scale(0.75);
            if (getHealth() > 0) {
                setDeltaMovement(-motion.x, 0.5, -motion.z);
                tpDelay = 4;
                spawnPixies = true;
            }
        }
        invulnerableTime = Math.max(invulnerableTime, 20);
    }

    @Override
    protected float getDamageAfterArmorAbsorb(@NotNull DamageSource source, float damage) {
        return super.getDamageAfterArmorAbsorb(source, Math.min(getDamageCap(), damage));
    }

    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);
        if (source.getEntity() instanceof Player player) {
            trueKiller = player;
        }
        if (!level().isClientSide) {
            level().playSound(null, blockPosition(), BotaniaSounds.gaiaDeath.get(),
                    SoundSource.HOSTILE, 1F, 1F);
            dropDeathLoot();
            dropFimbultyrTablet(source);
            bossInfo.removeAllPlayers();
        }
    }

    /** 困难模式（盖亚 II）被喝过智慧泉水的玩家击杀时，额外掉落菲姆布尔的符文牌。 */
    private void dropFimbultyrTablet(DamageSource source) {
        if (!hardMode || !(source.getEntity() instanceof Player player) || player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        if (!MythicBotanyPlayerData.hasMimirKnowledge(player)) {
            return;
        }
        ItemStack tablet = MythicBotanyItems.MB_FIMBULTYR_TABLET.asStack();
        if (!tablet.isEmpty()) {
            spawnAtLocation(tablet, 0.5F);
        }
    }

    private static final String[] ANCIENT_WILLS = {
            "ancient_will_ahrim", "ancient_will_dharok", "ancient_will_guthan",
            "ancient_will_torag", "ancient_will_verac", "ancient_will_karil"
    };

    private static final String[] RUNES = {
            "rune_water", "rune_earth", "rune_air", "rune_fire",
            "rune_spring", "rune_summer", "rune_autumn", "rune_winter",
            "rune_mana", "rune_lust", "rune_gluttony", "rune_greed",
            "rune_sloth", "rune_wrath", "rune_envy", "rune_pride"
    };

    /** 与原版战利品表一致：对每位参战玩家各结算一次掉落。 */
    protected void dropDeathLoot() {
        if (trueKiller == null || trueKiller instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        List<UUID> attackers = new ArrayList<>(playersWhoAttacked);
        if (trueKiller != null && !attackers.contains(trueKiller.getUUID())) {
            attackers.add(trueKiller.getUUID());
        }
        for (UUID uuid : attackers) {
            if (!(level().getPlayerByUUID(uuid) instanceof ServerPlayer player) || player instanceof net.minecraftforge.common.util.FakePlayer) {
                continue;
            }
            dropForPlayer(player, player == trueKiller);
        }
    }

    private void dropForPlayer(Player player, boolean isTrueKiller) {
        int essence = hardMode ? 10 : 6;
        if (isTrueKiller) {
            essence = hardMode ? 16 : 8;
        }
        dropAt(player, "life_essence", essence);

        Item elementiumAxe = BotaniaReplica.BOTANIA.getItem("elementium_axe");
        if (isTrueKiller && elementiumAxe != null && player.getMainHandItem().is(elementiumAxe)) {
            int looting = EnchantmentHelper.getEnchantmentLevel(Enchantments.MOB_LOOTING, player);
            if (level().random.nextFloat() < 0.0769F * (1 + looting)) {
                dropAt(player, new ItemStack(BotaniaBlocks.GAIA_HEAD.get().asItem()));
            }
        }

        if (!hardMode) {
            if (level().random.nextFloat() < 0.2F) {
                dropAt(player, "record_gaia_1", 1);
            }
            return;
        }

        if (level().random.nextFloat() < 0.5F) {
            if (level().random.nextFloat() < 0.3F) {
                dropAt(player, "blacker_lotus", 1);
            } else {
                dropAt(player, "black_lotus", 1 + level().random.nextInt(3));
            }
        }

        dropAt(player, ANCIENT_WILLS[level().random.nextInt(ANCIENT_WILLS.length)], 1);

        if (level().random.nextFloat() < 0.25F) {
            dropAt(player, "overgrowth_seed", 1 + level().random.nextInt(3));
        }
        if (level().random.nextFloat() < 0.9F) {
            dropAt(player, "manasteel_ingot", 16 + level().random.nextInt(12));
        }
        if (level().random.nextFloat() < 0.7F) {
            dropAt(player, "mana_pearl", 8 + level().random.nextInt(6));
        }
        if (level().random.nextFloat() < 0.5F) {
            dropAt(player, "mana_diamond", 4 + level().random.nextInt(3));
        }

        int runeRolls = 1 + level().random.nextInt(6);
        for (int i = 0; i < runeRolls; i++) {
            dropAt(player, RUNES[level().random.nextInt(RUNES.length)], 2 + level().random.nextInt(3));
        }

        if (level().random.nextFloat() < 0.2F) {
            dropRelic(player, "pinkinator");
        }

        if (level().random.nextFloat() < 0.44F) {
            int roll = level().random.nextInt(44);
            if (roll < 14) {
                dropAt(player, "record_gaia_2", 1);
            } else if (roll < 29) {
                dropAt(player, new ItemStack(Items.MUSIC_DISC_13));
            } else {
                dropAt(player, new ItemStack(Items.MUSIC_DISC_WAIT));
            }
        }

        dropRelic(player, "dice");
    }

    private void dropRelic(Player player, String id) {
        Item item = BotaniaReplica.BOTANIA.getItem(id);
        if (item == null || item == Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        RelicItem.bindToUUID(stack, player.getUUID());
        dropAt(player, stack);
    }

    private void dropAt(Player player, String id, int count) {
        Item item = BotaniaReplica.BOTANIA.getItem(id);
        if (item == null || item == Items.AIR || count <= 0) {
            return;
        }
        dropAt(player, new ItemStack(item, count));
    }

    private void dropAt(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity entity = player.spawnAtLocation(stack);
        if (entity != null) {
            entity.setNoPickUpDelay();
        }
    }

    /** 参与过战斗的玩家，掉落按玩家逐人结算。 */
    protected List<UUID> getPlayersWhoAttacked() {
        return playersWhoAttacked;
    }

    // ==================== tick ====================

    @Override
    public void aiStep() {
        super.aiStep();
        int invul = getInvulTime();
        if (level().isClientSide) {
            if (invul > 0) {
                for (int i = 0; i < 3; i++) {
                    level().addParticle(ParticleTypes.PORTAL,
                            getX() + (level().random.nextDouble() - 0.5) * 1.5,
                            getY() + level().random.nextDouble() * 2.0,
                            getZ() + (level().random.nextDouble() - 0.5) * 1.5,
                            0, 0.05, 0);
                }
            }
            return;
        }

        if (invul > 0) {
            setHealth(getHealth() + (getMaxHealth() - 1F) / SPAWN_TICKS);
            setInvulTime(invul - 1);
            Vec3 movement = getDeltaMovement();
            setDeltaMovement(movement.x, 0.03, movement.z);
            if (invul % 20 == 0) {
                level().playSound(null, blockPosition(), SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 0.6F, 0.5F);
            }
            return;
        }

        aggro = true;
        boolean dying = getHealth() / getMaxHealth() < 0.2F;
        if (dying && mobSpawnTicks > 0) {
            int reverseTicks = MOB_SPAWN_TICKS - mobSpawnTicks;
            if (reverseTicks < MOB_SPAWN_START_TICKS) {
                setDeltaMovement(getDeltaMovement().x, 0.2, getDeltaMovement().z);
                setInvulTime(invul + 1);
            }
            if (reverseTicks > MOB_SPAWN_START_TICKS * 2 && mobSpawnTicks > MOB_SPAWN_END_TICKS && mobSpawnTicks % MOB_SPAWN_WAVE_TIME == 0) {
                spawnMobs();
            }
            mobSpawnTicks--;
        }

        if (hardMode && tickCount % 15 < 4) {
            spawnMissile();
        }

        if (!dying && --tpDelay <= 0) {
            teleportRandomly();
            if (spawnLandmines) {
                spawnLandmines();
            }
            spawnPixies(spawnPixies ? Math.max(1, level().random.nextInt(hardMode ? 6 : 3)) : 1);
            tpDelay = 40;
            spawnLandmines = true;
            spawnPixies = false;
        }

        for (Player player : getPlayersAround()) {
            keepInsideArena(player);
        }
    }

    private void spawnMobs() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        List<Player> players = getPlayersAround();
        for (int pl = 0; pl < Math.max(1, playerCount); pl++) {
            for (int i = 0; i < 3 + level().random.nextInt(2); i++) {
                Mob mob = switch (level().random.nextInt(3)) {
                    case 0 -> {
                        if (level().random.nextInt(hardMode ? 3 : 12) == 0) {
                            yield EntityType.WITCH.create(server);
                        }
                        yield EntityType.ZOMBIE.create(server);
                    }
                    case 1 -> {
                        if (level().random.nextInt(8) == 0) {
                            yield EntityType.WITHER_SKELETON.create(server);
                        }
                        yield EntityType.SKELETON.create(server);
                    }
                    case 2 -> {
                        if (!players.isEmpty()) {
                            for (int j = 0; j < 1 + level().random.nextInt(hardMode ? 8 : 5); j++) {
                                spawnPixie(server, players.get(level().random.nextInt(players.size())));
                            }
                        }
                        yield null;
                    }
                    default -> null;
                };
                if (mob == null) {
                    continue;
                }
                if (!mob.fireImmune()) {
                    mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
                }
                double x = getX() + 0.5 + level().random.nextDouble() * 6 - 3;
                double z = getZ() + 0.5 + level().random.nextDouble() * 6 - 3;
                mob.moveTo(x, getY() - 1, z, level().random.nextFloat() * 360F, 0);
                mob.finalizeSpawn(server, server.getCurrentDifficultyAt(mob.blockPosition()),
                        MobSpawnType.MOB_SUMMONED, null, null);
                if (mob instanceof WitherSkeleton && hardMode) {
                    mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BotaniaItems.ELEMENTIUM_SWORD.get()));
                }
                server.addFreshEntity(mob);
            }
        }
        level().playSound(null, blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1F, 0.5F);
    }

    private void spawnPixie(ServerLevel server, Player target) {
        PixieEntity pixie = new PixieEntity(level());
        pixie.setProps(target, this, 1, 8F);
        pixie.moveTo(getX() + getBbWidth() / 2, getY() + 2, getZ() + getBbWidth() / 2, 0, 0);
        pixie.finalizeSpawn(server, server.getCurrentDifficultyAt(pixie.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);
        server.addFreshEntity(pixie);
    }

    private void spawnLandmines() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        List<Player> players = getPlayersAround();
        if (players.isEmpty()) {
            return;
        }
        int count = hardMode ? 7 : 6;
        for (int i = 0; i < count; i++) {
            int x = source.getX() - 10 + random.nextInt(20);
            int y = (int) players.get(random.nextInt(players.size())).getY();
            int z = source.getZ() - 10 + random.nextInt(20);
            MagicLandmineEntity landmine = BotaniaEntities.MAGIC_LANDMINE.create(server);
            if (landmine == null) {
                continue;
            }
            landmine.setPos(x + 0.5, y, z + 0.5);
            landmine.summoner = this;
            server.addFreshEntity(landmine);
        }
    }

    private void spawnPixies(int perPlayer) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        List<Player> players = getPlayersAround();
        if (players.isEmpty()) {
            return;
        }
        for (int pl = 0; pl < Math.max(1, playerCount); pl++) {
            for (int i = 0; i < perPlayer; i++) {
                spawnPixie(server, players.get(random.nextInt(players.size())));
            }
        }
    }

    private void spawnMissile() {
        MagicMissileEntity missile = new MagicMissileEntity(this, true);
        missile.setPos(getX() + (Math.random() - 0.5 * 0.1), getY() + 2.4 + (Math.random() - 0.5 * 0.1),
                getZ() + (Math.random() - 0.5 * 0.1));
        if (missile.findTarget()) {
            level().playSound(null, blockPosition(), BotaniaSounds.missile.get(), SoundSource.HOSTILE, 1F,
                    0.8F + (float) Math.random() * 0.2F);
            level().addFreshEntity(missile);
        }
    }

    private void teleportRandomly() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        double x = getX() + (level().random.nextDouble() - 0.5) * ARENA_RANGE * 1.5;
        double z = getZ() + (level().random.nextDouble() - 0.5) * ARENA_RANGE * 1.5;
        double y = source.getY() + 1 + level().random.nextInt(3);
        if (Math.abs(x - source.getX()) > ARENA_RANGE || Math.abs(z - source.getZ()) > ARENA_RANGE) {
            return;
        }
        Vec3 old = position();
        teleportTo(x, y, z);
        server.sendParticles(ParticleTypes.PORTAL, old.x, old.y + getBbHeight() / 2, old.z, 32, 0.5, 0.5, 0.5, 0.1);
        server.sendParticles(ParticleTypes.PORTAL, x, y + getBbHeight() / 2, z, 32, 0.5, 0.5, 0.5, 0.1);
        level().playSound(null, old.x, old.y, old.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1F, 1F);
        level().playSound(null, x, y, z, vazkii.botania.common.handler.BotaniaSounds.gaiaTeleport.get(),
                SoundSource.HOSTILE, 1F, 1F);
    }

    private void keepInsideArena(Player player) {
        if (player.isSpectator() || player.isCreative()) {
            return;
        }
        if (!getArenaBB(source).contains(player.position())) {
            Vec3 center = Vec3.atCenterOf(source);
            Vec3 direction = center.subtract(player.position()).normalize().scale(1.5);
            player.teleportTo(center.x + direction.x, source.getY() + 1, center.z + direction.z);
            player.hurt(damageSources().mobAttack(this), 2);
        }
    }

    // ==================== 存盘 ====================

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_INVUL_TIME, getInvulTime());
        tag.putBoolean(TAG_AGGRO, aggro);
        tag.putInt(TAG_SOURCE_X, source.getX());
        tag.putInt(TAG_SOURCE_Y, source.getY());
        tag.putInt(TAG_SOURCE_Z, source.getZ());
        tag.putInt(TAG_MOB_SPAWN_TICKS, mobSpawnTicks);
        tag.putBoolean(TAG_HARD_MODE, hardMode);
        tag.putInt(TAG_PLAYER_COUNT, playerCount);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setInvulTime(tag.getInt(TAG_INVUL_TIME));
        aggro = tag.getBoolean(TAG_AGGRO);
        source = new BlockPos(tag.getInt(TAG_SOURCE_X), tag.getInt(TAG_SOURCE_Y), tag.getInt(TAG_SOURCE_Z));
        mobSpawnTicks = tag.contains(TAG_MOB_SPAWN_TICKS) ? tag.getInt(TAG_MOB_SPAWN_TICKS) : MOB_SPAWN_TICKS;
        if (mobSpawnTicks <= 0) {
            mobSpawnTicks = MOB_SPAWN_TICKS;
        }
        hardMode = tag.getBoolean(TAG_HARD_MODE);
        playerCount = tag.getInt(TAG_PLAYER_COUNT);
    }

    // ==================== Boss 血条 ====================

    @Override
    public void startSeenByPlayer(@NotNull ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossInfo.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(@NotNull ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossInfo.removePlayer(player);
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        bossInfo.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossInfo.setProgress(getHealth() / getMaxHealth());
    }

    @Nullable
    @Override
    public Entity changeDimension(@NotNull ServerLevel level) {
        return null;
    }
}
