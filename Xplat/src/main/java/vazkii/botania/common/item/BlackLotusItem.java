/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import vazkii.botania.api.item.ManaDissolvable;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.common.helper.EntityHelper;

import java.util.List;

public class BlackLotusItem extends Item implements ManaDissolvable {

	private static final int MANA_PER = 8000;
	private static final int MANA_PER_T2 = 100000;

	public BlackLotusItem(Properties props) {
		super(props);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return super.isFoil(stack) || stack.is(BotaniaItems.blackerLotus);
	}

	@Override
	public void onDissolveTick(ManaPool pool, ItemEntity item) {
		if (pool.isFull() || pool.getCurrentMana() == 0) {
			return;
		}

		boolean t2 = item.getItem().is(BotaniaItems.blackerLotus);

		if (item.level().isClientSide) {
			BlockPos pos = pool.getManaReceiverPos();
			spawnDissolveParticles(item.level(), pos.getX(), pos.getY() + 0.5, pos.getZ());
			return;
		}

		pool.receiveMana(t2 ? MANA_PER_T2 : MANA_PER);
		EntityHelper.shrinkItem(item);
		item.playSound(BotaniaSounds.blackLotus, 1F, t2 ? 0.1F : 1F);
	}

	private static void spawnDissolveParticles(Level world, double x, double y, double z) {
		for (int i = 0; i < 50; i++) {
			float r = (float) Math.random() * 0.35F;
			float g = 0F;
			float b = (float) Math.random() * 0.35F;
			float s = 0.45F * (float) Math.random() * 0.25F;

			float m = 0.045F;
			float mx = ((float) Math.random() - 0.5F) * m;
			float my = (float) Math.random() * m;
			float mz = ((float) Math.random() - 0.5F) * m;

			WispParticleData data = WispParticleData.wisp(s, r, g, b);
			world.addParticle(data, x, y, z, mx, my, mz);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Level world, List<Component> list, TooltipFlag flags) {
		list.add(Component.translatable("botaniamisc.lotusDesc").withStyle(ChatFormatting.GRAY));
	}

}
