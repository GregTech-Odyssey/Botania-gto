package vazkii.botania.common.item.relic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import org.jetbrains.annotations.NotNull;
import vazkii.botania.common.BotaniaItems;
import vazkii.botania.common.handler.BotaniaSounds;

import java.util.ArrayList;
import java.util.List;

/** 命运骰子：未集齐 6 件遗物时随机变成其中一件，集齐后掷出奖励战利品。 */
public class DiceOfFateItem extends RelicItem {

    public DiceOfFateItem(Properties properties) {
        super(properties);
    }

    private static List<Item> relicItems() {
        return List.of(
                BotaniaItems.INFINITE_FRUIT.get(),
                BotaniaItems.KING_KEY.get(),
                BotaniaItems.FLUGEL_EYE.get(),
                BotaniaItems.THOR_RING.get(),
                BotaniaItems.ODIN_RING.get(),
                BotaniaItems.LOKI_RING.get());
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && getSoulbindUUID(stack) == null) {
            bindToUUID(stack, player.getUUID());
        }
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isRightPlayer(stack, player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), BotaniaSounds.diceOfFate.get(),
                SoundSource.PLAYERS, 1F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));

        List<Item> relics = relicItems();
        List<Integer> possible = new ArrayList<>();
        for (int i = 0; i < relics.size(); i++) {
            if (!hasRelicAlready(player, relics.get(i))) {
                possible.add(i);
            }
        }

        if (!possible.isEmpty()) {
            int relicIdx = possible.get(level.random.nextInt(possible.size()));
            player.sendSystemMessage(Component.translatable("botaniamisc.diceRoll", relicIdx + 1)
                    .withStyle(ChatFormatting.DARK_GREEN));
            ItemStack toGive = new ItemStack(relics.get(relicIdx));
            bindToUUID(toGive, player.getUUID());
            return InteractionResultHolder.consume(toGive);
        }

        int roll = level.random.nextInt(6) + 1;
        ResourceLocation tableId = new ResourceLocation("botania", "dice/roll_" + roll);
        LootTable table = level.getServer().getLootData().getLootTable(tableId);
        LootParams params = new LootParams.Builder((ServerLevel) level)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.GIFT);
        List<ItemStack> generated = table.getRandomItems(params);
        for (ItemStack drop : generated) {
            player.getInventory().placeItemBackInInventory(drop);
        }
        String langKey = generated.isEmpty() ? "botaniamisc.dudDiceRoll" : "botaniamisc.diceRoll";
        player.sendSystemMessage(Component.translatable(langKey, roll).withStyle(ChatFormatting.DARK_GREEN));
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    private static boolean hasRelicAlready(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRightPlayer(ItemStack stack, Player player) {
        var uuid = getSoulbindUUID(stack);
        return uuid == null || uuid.equals(player.getUUID());
    }
}
