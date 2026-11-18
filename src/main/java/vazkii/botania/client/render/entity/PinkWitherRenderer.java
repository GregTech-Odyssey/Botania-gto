package vazkii.botania.client.render.entity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;
import vazkii.botania.common.entity.PinkWitherEntity;

/** 粉色凋灵：复用原版凋灵渲染器，只替换贴图。 */
public class PinkWitherRenderer extends EntityRenderer<PinkWitherEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("botania", "textures/model/wither_pink.png");

    private final WitherBossRenderer delegate;

    public PinkWitherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.delegate = new WitherBossRenderer(ctx);
    }

    @Override
    public void render(@NotNull PinkWitherEntity entity, float entityYaw, float partialTicks, @NotNull PoseStack ms,
                       @NotNull MultiBufferSource buffers, int light) {
        this.delegate.render(entity, entityYaw, partialTicks, ms, buffers, light);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull PinkWitherEntity entity) {
        return TEXTURE;
    }
}
