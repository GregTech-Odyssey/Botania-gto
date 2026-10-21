/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.client.fx;

import com.mojang.serialization.Codec;
import com.suried.gtorenders.effect.MoteLook;
import com.suried.gtorenders.effect.MoteParticles;
import com.suried.gtorenders.effect.MoteSprite;


import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;

import org.jetbrains.annotations.NotNull;

public class WispParticleType extends ParticleType<WispParticleData> {
	public WispParticleType() {
		super(false, WispParticleData.DESERIALIZER);
	}

	@NotNull
	@Override
	public Codec<WispParticleData> codec() {
		return WispParticleData.CODEC;
	}

	public static class Factory implements ParticleProvider<WispParticleData> {
		private final MoteLook look;

		public Factory(SpriteSet sprite) {
			this.look = MoteLook.sprite(MoteSprite.of(sprite));
		}

		@Override
		public Particle createParticle(WispParticleData data, ClientLevel world, double x, double y, double z, double mx, double my, double mz) {
			var random = world.random;
			float size = (random.nextFloat() * 0.5F + 0.5F) * 2.0F * data.size;
			float life = (float) (28D / (random.nextDouble() * 0.3D + 0.7D) * data.maxAgeMul);
			int rgb = rgb(data.r) << 16 | rgb(data.g) << 8 | rgb(data.b);
			MoteParticles.spawn(x, y, z, mx, my, mz, data.gravity == 0 ? 0.98F : 1, data.gravity, life, size, MoteParticles.PULSE, rgb, 0.375F,
					data.depthTest ? 0 : MoteParticles.THROUGH_WALLS, look);
			return null;
		}
	}

	static int rgb(float channel) {
		return Math.max(0, Math.min(255, (int) (channel * 255)));
	}
}
