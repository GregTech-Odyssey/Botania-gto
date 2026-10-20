package vazkii.botania.forge.integration.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaReceiver;

import java.util.List;

import static vazkii.botania.common.lib.ResourceLocationHelper.prefix;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.p2p.CapabilityP2PTunnelPart;
import appeng.parts.p2p.P2PModels;

public class ManaP2PTunnelPart extends CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver> {
	private static final P2PModels MODELS = new P2PModels(prefix("part/mana_p2p_tunnel"));

	@PartModels
	public static List<IPartModel> getModels() {
		return MODELS.getModels();
	}

	public ManaP2PTunnelPart(IPartItem<?> partItem) {
		super(partItem, BotaniaForgeCapabilities.MANA_RECEIVER);
		inputHandler = new Input();
		outputHandler = new Output();
		emptyHandler = new Empty();
	}

	@Override
	public IPartModel getStaticModels() {
		return MODELS.getModel(isPowered(), isActive());
	}

	private abstract class Located implements ManaReceiver {
		@Override
		public Level getManaReceiverLevel() {
			return getLevel();
		}

		@Override
		public BlockPos getManaReceiverPos() {
			return getBlockEntity().getBlockPos();
		}

		@Override
		public boolean canReceiveManaFromBursts() {
			return true;
		}
	}

	private class Input extends Located {
		@Override
		public int getCurrentMana() {
			return 0;
		}

		@Override
		public boolean isFull() {
			for (var output : getOutputs()) {
				try (var guard = output.getAdjacentCapability()) {
					if (!guard.get().isFull()) {
						return false;
					}
				}
			}
			return true;
		}

		@Override
		public void receiveMana(int mana) {
			receiveManaLong(mana);
		}

		@Override
		public void receiveManaLong(long mana) {
			var outputs = getOutputs();
			if (mana <= 0 || outputs.isEmpty()) {
				return;
			}
			long remaining = mana;
			int left = outputs.size();
			for (var output : outputs) {
				long share = remaining / left--;
				try (var guard = output.getAdjacentCapability()) {
					remaining -= ManaAccess.insert(guard.get(), left == 0 ? remaining : share, false);
				}
				if (remaining <= 0) {
					break;
				}
			}
			long moved = mana - remaining;
			if (moved > 0) {
				deductTransportCost(moved, ManaKeyType.TYPE);
			}
		}
	}

	private class Output extends Located {
		@Override
		public int getCurrentMana() {
			try (var guard = getInputCapability()) {
				return guard.get().getCurrentMana();
			}
		}

		@Override
		public long getCurrentManaLong() {
			try (var guard = getInputCapability()) {
				return guard.get().getCurrentManaLong();
			}
		}

		@Override
		public boolean isFull() {
			try (var guard = getInputCapability()) {
				return guard.get().isFull();
			}
		}

		@Override
		public void receiveMana(int mana) {
			receiveManaLong(mana);
		}

		@Override
		public void receiveManaLong(long mana) {
			try (var guard = getInputCapability()) {
				long moved = mana > 0 ? ManaAccess.insert(guard.get(), mana, false) : ManaAccess.extract(guard.get(), -mana, false);
				if (moved > 0) {
					deductTransportCost(moved, ManaKeyType.TYPE);
				}
			}
		}
	}

	private class Empty extends Located {
		@Override
		public int getCurrentMana() {
			return 0;
		}

		@Override
		public boolean isFull() {
			return true;
		}

		@Override
		public void receiveMana(int mana) {}

		@Override
		public boolean canReceiveManaFromBursts() {
			return false;
		}
	}
}
