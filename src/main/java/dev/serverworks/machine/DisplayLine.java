package dev.serverworks.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** One line of a Status Display: a translation key, its arguments, and a colour. */
public record DisplayLine(String key, List<String> args, int color) {
	public static final Codec<DisplayLine> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.string(0, 128).fieldOf("key").forGetter(DisplayLine::key),
			Codec.string(0, 64).listOf(0, 4).optionalFieldOf("args", List.of()).forGetter(DisplayLine::args),
			Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(DisplayLine::color)
	).apply(i, DisplayLine::new));

	public static final DisplayLine EMPTY = new DisplayLine("", List.of(), 0xFFFFFF);

	public DisplayLine {
		args = List.copyOf(args);
	}

	public static DisplayLine of(String key, int color, Object... args) {
		return new DisplayLine(key, java.util.Arrays.stream(args).map(String::valueOf).toList(), color);
	}
}
