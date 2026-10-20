# Agent 须知

本仓库是 GregTech Odyssey 整合包维护的植物魔法分支（Botania GTO Edition）。

- 任何改变游戏设计的修改（数值、机制、配方规则、物品与方块行为、显示与反馈等），必须在同一次提交中更新根目录的 [GTO_CHANGES.md](GTO_CHANGES.md)，记录方式见该文档的"维护约定"。
- GTO_CHANGES.md 只记录游戏设计层面的差异，不记录实现方式。
- 模组 ID 保持 `botania`，不得改动资源命名空间与 Java 包名，以免破坏存档与附属模组兼容。
- 植物魔法本体不得依赖 GregTech、GTOCore、GTOLib 等整合包模组；需要整合包参与的行为，通过通用接口或配方字段开放，由整合包侧注册或声明。唯一例外是 DataSyncLib：方块实体向客户端的同步统一走它（存盘仍用原版 NBT）。
- 只维护 Forge 版，Fabric 子项目已删除；构建与运行都用 JDK 21（DataSyncLib 是 Java 21 字节码）。Xplat 的数据生成用 `./gradlew :Forge:runXplatData`，输出到 `Xplat/src/generated/resources`。
- 持续性或高频的视觉特效（每 tick、每秒反复出现的）由客户端根据已同步的状态或本地事件生成，不从服务端发包；低频的一次性事件特效（合成完成、使用物品、实体被处理等，服务端才知道何时发生）可以由服务端发包。

## 发布

- 发布方式与 GTO 的 AE、GTM 分支相同：Forge 子项目使用 `com.gto.gtopublishgradleplugin`，发布到 `https://gtodyssey.com/maven/releases`，坐标为 `vazkii.botania:Botania-forge-1.20.1:<版本>`。凭据从 `~/.gradle/gradle.properties` 的 `gtodysseyRepositoryUsername` / `gtodysseyRepositoryPassword` 读取，不得写入仓库。
- 版本号写在 `gradle.properties` 的 `gto_version`，格式为 `<上游构建号>.<年月>.<序号>`，例如 `457.2610.1`。每次发布都要递增；同步上游新构建时，第一段跟着上游构建号走。
- 本地联调用 `./gradlew :Forge:publishToMavenLocal`，GTOCore 的 `botania_version` 改成同一个版本号即可。正式发布用 `./gradlew :Forge:gtoValidate`，确认无误后再运行 `./gradlew :Forge:gtoPublishMaven`。
- 整合包统一发布时，如果本仓库自上次发布以来有改动，需要和 AE、GTM 一起发布新版本，并同步更新 GTOCore 的 `botania_version`。
- CurseForge 项目 ID 为 1754410，已开启 CurseForge 上传；令牌放在 `~/.gradle/gradle.properties` 的 `gtoCurseforgeToken`。`./gradlew :Forge:gtoPublish` 会依次完成预检、构建，并发布到 Maven 和 CurseForge。
