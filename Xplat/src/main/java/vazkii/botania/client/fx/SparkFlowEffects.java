/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.client.fx;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.common.entity.SparkBaseEntity;
import vazkii.botania.common.helper.ColorHelper;
import vazkii.botania.common.helper.VecHelper;

// Client side of the spark flow sync: the server only reports which links are carrying mana,
// and the wisps along those links are spawned here at a fixed cadence.
public final class SparkFlowEffects {
	private static final int PERIOD = 20;
	private static final int MAX_MISSES = 40;
	private static final double CULL_SQ = 33 * 33;

	private static final Int2ObjectOpenHashMap<Flows> FLOWS = new Int2ObjectOpenHashMap<>();
	private static ClientLevel lastLevel;
	private static int ticks;

	private static final class Flows {
		int[] outgoing;
		int[] incoming;
		int misses;

		Flows(int[] outgoing, int[] incoming) {
			this.outgoing = outgoing;
			this.incoming = incoming;
		}
	}

	private SparkFlowEffects() {}

	public static void update(int sparkId, int[] outgoing, int[] incoming) {
		if (outgoing.length == 0 && incoming.length == 0) {
			FLOWS.remove(sparkId);
			return;
		}
		var flows = FLOWS.get(sparkId);
		if (flows == null) {
			FLOWS.put(sparkId, new Flows(outgoing, incoming));
		} else {
			flows.outgoing = outgoing;
			flows.incoming = incoming;
			flows.misses = 0;
		}
	}

	public static int activeSparks() {
		return FLOWS.size();
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			FLOWS.clear();
			lastLevel = level;
		}
		if (level == null || FLOWS.isEmpty() || mc.isPaused()) {
			return;
		}
		int now = ++ticks;
		Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
		var it = FLOWS.int2ObjectEntrySet().fastIterator();
		while (it.hasNext()) {
			Int2ObjectMap.Entry<Flows> entry = it.next();
			Flows flows = entry.getValue();
			int sparkId = entry.getIntKey();
			if (!(level.getEntity(sparkId) instanceof SparkBaseEntity spark)) {
				if (++flows.misses > MAX_MISSES) {
					it.remove();
				}
				continue;
			}
			flows.misses = 0;
			int color = ColorHelper.getColorValue(spark.getNetwork());
			for (int targetId : flows.outgoing) {
				if (Math.floorMod(now + sparkId + targetId, PERIOD) == 0 && spark.distanceToSqr(camera) <= CULL_SQ) {
					var target = level.getEntity(targetId);
					if (target != null) {
						spawnWisp(level, spark, target, color);
					}
				}
			}
			for (int sourceId : flows.incoming) {
				if (Math.floorMod(now + sparkId + sourceId, PERIOD) == 0) {
					var source = level.getEntity(sourceId);
					if (source != null && source.distanceToSqr(camera) <= CULL_SQ) {
						spawnWisp(level, source, spark, color);
					}
				}
			}
		}
	}

	public static void beam(Level world, Entity e1, Entity e2) {
		Vec3 orig = new Vec3(e1.getX(), e1.getY() + 0.25, e1.getZ());
		Vec3 end = new Vec3(e2.getX(), e2.getY() + 0.25, e2.getZ());
		Vec3 diff = end.subtract(orig);
		Vec3 movement = diff.normalize().scale(0.1);
		int iters = (int) (diff.length() / movement.length());
		float huePer = 1F / iters;
		float hueSum = (float) Math.random();

		Vec3 currentPos = orig;
		for (int i = 0; i < iters; i++) {
			float hue = i * huePer + hueSum;
			int color = Mth.hsvToRgb(Mth.frac(hue), 1F, 1F);
			float r = Math.min(1F, (color >> 16 & 0xFF) / 255F + 0.4F);
			float g = Math.min(1F, (color >> 8 & 0xFF) / 255F + 0.4F);
			float b = Math.min(1F, (color & 0xFF) / 255F + 0.4F);

			SparkleParticleData data = SparkleParticleData.noClip(1, r, g, b, 12);
			world.addAlwaysVisibleParticle(data, true, currentPos.x, currentPos.y, currentPos.z, 0, 0, 0);
			currentPos = currentPos.add(movement);
		}
	}

	private static void spawnWisp(ClientLevel level, Entity from, Entity to, int color) {
		double rc = 0.45;
		Vec3 thisVec = VecHelper.fromEntityCenter(from).add((Math.random() - 0.5) * rc, (Math.random() - 0.5) * rc, (Math.random() - 0.5) * rc);
		Vec3 receiverVec = VecHelper.fromEntityCenter(to).add((Math.random() - 0.5) * rc, (Math.random() - 0.5) * rc, (Math.random() - 0.5) * rc);

		Vec3 motion = receiverVec.subtract(thisVec).scale(0.04F);
		float r = ((color >> 16) & 0xFF) / 255.0F;
		float g = ((color >> 8) & 0xFF) / 255.0F;
		float b = (color & 0xFF) / 255.0F;
		if (level.random.nextFloat() < 0.25) {
			r += 0.2F * (float) level.random.nextGaussian();
			g += 0.2F * (float) level.random.nextGaussian();
			b += 0.2F * (float) level.random.nextGaussian();
		}
		float size = 0.125F + 0.125F * (float) Math.random();

		WispParticleData data = WispParticleData.wisp(size, r, g, b).withNoClip(true);
		level.addAlwaysVisibleParticle(data, thisVec.x, thisVec.y, thisVec.z, motion.x, motion.y, motion.z);
	}
}
