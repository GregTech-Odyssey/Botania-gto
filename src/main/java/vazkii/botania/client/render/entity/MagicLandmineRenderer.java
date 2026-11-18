package vazkii.botania.client.render.entity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import vazkii.botania.client.core.handler.ClientTickHandler;
import vazkii.botania.common.entity.MagicLandmineEntity;

/** 魔法地雷：在地面绘制一层紫色半透明方块，淡入淡出。 */
public class MagicLandmineRenderer extends EntityRenderer<MagicLandmineEntity> {

    public MagicLandmineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(@NotNull MagicLandmineEntity entity, float entityYaw, float partialTicks, @NotNull PoseStack ms,
                       @NotNull MultiBufferSource buffers, int light) {
        super.render(entity, entityYaw, partialTicks, ms, buffers, light);
        AABB aabb = entity.getBoundingBox().move(entity.position().scale(-1));
        float glow = (float) (Math.sin(ClientTickHandler.total() / 20) + 1) * 0.2F + 0.6F;
        int red = (int) (105 * glow);
        int green = (int) (25 * glow);
        int blue = (int) (145 * glow);
        int alpha = 32;
        if (entity.tickCount < 8) {
            alpha = (int) (alpha * Math.min((entity.tickCount + partialTicks) / 8F, 1F));
        } else if (entity.tickCount > 47) {
            alpha = (int) (alpha * Math.min(1F - (entity.tickCount - 47 + partialTicks) / 8F, 1F));
        }
        ms.pushPose();
        ms.translate(aabb.minX, aabb.minY + 0.001, aabb.minZ);
        float f = 1F / 16F;
        float x = (float) (aabb.getXsize() - f);
        float z = (float) (aabb.getZsize() - f);
        VertexConsumer buffer = buffers.getBuffer(RenderType.debugFilledBox());
        Matrix4f mat = ms.last().pose();
        buffer.vertex(mat, f, 0, z).color(red, green, blue, alpha).endVertex();
        buffer.vertex(mat, x, 0, z).color(red, green, blue, alpha).endVertex();
        buffer.vertex(mat, x, 0, f).color(red, green, blue, alpha).endVertex();
        buffer.vertex(mat, f, 0, f).color(red, green, blue, alpha).endVertex();
        ms.popPose();
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull MagicLandmineEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
