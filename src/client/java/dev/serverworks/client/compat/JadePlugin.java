package dev.serverworks.client.compat;

import dev.serverworks.compat.JadeData;
import dev.serverworks.host.ServerInstance.State;
import dev.serverworks.machine.DriveBayBlock;
import dev.serverworks.machine.DriveBayBlockEntity;
import dev.serverworks.machine.RackBlock;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.machine.StatusDisplayBlockEntity;
import dev.serverworks.rack.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * Jade tooltips for rack blocks: whether the rack is formed, its platform, and what its
 * server is doing. Loaded by Jade only when Jade is installed.
 */
public final class JadePlugin implements IWailaPlugin {
	@Override
	public void register(IWailaCommonRegistration registration) {
		JadeData data = new JadeData();
		registration.registerBlockDataProvider(data, ServerCoreBlockEntity.class);
		registration.registerBlockDataProvider(data, DriveBayBlockEntity.class);
		registration.registerBlockDataProvider(data, StatusDisplayBlockEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(new Tooltip(), RackBlock.class);
	}

	private static final class Tooltip implements IBlockComponentProvider {
		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			var state = accessor.getBlockState();
			if (!state.getValue(RackBlock.FORMED)) {
				tooltip.add(Component.translatable("serverworks.jade.not_formed").withStyle(ChatFormatting.GRAY));
				return;
			}
			if (state.getBlock() instanceof DriveBayBlock) {
				tooltip.add(Component.translatable("serverworks.jade.disks", state.getValue(DriveBayBlock.DISKS), 8));
			}
			CompoundTag data = accessor.getServerData();
			if (!data.contains("state")) {
				return;
			}
			Platform platform = Platform.byId(data.getStringOr("platform", ""));
			if (platform != null) {
				tooltip.add(Component.translatable("serverworks.jade.platform", Component.translatable(platform.translationKey())));
			}
			State serverState = State.valueOf(data.getStringOr("state", "IDLE"));
			Component line = data.getBooleanOr("asleep", false) && serverState.canStart()
					? Component.translatable("serverworks.instance.state.sleeping")
					: serverState == State.DOWNLOADING
					? Component.translatable(serverState.translationKey(), data.getIntOr("progress", 0))
					: Component.translatable(serverState.translationKey());
			ChatFormatting colour = switch (serverState) {
				case RUNNING -> ChatFormatting.GREEN;
				case CRASHED, FAILED -> ChatFormatting.RED;
				case IDLE, STOPPED -> ChatFormatting.GRAY;
				default -> ChatFormatting.AQUA;
			};
			tooltip.add(line.copy().withStyle(colour));
			int players = data.getIntOr("players", -1);
			if (serverState == State.RUNNING && players >= 0) {
				tooltip.add(Component.translatable("serverworks.display.players", players, data.getIntOr("max", 0)));
			}
		}

		@Override
		public Identifier getUid() {
			return JadeData.UID;
		}
	}

	static String lower(String text) {
		return text.toLowerCase(Locale.ROOT);
	}
}
