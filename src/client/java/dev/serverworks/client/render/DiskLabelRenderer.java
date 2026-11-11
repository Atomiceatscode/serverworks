package dev.serverworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import dev.serverworks.client.icon.IconCache;
import dev.serverworks.item.DiskLabel;
import dev.serverworks.registry.ModComponents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Draws a Mod Disk's project icon on both faces of the disk.
 *
 * <p>The disk body is an ordinary flat item model; this adds the one part that cannot be
 * baked ahead of time. Its argument is the icon's texture id, which is null until the
 * icon has downloaded, so an unloaded disk simply shows its printed label area.
 */
public final class DiskLabelRenderer implements SpecialModelRenderer<Identifier> {
	// The label window on the 16x16 disk texture, in model units (pixels / 16, y up).
	private static final float X0 = 3 / 16f;
	private static final float X1 = 13 / 16f;
	private static final float Y0 = 4 / 16f;
	private static final float Y1 = 14 / 16f;
	// Flat item models are one pixel thick around z = 0.5; sit just proud of each face.
	private static final float FRONT_Z = 8.5f / 16f + 0.002f;
	private static final float BACK_Z = 7.5f / 16f - 0.002f;

	@Override
	public void submit(@Nullable Identifier texture, PoseStack poseStack, SubmitNodeCollector collector,
			int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
		if (texture == null) {
			return;
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, buffer) -> {
			vertex(buffer, pose, X0, Y0, FRONT_Z, 0, 1, 1, lightCoords, overlayCoords);
			vertex(buffer, pose, X1, Y0, FRONT_Z, 1, 1, 1, lightCoords, overlayCoords);
			vertex(buffer, pose, X1, Y1, FRONT_Z, 1, 0, 1, lightCoords, overlayCoords);
			vertex(buffer, pose, X0, Y1, FRONT_Z, 0, 0, 1, lightCoords, overlayCoords);

			vertex(buffer, pose, X1, Y0, BACK_Z, 1, 1, -1, lightCoords, overlayCoords);
			vertex(buffer, pose, X0, Y0, BACK_Z, 0, 1, -1, lightCoords, overlayCoords);
			vertex(buffer, pose, X0, Y1, BACK_Z, 0, 0, -1, lightCoords, overlayCoords);
			vertex(buffer, pose, X1, Y1, BACK_Z, 1, 0, -1, lightCoords, overlayCoords);
		});
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
			float normalZ, int light, int overlay) {
		buffer.addVertex(pose, x, y, z)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, 0, 0, normalZ);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(X0, Y0, BACK_Z));
		output.accept(new Vector3f(X1, Y1, FRONT_Z));
	}

	@Override
	public @Nullable Identifier extractArgument(ItemStack stack) {
		DiskLabel label = stack.get(ModComponents.DISK_LABEL);
		if (label == null || label.iconUrl().isEmpty()) {
			return null;
		}
		return IconCache.INSTANCE.get(label.iconUrl().get());
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<Identifier> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

		@Override
		public SpecialModelRenderer<Identifier> bake(BakingContext context) {
			return new DiskLabelRenderer();
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
