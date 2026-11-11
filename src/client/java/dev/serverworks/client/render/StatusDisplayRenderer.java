package dev.serverworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.serverworks.machine.DisplayLine;
import dev.serverworks.machine.RackBlock;
import dev.serverworks.machine.RackComponentBlock;
import dev.serverworks.machine.StatusDisplayBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Draws a Status Display's three lines on its screen, lit as if the screen glowed. */
public final class StatusDisplayRenderer implements BlockEntityRenderer<StatusDisplayBlockEntity, StatusDisplayRenderer.State> {
	/** Text scale: blocks per font pixel. A sign uses about 0.0104. */
	private static final float SCALE = 0.016f;
	private static final float MIN_SCALE = 0.008f;
	/** The screen area on the texture, in blocks. */
	private static final float SCREEN_WIDTH = 13f / 16f;
	private static final float SCREEN_TOP = 7f / 16f - 0.5f / 16f;
	private static final float LINE_SPACING = 3.6f / 16f;

	private final Font font;

	public StatusDisplayRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	public static final class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		boolean formed;
		final List<FormattedCharSequence> lines = new ArrayList<>();
		final List<Integer> colors = new ArrayList<>();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(StatusDisplayBlockEntity display, State state, float partialTicks, Vec3 camera,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(display, state, partialTicks, camera, breakProgress);
		var blockState = display.getBlockState();
		state.formed = blockState.getBlock() instanceof RackBlock && blockState.getValue(RackBlock.FORMED);
		state.facing = blockState.hasProperty(RackComponentBlock.FACING) ? blockState.getValue(RackComponentBlock.FACING)
				: Direction.NORTH;
		state.lines.clear();
		state.colors.clear();
		for (DisplayLine line : display.lines()) {
			Component text = line.key().isEmpty() ? Component.empty()
					: Component.translatable(line.key(), line.args().toArray());
			state.lines.add(text.getVisualOrderText());
			state.colors.add(0xFF000000 | line.color());
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.formed || state.lines.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.5f, 0.5f);
		switch (state.facing) {
			case UP -> poseStack.rotateDegrees(Axis.XP, -90);
			case DOWN -> poseStack.rotateDegrees(Axis.XP, 90);
			default -> poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot());
		}
		// The screen is set one pixel back inside the display's bezel.
		poseStack.translate(0f, 0f, 0.5f - 1f / 16f + 0.003f);
		for (int i = 0; i < state.lines.size(); i++) {
			FormattedCharSequence line = state.lines.get(i);
			int width = font.width(line);
			if (width == 0) {
				continue;
			}
			float scale = Math.max(MIN_SCALE, Math.min(SCALE, SCREEN_WIDTH / width));
			poseStack.pushPose();
			poseStack.translate(0f, SCREEN_TOP - (i + 0.5f) * LINE_SPACING, 0f);
			poseStack.scale(scale, -scale, scale);
			collector.submitText(poseStack, -width / 2f, -4f, line, false, Font.DisplayMode.POLYGON_OFFSET,
					LightCoordsUtil.FULL_BRIGHT, state.colors.get(i), 0, 0);
			poseStack.popPose();
		}
		poseStack.popPose();
	}
}
