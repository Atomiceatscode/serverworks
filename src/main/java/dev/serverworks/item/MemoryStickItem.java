package dev.serverworks.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** A stick of server memory. Put it in a Memory Bank and the rack's server gets that much more. */
public final class MemoryStickItem extends Item {
	private final int gigabytes;

	public MemoryStickItem(int gigabytes, Properties properties) {
		super(properties);
		this.gigabytes = gigabytes;
	}

	public int gigabytes() {
		return gigabytes;
	}

	public int megabytes() {
		return gigabytes * 1024;
	}

	/** How much memory a stack adds, or 0 when it is not a stick. */
	public static int megabytes(ItemStack stack) {
		return stack.getItem() instanceof MemoryStickItem stick ? stick.megabytes() : 0;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
			TooltipFlag flag) {
		lines.accept(Component.translatable("item.serverworks.memory_stick.adds", gigabytes).withStyle(ChatFormatting.GRAY));
		lines.accept(Component.translatable("item.serverworks.memory_stick.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
