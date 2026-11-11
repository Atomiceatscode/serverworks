package dev.serverworks.machine;

import dev.serverworks.rack.Cell;
import dev.serverworks.rack.PartKind;
import dev.serverworks.rack.Platform;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Decides which server software a rack runs. There is one of these blocks per platform. */
public final class PlatformModuleBlock extends RackComponentBlock {
	private final Platform platform;

	public PlatformModuleBlock(Platform platform, BlockBehaviour.Properties properties) {
		super(PartKind.PLATFORM, properties);
		this.platform = platform;
	}

	public Platform platform() {
		return platform;
	}

	@Override
	public Cell cell(BlockState state) {
		return Cell.platform(platform);
	}
}
