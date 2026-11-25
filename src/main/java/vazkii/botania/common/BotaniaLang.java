package vazkii.botania.common;

import com.gtocore.data.lang.LangHandler;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * 复刻内容在注册时登记的中英文名，由 GTOCore 的语言生成器统一写出。
 */
public final class BotaniaLang {

    private static final Object2ObjectOpenHashMap<String, String[]> LANG = new Object2ObjectOpenHashMap<>();

    static {
        add("botania.ae2.mana", "魔力", "Mana");
        add("botania.ae2.cell_stored", "已存：%s / %s 魔力", "Stored: %s / %s Mana");
        add("item.botania.mana_cell_housing", "ME 魔力元件外壳", "ME Mana Cell Housing");
        add("item.botania.mana_storage_cell_1k", "1k ME 魔力存储元件", "1k ME Mana Storage Cell");
        add("item.botania.mana_storage_cell_4k", "4k ME 魔力存储元件", "4k ME Mana Storage Cell");
        add("item.botania.mana_storage_cell_16k", "16k ME 魔力存储元件", "16k ME Mana Storage Cell");
        add("item.botania.mana_storage_cell_64k", "64k ME 魔力存储元件", "64k ME Mana Storage Cell");
        add("item.botania.mana_storage_cell_256k", "256k ME 魔力存储元件", "256k ME Mana Storage Cell");
        add("item.botania.creative_mana_cell", "创造 ME 魔力存储元件", "Creative ME Mana Storage Cell");
        add("block.mythicbotany.rune_holder", "符文支托", "Rune Holder");
        add("block.mythicbotany.central_rune_holder", "大师符文支托", "Central Rune Holder");
        add("message.mythicbotany.mimir_unknown", "你已经尽力了，但似乎看不出其背后的深刻含义。据说从装有智慧之泉水的海姆达尔号角喝水可能会有所帮助。",
                "You try your best, but you just don't seem to see the deeper sense behind this. It is told that drinking from the well of Mimir might help.");
        add("message.mythicbotany.ritual_running", "仪式正在进行。", "The ritual is running.");
        add("message.mythicbotany.ritual_wrong_shape", "即使你深入研究过符文，但你的仪式似乎是错误的。",
                "This ritual looks wrong even to you who has studied the runes in depth.");
        add("message.mythicbotany.ritual_wrong_items", "仪式对您来说是十分熟悉的，您会感觉到，您有权执行这个仪式。但是祭品似乎有些问题。",
                "The ritual is known to you, and you feel you have the power to perform it. However, something seems to be wrong with the ingredients.");
        add("message.mythicbotany.ritual_no_mana", "仪式暂停：大师符文支托缺少魔力。", "The ritual is paused: the central rune holder needs mana.");
        LANG.put("itemGroup.botania.gto", new String[] { "植物魔法", "Botania" });
        LANG.put("entity.botania.doppleganger", new String[] { "盖亚守护者", "Gaia Guardian" });
        LANG.put("botaniamisc.gaiaRitualPylons", new String[] { "需要 4 个盖亚水晶围绕召唤点", "Requires 4 Gaia Pylons around the ritual site" });
    }

    private BotaniaLang() {}

    static void add(String key, String cn, String en) {
        LANG.put(key, new String[] { cn, en });
    }

    public static void init() {
        LANG.forEach((key, value) -> LangHandler.addCNEN(key, value[0], value[1]));
    }
}
