package vazkii.botania.common.block.block_entity;

import com.gto.registrate.util.entry.BlockEntityEntry;

import vazkii.botania.common.BotaniaBlocks;
import vazkii.botania.common.MythicBotanyBlocks;
import vazkii.botania.common.block.mana.ManaPools;

import static vazkii.botania.common.BotaniaRegistration.BOTANIA;

/**
 * 复刻内容的方块实体注册表。
 *
 * <p>
 * 所有魔力池变体共用一个方块实体类型，容量差异由各自方块的 {@code ManaPoolSpec} 决定；
 * 三种水晶共用一套方块实体与渲染器。
 * </p>
 */
public final class BotaniaBlockEntities {

    private BotaniaBlockEntities() {}

    public static void init() {}

    public static final BlockEntityEntry<ManaPools.Pool> MANA_POOL = BOTANIA.<ManaPools.Pool>blockEntity("mana_pool", ManaPools.Pool::new)
            .validBlocks(
                    BotaniaBlocks.MANA_POOL,
                    BotaniaBlocks.CREATIVE_POOL,
                    BotaniaBlocks.DILUTED_POOL,
                    BotaniaBlocks.FABULOUS_POOL,
                    BotaniaBlocks.MANA_POOL)
            .register();

    public static final BlockEntityEntry<ManaPools.Large> LARGE_MANA_POOL = BOTANIA.<ManaPools.Large>blockEntity("large_mana_pool", ManaPools.Large::new)
            .validBlocks(BotaniaBlocks.LARGE_MANA_POOL)
            .register();

    public static final BlockEntityEntry<BotaniaBlockEntity> CORPOREA_INDEX = BOTANIA
            .<BotaniaBlockEntity>blockEntity("corporea_index", BotaniaBlockEntity::new)
            .validBlocks(BotaniaBlocks.CORPOREA_INDEX)
            .register();

    public static final BlockEntityEntry<AlfheimPortalBlockEntity> ALF_PORTAL = BOTANIA
            .<AlfheimPortalBlockEntity>blockEntity("alf_portal", AlfheimPortalBlockEntity::new)
            .validBlocks(BotaniaBlocks.ALFHEIM_PORTAL)
            .register();

    public static final BlockEntityEntry<TileReturnPortal> RETURN_PORTAL = BOTANIA
            .<TileReturnPortal>blockEntity("return_portal", TileReturnPortal::new)
            .validBlocks(MythicBotanyBlocks.MB_RETURN_PORTAL)
            .register();

    public static final BlockEntityEntry<PylonBlockEntity> PYLON = BOTANIA
            .<PylonBlockEntity>blockEntity("pylon", PylonBlockEntity::new)
            .validBlocks(
                    BotaniaBlocks.MANA_PYLON,
                    BotaniaBlocks.NATURA_PYLON,
                    BotaniaBlocks.GAIA_PYLON,
                    MythicBotanyBlocks.MB_ALFSTEEL_PYLON)
            .register();
}
