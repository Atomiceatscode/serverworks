package dev.serverworks.item;

import dev.serverworks.machine.RackBlock;
import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.machine.RackCores;
import dev.serverworks.machine.ServerCoreBlockEntity;
import dev.serverworks.rack.RackScan;
import dev.serverworks.rack.RackStructure;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rack builder's tool. Use it on any rack block to learn what the rack still needs:
 * it names the first problem and outlines every block still missing or misplaced for a few
 * seconds. Sneak-use it on a component to turn it.
 */
public final class WrenchItem extends Item {
	private static final DustParticleOptions OK_MARKER = new DustParticleOptions(0x40E080, 1.0F);

	public WrenchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		BlockPos pos = context.getClickedPos();
		BlockState state = context.getLevel().getBlockState(pos);
		if (!(state.getBlock() instanceof RackBlock)) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();

		if (context.isSecondaryUseActive() && state.getBlock() instanceof RackComponentBlock) {
			level.setBlock(pos, state.cycle(RackComponentBlock.FACING), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
			return InteractionResult.SUCCESS;
		}

		ServerCoreBlockEntity core = level.getBlockEntity(pos) instanceof ServerCoreBlockEntity here ? here
				: RackCores.nearest(level, pos);
		if (core == null) {
			message(player, Component.translatable("serverworks.wrench.no_core").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		core.rescan();
		RackScan scan = core.lastScan();
		if (scan == null) {
			return InteractionResult.SUCCESS;
		}
		if (scan.formed()) {
			RackStructure rack = scan.structure();
			message(player, Component.translatable("serverworks.wrench.formed",
					rack.sizeX(), rack.sizeY(), rack.sizeZ(),
					Component.translatable(rack.platform().translationKey())).withStyle(ChatFormatting.GREEN));
			outline(level, core.getBlockPos(), OK_MARKER);
		} else {
			message(player, problemText(scan).withStyle(ChatFormatting.RED));
			if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
				BlockPos corePos = core.getBlockPos();
				net.minecraft.core.Direction facing = level.getBlockState(corePos).getValue(RackComponentBlock.FACING);
				dev.serverworks.rack.Pos inward = new dev.serverworks.rack.Pos(-facing.getStepX(), -facing.getStepY(),
						-facing.getStepZ());
				java.util.List<dev.serverworks.rack.RackPreview.Hint> hints = dev.serverworks.rack.RackPreview.hints(
						core.cellView(), new dev.serverworks.rack.Pos(corePos.getX(), corePos.getY(), corePos.getZ()), inward);
				dev.serverworks.machine.WrenchPreviews.show(serverPlayer, level, hints);
			}
		}
		level.playSound(null, pos, SoundEvents.SPYGLASS_USE, SoundSource.BLOCKS, 0.8F, 1.4F);
		return InteractionResult.SUCCESS;
	}

	/** The problem as a sentence, naming the block to fix when there is one. */
	public static net.minecraft.network.chat.MutableComponent problemText(RackScan scan) {
		if (scan.problemPos() == null) {
			return Component.translatable(scan.problem().translationKey());
		}
		return Component.translatable(scan.problem().translationKey() + ".at",
				scan.problemPos().x(), scan.problemPos().y(), scan.problemPos().z());
	}

	private static void message(Player player, Component text) {
		if (player != null) {
			player.sendOverlayMessage(text);
		}
	}

	/** Traces the twelve edges of a block with particles so it stands out in a busy build. */
	private static void outline(ServerLevel level, BlockPos pos, DustParticleOptions particle) {
		double x = pos.getX();
		double y = pos.getY();
		double z = pos.getZ();
		for (int i = 0; i <= 4; i++) {
			double t = i / 4.0;
			for (int a = 0; a <= 1; a++) {
				for (int b = 0; b <= 1; b++) {
					spawn(level, particle, x + t, y + a, z + b);
					spawn(level, particle, x + a, y + t, z + b);
					spawn(level, particle, x + a, y + b, z + t);
				}
			}
		}
	}

	private static void spawn(ServerLevel level, DustParticleOptions particle, double x, double y, double z) {
		level.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0);
	}
}
