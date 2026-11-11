package dev.serverworks.client.icon;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Turns downloaded icon bytes (PNG, JPEG, GIF or WebP) into a small square of ARGB pixels. */
public final class IconDecoder {
	/** Refuse absurd images before allocating for them. */
	private static final int MAX_SOURCE_SIDE = 4096;

	private IconDecoder() {
	}

	/**
	 * Decodes and shrinks an image to {@code size} x {@code size}, keeping its aspect ratio
	 * and centring it on a transparent background.
	 *
	 * @return {@code size * size} ARGB pixels in row order
	 * @throws IOException when the bytes are not an image this can read
	 */
	public static int[] decode(byte[] bytes, int size) throws IOException {
		BufferedImage image = read(bytes);
		int w = image.getWidth();
		int h = image.getHeight();
		int[] source = image.getRGB(0, 0, w, h, null, 0, w);
		return fit(source, w, h, size);
	}

	static boolean isWebP(byte[] b) {
		return b.length >= 12
				&& b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
				&& b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
	}

	/** Reads the header first and refuses oversized images before any pixels are decoded. */
	private static BufferedImage read(byte[] bytes) throws IOException {
		try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			ImageReader reader;
			if (isWebP(bytes)) {
				// Created directly instead of through ImageIO's service lookup, which does not see
				// classes loaded by the mod class loader.
				reader = new WebPImageReaderSpi().createReaderInstance(null);
			} else {
				var readers = ImageIO.getImageReaders(in);
				if (!readers.hasNext()) {
					throw new IOException("Unsupported icon format");
				}
				reader = readers.next();
			}
			try {
				reader.setInput(in, true, true);
				int w = reader.getWidth(0);
				int h = reader.getHeight(0);
				if (w < 1 || h < 1 || w > MAX_SOURCE_SIDE || h > MAX_SOURCE_SIDE) {
					throw new IOException("Icon has unusable dimensions " + w + "x" + h);
				}
				return reader.read(0);
			} finally {
				reader.dispose();
			}
		}
	}

	/** Area-averaging resize with premultiplied alpha, so edges do not pick up dark fringes. */
	static int[] fit(int[] source, int w, int h, int size) {
		double scale = Math.min((double) size / w, (double) size / h);
		int outW = Math.max(1, (int) Math.round(w * scale));
		int outH = Math.max(1, (int) Math.round(h * scale));
		int offsetX = (size - outW) / 2;
		int offsetY = (size - outH) / 2;
		int[] out = new int[size * size];
		for (int y = 0; y < outH; y++) {
			int y0 = (int) ((long) y * h / outH);
			int y1 = Math.max(y0 + 1, (int) ((long) (y + 1) * h / outH));
			for (int x = 0; x < outW; x++) {
				int x0 = (int) ((long) x * w / outW);
				int x1 = Math.max(x0 + 1, (int) ((long) (x + 1) * w / outW));
				long a = 0;
				long r = 0;
				long g = 0;
				long b = 0;
				for (int sy = y0; sy < y1 && sy < h; sy++) {
					for (int sx = x0; sx < x1 && sx < w; sx++) {
						int p = source[sy * w + sx];
						int pa = p >>> 24;
						a += pa;
						r += (long) ((p >> 16) & 0xFF) * pa;
						g += (long) ((p >> 8) & 0xFF) * pa;
						b += (long) (p & 0xFF) * pa;
					}
				}
				int count = (Math.min(y1, h) - y0) * (Math.min(x1, w) - x0);
				int alpha = (int) (a / count);
				int pixel = 0;
				if (a > 0) {
					pixel = (alpha << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
				}
				out[(y + offsetY) * size + x + offsetX] = pixel;
			}
		}
		return out;
	}
}
