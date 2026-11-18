package vazkii.botania.common.item.relic;

import com.gtocore.utils.PlayerNameUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 遗物基类：首次进入物品栏时与持有者灵魂绑定。
 * 其他玩家持有时每 10 tick 受到 2 点魔法伤害，tooltip 显示绑定状态与遗物描述。
 */
public class RelicItem extends Item {

    public static final String TAG_SOULBIND_UUID = "soulbindUUID";

    public RelicItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player) {
            tickBinding(stack, player);
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        addRelicTooltip(this, stack, level, tooltip);
    }

    public static void tickBinding(ItemStack stack, Player player) {
        UUID soulbind = getSoulbindUUID(stack);
        if (soulbind == null) {
            bindToUUID(stack, player.getUUID());
        } else if (!soulbind.equals(player.getUUID()) && player.tickCount % 10 == 0) {
            player.hurt(player.damageSources().magic(), 2F);
        }
    }

    public static void addRelicTooltip(Item item, ItemStack stack, @Nullable Level level, List<Component> tooltip) {
        UUID soulbind = getSoulbindUUID(stack);
        if (soulbind == null) {
            tooltip.add(Component.translatable("botaniamisc.relicUnbound"));
        } else {
            tooltip.add(Component.translatable("botaniamisc.relicSoulbound",
                    PlayerNameUtils.getLastKnownName(level, soulbind)));
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String key = "item." + id.getNamespace() + "." + id.getPath() + ".desc";
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    public static void bindToUUID(ItemStack stack, UUID uuid) {
        stack.getOrCreateTag().putString(TAG_SOULBIND_UUID, uuid.toString());
    }

    @Nullable
    public static UUID getSoulbindUUID(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG_SOULBIND_UUID, net.minecraft.nbt.Tag.TAG_STRING)) {
            return null;
        }
        try {
            return UUID.fromString(stack.getTag().getString(TAG_SOULBIND_UUID));
        } catch (IllegalArgumentException exception) {
            stack.getTag().remove(TAG_SOULBIND_UUID);
            return null;
        }
    }
}
