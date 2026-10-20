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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import vazkii.botania.client.fx.SparkFlowEffects;
import vazkii.botania.network.BotaniaPacket;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

public record SparkFlowPacket(int sparkId, int[] outgoing, int[] incoming) implements BotaniaPacket {

	public static final ResourceLocation ID = prefix("sf");
	private static final int MAX_LINKS = 4096;

	@Override
	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(sparkId);
		writeIds(buf, outgoing);
		writeIds(buf, incoming);
	}

	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}

	public static SparkFlowPacket decode(FriendlyByteBuf buf) {
		return new SparkFlowPacket(buf.readVarInt(), readIds(buf), readIds(buf));
	}

	private static void writeIds(FriendlyByteBuf buf, int[] ids) {
		buf.writeVarInt(ids.length);
		for (int id : ids) {
			buf.writeVarInt(id);
		}
	}

	private static int[] readIds(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		if (n < 0 || n > MAX_LINKS) {
			throw new IllegalArgumentException("Too many spark links: " + n);
		}
		int[] ids = new int[n];
		for (int i = 0; i < n; i++) {
			ids[i] = buf.readVarInt();
		}
		return ids;
	}

	public static class Handler {
		public static void handle(SparkFlowPacket packet) {
			Minecraft.getInstance().execute(() -> SparkFlowEffects.update(packet.sparkId(), packet.outgoing(), packet.incoming()));
		}
	}
}
