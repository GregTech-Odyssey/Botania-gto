/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.network.clientbound;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.client.fx.SparkFlowEffects;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.helper.VecHelper;
import vazkii.botania.common.item.GrassSeedsItem;
import vazkii.botania.common.item.WandOfTheForestItem;
import vazkii.botania.common.proxy.Proxy;
import vazkii.botania.network.BotaniaPacket;
import vazkii.botania.network.EffectType;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

// Prefer using World.addBlockEvent/Block.eventReceived/TileEntity.receiveClientEvent where possible
// as those use less network bandwidth (~14 bytes), vs 26+ bytes here
public record BotaniaEffectPacket(EffectType type, double x, double y, double z, int... args) implements BotaniaPacket {

	public static final ResourceLocation ID = prefix("eff");
	private static final int MAX_VARIABLE_ARGS = 128;

	@Override
	public void encode(FriendlyByteBuf buf) {
		buf.writeByte(type().ordinal());
		buf.writeDouble(x());
		buf.writeDouble(y());
		buf.writeDouble(z());

		if (type().argCount != -1 && type().argCount != args().length) {
			throw new IllegalArgumentException("Argument count mismatch");
		}

		if (type().argCount == -1) {
			if (args().length > MAX_VARIABLE_ARGS) {
				throw new IllegalArgumentException("Too many variable arguments");
			}
			buf.writeVarInt(args().length);
		}
		for (int arg : args) {
			buf.writeVarInt(arg);
		}
	}

	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}

	public static BotaniaEffectPacket decode(FriendlyByteBuf buf) {
		EffectType type = EffectType.values()[buf.readByte()];
		double x = buf.readDouble();
		double y = buf.readDouble();
		double z = buf.readDouble();
		int argCount;
		if (type.argCount == -1) {
			argCount = buf.readVarInt();
			if (argCount > MAX_VARIABLE_ARGS) {
				throw new IllegalArgumentException("Too many variable arguments");
			}
		} else {
			argCount = type.argCount;
		}
		int[] args = new int[argCount];

		for (int i = 0; i < args.length; i++) {
			args[i] = buf.readVarInt();
		}

		return new BotaniaEffectPacket(type, x, y, z, args);
	}

	public static class Handler {
		public static void handle(BotaniaEffectPacket packet) {
			var type = packet.type();
			var x = packet.x();
			var y = packet.y();
			var z = packet.z();
			var args = packet.args();
			Minecraft mc = Minecraft.getInstance();
			// Lambda trips verifier on forge
			mc.execute(new Runnable() {
				@Override
				public void run() {
					Level world = mc.level;
					switch (type) {
						case ITEM_SMOKE -> {
							Entity item = world.getEntity(args[0]);
							if (item == null) {
								return;
							}

							int p = args[1];

							for (int i = 0; i < p; i++) {
								double m = 0.01;
								double d0 = item.level().random.nextGaussian() * m;
								double d1 = item.level().random.nextGaussian() * m;
								double d2 = item.level().random.nextGaussian() * m;
								double d3 = 10.0D;
								item.level().addParticle(ParticleTypes.POOF,
										x + item.level().random.nextFloat() * item.getBbWidth() * 2.0F - item.getBbWidth() - d0 * d3, y + item.level().random.nextFloat() * item.getBbHeight() - d1 * d3,
										z + item.level().random.nextFloat() * item.getBbWidth() * 2.0F - item.getBbWidth() - d2 * d3, d0, d1, d2);
							}
						}
						case SPARK_NET_INDICATOR -> {
							Entity e1 = world.getEntity(args[0]);
							if (e1 == null) {
								return;
							}
							for (int t = 1; t < args.length; t++) {
								Entity e2 = world.getEntity(args[t]);
								if (e2 != null) {
									SparkFlowEffects.beam(world, e1, e2);
								}
							}
						}
						case ENCHANTER_DESTROY -> {
							for (int i = 0; i < 50; i++) {
								float red = (float) Math.random();
								float green = (float) Math.random();
								float blue = (float) Math.random();
								WispParticleData data = WispParticleData.wisp((float) Math.random() * 0.15F + 0.15F, red, green, blue);
								world.addParticle(data, x, y, z, (float) (Math.random() - 0.5F) * 0.25F, (float) (Math.random() - 0.5F) * 0.25F, (float) (Math.random() - 0.5F) * 0.25F);
							}
						}
						case FLUGEL_EFFECT -> {
							Entity entity = world.getEntity(args[0]);
							if (entity != null) {
								for (int i = 0; i < 15; i++) {
									float x1 = (float) (entity.getX() + Math.random());
									float y1 = (float) (entity.getY() + Math.random());
									float z1 = (float) (entity.getZ() + Math.random());
									WispParticleData data = WispParticleData.wisp((float) Math.random(), (float) Math.random(), (float) Math.random(), (float) Math.random(), 1);
									world.addParticle(data, x1, y1, z1, 0, -(-0.3F + (float) Math.random() * 0.2F), 0);
								}
							}
						}
						case PARTICLE_BEAM -> {
							WandOfTheForestItem.doParticleBeam(world,
									new Vec3(x, y, z),
									new Vec3(args[0] + 0.5, args[1] + 0.5, args[2] + 0.5));
						}
						case DIVA_EFFECT -> {
							Entity target = world.getEntity(args[0]);
							if (target == null) {
								break;
							}

							double x1 = target.getX();
							double y1 = target.getY();
							double z1 = target.getZ();

							SparkleParticleData data = SparkleParticleData.sparkle(1F, 1F, 1F, 0.25F, 3);
							for (int i = 0; i < 50; i++) {
								world.addParticle(data, x1 + Math.random() * target.getBbWidth(), y1 + Math.random() * target.getBbHeight(), z1 + Math.random() * target.getBbWidth(), 0, 0, 0);
							}
						}
						case HALO_CRAFT -> {
							Entity target = world.getEntity(args[0]);
							if (target != null) {
								Vec3 lookVec3 = target.getLookAngle();
								Vec3 centerVector = VecHelper.fromEntityCenter(target).add(lookVec3.x * 3, 1.3, lookVec3.z * 3);
								float m = 0.1F;
								for (int i = 0; i < 4; i++) {
									WispParticleData data = WispParticleData.wisp(0.2F + 0.2F * (float) Math.random(), 1F, 0F, 1F);
									target.level().addParticle(data, centerVector.x, centerVector.y, centerVector.z, ((float) Math.random() - 0.5F) * m, ((float) Math.random() - 0.5F) * m, ((float) Math.random() - 0.5F) * m);
								}
							}

						}
						case AVATAR_TORNADO_JUMP -> {
							Entity p = world.getEntity(args[0]);
							if (p != null) {
								for (int i = 0; i < 20; i++) {
									for (int j = 0; j < 5; j++) {
										WispParticleData data = WispParticleData.wisp(0.35F + (float) Math.random() * 0.1F, 0.25F, 0.25F, 0.25F);
										world.addParticle(data, p.getX(),
												p.getY() + i, p.getZ(),
												0.2F * (float) (Math.random() - 0.5),
												-0.01F * (float) Math.random(),
												0.2F * (float) (Math.random() - 0.5));
									}
								}
							}
						}
						case AVATAR_TORNADO_BOOST -> {
							Entity p = world.getEntity(args[0]);
							if (p != null) {
								Vec3 lookDir = p.getLookAngle();
								for (int i = 0; i < 20; i++) {
									for (int j = 0; j < 5; j++) {
										WispParticleData data = WispParticleData.wisp(0.35F + (float) Math.random() * 0.1F, 0.25F, 0.25F, 0.25F);
										world.addParticle(data, p.getX() + lookDir.x() * i,
												p.getY() + lookDir.y() * i,
												p.getZ() + lookDir.z() * i,
												0.2F * (float) (Math.random() - 0.5) * (Math.abs(lookDir.y()) + Math.abs(lookDir.z())) + -0.01F * (float) Math.random() * lookDir.x(),
												0.2F * (float) (Math.random() - 0.5) * (Math.abs(lookDir.x()) + Math.abs(lookDir.z())) + -0.01F * (float) Math.random() * lookDir.y(),
												0.2F * (float) (Math.random() - 0.5) * (Math.abs(lookDir.y()) + Math.abs(lookDir.x())) + -0.01F * (float) Math.random() * lookDir.z());
									}
								}
							}
						}
						case THUNDERCALLER_EFFECT -> {
							Vec3 source = new Vec3(x, y, z);
							for (int id : args) {
								var entity = world.getEntity(id);
								if (entity == null) {
									break;
								}
								var entityPos = VecHelper.fromEntityCenter(entity);
								Proxy.INSTANCE.lightningFX(world, source, entityPos, 1, 0x0179C4, 0xAADFFF);
								source = entityPos;
							}
						}
						case GRASS_SEED_PARTICLES -> {
							int color = args[0];
							GrassSeedsItem.spawnParticles(world, BlockPos.containing(x, y, z),
									GrassSeedsItem.extractR(color), GrassSeedsItem.extractG(color), GrassSeedsItem.extractB(color));
						}
					}
				}
			});
		}
	}

}
