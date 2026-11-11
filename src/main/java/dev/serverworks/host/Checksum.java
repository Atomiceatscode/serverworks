package dev.serverworks.host;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * An expected file digest.
 *
 * @param algorithm a JDK digest name: MD5, SHA-1, SHA-256 or SHA-512
 * @param hex       lowercase hex
 */
public record Checksum(String algorithm, String hex) {
	public Checksum {
		hex = hex.toLowerCase(Locale.ROOT);
	}

	public static Checksum md5(String hex) {
		return new Checksum("MD5", hex);
	}

	public static Checksum sha1(String hex) {
		return new Checksum("SHA-1", hex);
	}

	public static Checksum sha256(String hex) {
		return new Checksum("SHA-256", hex);
	}

	public static Checksum sha512(String hex) {
		return new Checksum("SHA-512", hex);
	}

	public MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public boolean matches(MessageDigest finished) {
		return hex.equals(HexFormat.of().formatHex(finished.digest()));
	}

	public boolean matches(Path file) throws IOException {
		MessageDigest digest = newDigest();
		byte[] buffer = new byte[1 << 16];
		try (InputStream in = Files.newInputStream(file)) {
			int read;
			while ((read = in.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}
		}
		return matches(digest);
	}
}
