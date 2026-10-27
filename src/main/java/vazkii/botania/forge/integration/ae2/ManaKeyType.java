package vazkii.botania.forge.integration.ae2;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.ApiStatus;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

public final class ManaKeyType extends AEKeyType {
	public static final ResourceLocation ID = prefix("mana");
	@Deprecated(since = "457.2610.7", forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "2027.6.1")
	public static final ResourceLocation LEGACY_ID = new ResourceLocation("appbot", "mana");
	public static final String NAME_KEY = "botania.ae2.mana";
	public static final int AMOUNT_PER_BYTE = 500;
	public static final ManaKeyType TYPE = new ManaKeyType();

	private ManaKeyType() {
		super(ID, ManaKey.class, Component.translatable(NAME_KEY));
	}

	@Override
	public AEKey readFromPacket(FriendlyByteBuf input) {
		return ManaKey.KEY;
	}

	@Override
	public AEKey loadKeyFromTag(CompoundTag tag) {
		return ManaKey.KEY;
	}

	@Override
	public int getAmountPerOperation() {
		return AMOUNT_PER_BYTE;
	}

	@Override
	public int getAmountPerByte() {
		return AMOUNT_PER_BYTE;
	}
}
