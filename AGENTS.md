# Agent 须知

本仓库是 GregTech Odyssey 整合包维护的植物魔法分支（Botania GTO Edition）。

- 任何改变游戏设计的修改（数值、机制、配方规则、物品与方块行为、显示与反馈等），必须在同一次提交中更新根目录的 [GTO_CHANGES.md](GTO_CHANGES.md)，记录方式见该文档的"维护约定"。
- GTO_CHANGES.md 只记录游戏设计层面的差异，不记录实现方式。
- 模组 ID 保持 `botania`，不得改动资源命名空间与 Java 包名，以免破坏存档与附属模组兼容。
- 附属模组（神话植物学、ExtraBotany 等）按上游签名编译。改公开或受保护方法的参数、返回类型（例如魔力 int 改 long）时，必须保留上游原签名作为转发重载，不能直接改掉，否则附属模组运行时抛 `NoSuchMethodError`（`HUDHandler.renderManaBar` 曾因此让神话植物学的符文仪式配方页渲染出错）。
- 植物魔法本体不得依赖 GregTech、GTOCore、GTOLib 等整合包模组；需要整合包参与的行为，通过通用接口或配方字段开放，由整合包侧注册或声明。例外只有 DataSyncLib、GTOUtils 与 GTORenders：方块实体向客户端的同步统一走 DataSyncLib（存盘仍用原版 NBT）；按范围找方块实体或实体一律用 GTOUtils 的索引，不要逐格扫描或遍历全维度；按方块状态在一片范围里找格子用 GTOUtils 的 `LevelSections`（`betweenClosed`/`inRange`/`withinManhattan`，按区段调色板跳过不可能命中的区段，结果顺序与原版逐格遍历相同）。按类型或能力收录的方块实体索引与按维度存放的数据集中在 `BotaniaBlockEntityIndexes`（模组构造时初始化，新索引只能加在这里）：按类型用 `BlockEntityIndex`，区块增删方块实体时由 GTOUtils 自动登记注销，方块实体里不写钩子；按能力用 `ProbedBlockEntityIndex`；按维度存放用 `LevelStore`。成员由条件决定的（如入网的魔力池）用 `LevelBlockIndex` 自己登记，见 `ManaNetworkHandler`；实体见 `ManaSparkIndex`。查询范围用 `BlockRange` 选球形或长方体，结果依赖原版遍历顺序的用 `BlockOrder` 排序。 wisp 与非腐化 sparkle 粒子的 Provider 调用 GTORenders 的 `MoteParticles`（带本模组原贴图的 `MoteLook`，返回 null，不生成粒子对象），由 GPU 批量绘制；各处照常 `addParticle(WispParticleData/SparkleParticleData)` 即可，外观与原版一致。按像素画液面或贴图盒用 GTORenders 的 `SpriteQuads`（见 `LargeManaPoolBlockEntityRenderer`）。
- 魔力在两个持有者之间转移一律走 `ManaEndpoint.transfer(from, to, amount)`：它先模拟目标能收多少、从来源扣、只给实际扣到的量、给不出去的退回来源。`ManaReceiver` 与 `ManaItem` 都是 `ManaEndpoint`，`addMana/removeMana(amount, simulate)` 返回实际变化量；存取结果与读数可能不一致的实现（福鲁伊克斯魔力池、P2P、AE 槽位适配、创造魔力池）必须覆写这两个方法如实返回。不要手写"先给后扣"或用 `getCurrentMana` 代替实际变化量记账；代理整个 ME 网络的方块不跨操作缓存读数，存储总线对着同网的代理方块视为空。只改动自身存量（如符文祭坛扣自己的魔力）可以照旧 `receiveMana`。
- 应用能源 2（AE2）是可选联动：只在 `src/main/java/vazkii/botania/forge/integration/ae2` 里引用 AE 类（compileOnly），入口由 `ModList.isLoaded("ae2")` 守住；没装 AE 时不得加载这个包里的任何类。联动的配方带 `forge:mod_loaded` 条件，方块掉落写在代码里（掉落表不支持条件），改完要在不装 AE 的情况下跑一次 `runServer` 确认无报错。该联动取代了 Applied Botanics，`appbot` 命名空间的注册名通过别名和缺失映射自动转到 `botania`。
- 只维护 Forge 单项目，源码与资源统一在 `src/`，不再接入 Xplat/Fabric 项目或跨平台依赖；构建与运行都用 JDK 21（DataSyncLib 是 Java 21 字节码）。数据生成统一用 `./gradlew runData`，输出到 `src/generated/resources`。旧 `vazkii.botania.xplat` 接口仅为附属模组二进制兼容保留，固定使用 Forge 实现，不再通过 ServiceLoader 选择平台。
- 持续性或高频的视觉特效（每 tick、每秒反复出现的）由客户端根据已同步的状态或本地事件生成，不从服务端发包；低频的一次性事件特效（合成完成、使用物品、实体被处理等，服务端才知道何时发生）可以由服务端发包。

## 发布

- 发布方式与 GTO 的 AE、GTM 分支相同：根项目使用 `com.gto.gtopublishgradleplugin`，发布到 `https://gtodyssey.com/maven/releases`，坐标为 `vazkii.botania:Botania-forge-1.20.1:<版本>`。凭据从 `~/.gradle/gradle.properties` 的 `gtodysseyRepositoryUsername` / `gtodysseyRepositoryPassword` 读取，不得写入仓库。
- 版本号写在 `gradle.properties` 的 `gto_version`，格式为 `<上游构建号>.<年月>.<序号>`，例如 `457.2610.1`。每次发布都要递增；同步上游新构建时，第一段跟着上游构建号走。
- 本地联调用 `./gradlew publishToMavenLocal`，GTOCore 的 `botania_version` 改成同一个版本号即可。正式发布用 `./gradlew gtoValidate`，确认无误后再运行 `./gradlew gtoPublishMaven`。
- 整合包统一发布时，如果本仓库自上次发布以来有改动，需要和 AE、GTM 一起发布新版本，并同步更新 GTOCore 的 `botania_version`。
- CurseForge 项目 ID 为 1754410，已开启 CurseForge 上传；令牌放在 `~/.gradle/gradle.properties` 的 `gtoCurseforgeToken`。`./gradlew gtoPublish` 会依次完成预检、构建，并发布到 Maven 和 CurseForge。CurseForge 文件的前置依赖写在 `gtoPublish` 的 `curseforgeRelations`（键是项目 slug，现为 `gtoutils: 'required'`），上传时自动声明，不用去网页补；新增外部依赖时同步加上。
