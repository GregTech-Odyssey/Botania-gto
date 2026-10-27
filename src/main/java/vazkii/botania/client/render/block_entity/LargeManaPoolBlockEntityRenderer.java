/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.suried.gtorenders.geometry.SpriteQuads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;

import vazkii.botania.api.mana.PoolOverlayProvider;
import vazkii.botania.client.core.handler.ClientTickHandler;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.common.block.block_entity.mana.LargeManaPoolBlockEntity;
import vazkii.botania.common.block.mana.LargeManaPoolBlock;

import java.util.Objects;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

public class LargeManaPoolBlockEntityRenderer implements BlockEntityRenderer<LargeManaPoolBlockEntity> {
	private final TextureAtlasSprite waterSprite;

	public LargeManaPoolBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
		this.waterSprite = Objects.requireNonNull(
				Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(prefix("block/mana_water")));
	}

	@Override
	public void render(@NotNull LargeManaPoolBlockEntity pool, float partialTicks, @NotNull PoseStack ms, @NotNull MultiBufferSource buffers, int light, int overlay) {
		if (!pool.isCore() || pool.getLevel() == null) {
			return;
		}
		BlockPos core = pool.getBlockPos();
		if (pool.getLevel().getBlockState(core.below()).getBlock() instanceof PoolOverlayProvider provider) {
			TextureAtlasSprite icon = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(provider.getIcon(pool.getLevel(), core));
			float alpha = (float) ((Math.sin((ClientTickHandler.ticksInGame + partialTicks) / 20.0) + 1) * 0.3 + 0.2);
			renderLayer(pool, core, (float) (LargeManaPoolBlock.FLOOR / 16D + 0.0005), buffers.getBuffer(RenderHelper.ICON_OVERLAY), icon, alpha, ms, light);
		}
		float fill = pool.getFillFraction();
		if (fill <= 0) {
			return;
		}
		float height = (float) LargeManaPoolBlockEntity.surfaceHeight(fill);
		renderLayer(pool, core, height, buffers.getBuffer(RenderHelper.manaPoolWater()), waterSprite, 1, ms, light);
	}

	private static void renderLayer(LargeManaPoolBlockEntity pool, BlockPos core, float height, VertexConsumer buffer, TextureAtlasSprite sprite, float alpha, PoseStack ms, int light) {
		int argb = (int) (alpha * 255) << 24 | 0xFFFFFF;
		float y = height * 16;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockState state = pool.getLevel().getBlockState(core.offset(dx, 0, dz));
				if (!(state.getBlock() instanceof LargeManaPoolBlock)) {
					continue;
				}
				int rim = LargeManaPoolBlock.RIM;
				int x0 = state.getValue(LargeManaPoolBlock.WEST) ? 0 : rim;
				int x1 = state.getValue(LargeManaPoolBlock.EAST) ? 16 : 16 - rim;
				int z0 = state.getValue(LargeManaPoolBlock.NORTH) ? 0 : rim;
				int z1 = state.getValue(LargeManaPoolBlock.SOUTH) ? 16 : 16 - rim;
				SpriteQuads.face(buffer, ms.last().pose(), sprite, Direction.UP, dx * 16 + x0, y, dz * 16 + z0, dx * 16 + x1, y, dz * 16 + z1, argb, light);
			}
		}
	}

	@Override
	public boolean shouldRenderOffScreen(@NotNull LargeManaPoolBlockEntity pool) {
		return true;
	}
}
