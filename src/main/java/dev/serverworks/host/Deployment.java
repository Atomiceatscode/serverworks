package dev.serverworks.host;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.serverworks.host.InstallPlan.Artifact;
import dev.serverworks.host.ServerInstance.Failure;
import dev.serverworks.host.ServerInstance.State;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gets one server ready to launch: works out what to install, downloads it into a shared
 * cache, and lays out the server's folder.
 *
 * <p>Folder layout under the runtime root:
 * <pre>
 * cache/server/&lt;platform&gt;/     server jars, shared by every rack
 * cache/content/&lt;sha512&gt;/      mod and plugin files, shared by every rack
 * instances/&lt;rack id&gt;/         one server's working folder: world, configs, logs
 * </pre>
 */
public final class Deployment implements ServerInstance.Preparation {
	static final String SERVER_JAR = "server.jar";
	/** Lists the files this mod placed, so it only ever removes its own. */
	static final String MANIFEST = ".serverworks-managed.json";
	/** Records which installer build last ran in a server folder. */
	static final String INSTALLED_MARKER = ".serverworks-installed";
	/** Present while a server is set up to sit behind a proxy. */
	static final String PROXIED_MARKER = ".serverworks-proxied";
	/** Records which modpack's overrides were last copied in. */
	static final String PACK_MARKER = ".serverworks-pack";
	private static final long MAX_SERVER_BYTES = 512L * 1024 * 1024;
	private static final long MAX_CONTENT_BYTES = 512L * 1024 * 1024;

	private final Path root;
	private final Fetcher fetcher;
	private final ContentResolver resolver;
	private final LaunchRequest request;
	private final String javaBinary;
	private final int javaVersion;

	public Deployment(Path root, Fetcher fetcher, ContentResolver resolver, LaunchRequest request, String javaBinary,
			int javaVersion) {
		this.root = root;
		this.fetcher = fetcher;
		this.resolver = resolver;
		this.request = request;
		this.javaBinary = javaBinary;
		this.javaVersion = javaVersion;
	}

	public static Path instanceDir(Path root, java.util.UUID rackId) {
		return root.resolve("instances").resolve(rackId.toString());
	}

	@Override
	public ServerInstance.Prepared prepare(ServerInstance.Progress progress) throws PrepareException {
		if (!request.eulaAccepted()) {
			throw new PrepareException(Failure.EULA, "The Minecraft EULA has not been accepted");
		}
		PlatformProvider provider = Providers.get(request.platform());
		if (provider == null) {
			throw new PrepareException(Failure.UNSUPPORTED, request.platform().id() + " cannot be launched by this build yet");
		}

		progress.state(State.RESOLVING, 0);
		InstallPlan plan = provider.plan(fetcher, request.minecraftVersion());
		if (plan.javaVersion() > javaVersion) {
			throw new PrepareException(Failure.LAUNCH, plan.build() + " needs Java " + plan.javaVersion()
					+ ", but this game runs on Java " + javaVersion);
		}
		progress.log("Resolving " + plan.build());
		List<ContentResolver.Wanted> wanted = new ArrayList<>(request.disks());
		boolean modernFabric = request.asBackend() != null && request.asBackend().mode() == Forwarding.MODERN
				&& (request.platform() == dev.serverworks.rack.Platform.FABRIC || request.platform() == dev.serverworks.rack.Platform.QUILT);
		if (modernFabric) {
			wanted.add(new ContentResolver.Wanted(ProxyConfigs.FABRIC_PROXY_PROJECT, ProxyConfigs.FABRIC_PROXY_TITLE, null,
					dev.serverworks.catalog.model.SideSupport.UNSUPPORTED));
		}
		if (request.platform().group() == dev.serverworks.rack.Platform.Group.PROXY && request.asProxy() == null) {
			throw new PrepareException(Failure.CONTENT, "A proxy needs at least one linked rack behind it");
		}
		List<ContentFile> content = new ArrayList<>(resolver.resolve(request.platform(), request.minecraftVersion(), wanted));
		progress.checkCancelled();

		// Download whatever is not already cached.
		Path serverCache = root.resolve("cache/server").resolve(request.platform().id()).resolve(plan.serverJar().fileName());
		Map<ContentFile, Path> contentCache = new LinkedHashMap<>();
		long total = Files.exists(serverCache) ? 0 : Math.max(1, plan.serverJar().size());
		for (ContentFile file : content) {
			Path cached = root.resolve("cache/content").resolve(file.sha512().substring(0, 2)).resolve(file.sha512())
					.resolve(file.fileName());
			contentCache.put(file, cached);
			if (!Files.exists(cached)) {
				total += Math.max(1, file.size());
			}
		}
		progress.state(State.DOWNLOADING, 0);
		long[] done = {0};
		long finalTotal = Math.max(1, total);
		java.util.function.LongConsumer onBytes = bytes -> {
			done[0] += bytes;
			progress.state(State.DOWNLOADING, (int) Math.min(100, done[0] * 100 / finalTotal));
		};
		if (!Files.exists(serverCache)) {
			Artifact jar = plan.serverJar();
			progress.log("Downloading " + plan.build());
			fetcher.download(HostAllowlist.require(jar.url()), serverCache, jar.checksum(), MAX_SERVER_BYTES, onBytes);
		}
		for (Map.Entry<ContentFile, Path> entry : contentCache.entrySet()) {
			progress.checkCancelled();
			if (!Files.exists(entry.getValue())) {
				ContentFile file = entry.getKey();
				progress.log("Downloading " + file.project());
				fetcher.download(HostAllowlist.require(file.url()), entry.getValue(), Checksum.sha512(file.sha512()),
						MAX_CONTENT_BYTES, onBytes);
			}
		}
		progress.checkCancelled();

		// Modpacks list their own files: read them now, and fetch any not cached yet.
		Path pack = null;
		for (ContentFile file : List.copyOf(content)) {
			if (!file.directory().equals(ContentResolver.PACK_DIRECTORY)) {
				continue;
			}
			pack = contentCache.remove(file);
			content.remove(file);
			progress.log("Unpacking " + file.project());
			for (ContentFile inner : ModpackIndex.serverFiles(pack, file.project(), request.platform(), request.minecraftVersion())) {
				Path cached = root.resolve("cache/content").resolve(inner.sha512().substring(0, 2)).resolve(inner.sha512())
						.resolve(inner.fileName());
				if (!Files.exists(cached)) {
					progress.checkCancelled();
					fetcher.download(HostAllowlist.require(inner.url()), cached, Checksum.sha512(inner.sha512()),
							MAX_CONTENT_BYTES, onBytes);
				}
				content.add(inner);
				contentCache.put(inner, cached);
			}
		}

		progress.state(State.INSTALLING, 0);
		Path dir = instanceDir(root, request.rackId());
		List<String> launch;
		try {
			Files.createDirectories(dir);
			launch = switch (plan.setup()) {
				case NONE -> {
					place(serverCache, dir.resolve(SERVER_JAR));
					yield List.of("-jar", SERVER_JAR, "nogui");
				}
				case BUILD_JAR -> {
					place(build(plan, serverCache, progress), dir.resolve(SERVER_JAR));
					yield List.of("-jar", SERVER_JAR, "nogui");
				}
				case INSTALL_IN_INSTANCE -> {
					install(plan, serverCache, dir, progress);
					yield Launchers.discover(dir);
				}
			};
			Files.writeString(dir.resolve("eula.txt"),
					"# Accepted in game through Serverworks by the person running this machine.\neula=true\n", StandardCharsets.UTF_8);
			switch (request.platform()) {
				case VELOCITY -> ProxyConfigs.velocity(dir, request, request.asProxy());
				case BUNGEECORD -> ProxyConfigs.bungeeCord(dir, request, request.asProxy());
				default -> {
					ProxyConfigs.backend(dir, request.platform(), request.asBackend());
					writeProperties(dir);
				}
			}
			syncContent(dir, contentCache);
			if (pack != null) {
				applyOverrides(pack, dir, progress);
				// Players need the whole modpack, so it is the client pack as it is.
				Files.copy(pack, dir.resolve(ClientPack.FILE_NAME), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} else {
				byte[] clientPack = ClientPack.build(request.platform(), request.minecraftVersion(), plan.loaderVersion(),
						request.motd(), content);
				if (clientPack != null) {
					Files.write(dir.resolve(ClientPack.FILE_NAME), clientPack);
				} else {
					Files.deleteIfExists(dir.resolve(ClientPack.FILE_NAME));
				}
			}
		} catch (IOException e) {
			throw new PrepareException(Failure.INSTALL, "Could not set up the server folder: " + e.getMessage(), e);
		}
		for (ContentFile file : content) {
			progress.log((file.automatic() ? "Added dependency " : "Installed ") + file.project() + " (" + file.fileName() + ")");
		}

		List<String> command = new ArrayList<>(List.of(
				javaBinary,
				"-Xms" + Math.min(512, request.ramMb()) + "M",
				"-Xmx" + request.ramMb() + "M",
				"-D" + ServerInstance.MARKER_PROPERTY + "=" + request.rackId()));
		command.addAll(launch);
		return new ServerInstance.Prepared(dir, command, plan.readyMarker());
	}

	/** Compiles the server jar with the platform's build tool, once per version; later racks reuse it. */
	private Path build(InstallPlan plan, Path tool, ServerInstance.Progress progress) throws PrepareException, IOException {
		Path built = root.resolve("cache/server").resolve(request.platform().id()).resolve(plan.builtJar());
		if (Files.exists(built)) {
			return built;
		}
		Path work = root.resolve("cache/build").resolve(request.platform().id());
		Files.createDirectories(work);
		progress.log("Building " + plan.build() + ". The first build takes several minutes.");
		List<String> command = new ArrayList<>(List.of(javaBinary, "-jar", tool.toAbsolutePath().toString()));
		command.addAll(plan.setupArgs());
		SetupRunner.run(command, work, Providers.BUILD_TIMEOUT_MINUTES, progress);
		Path output = work.resolve(plan.builtJar());
		if (!Files.exists(output)) {
			throw new PrepareException(Failure.INSTALL, "The build finished without producing " + plan.builtJar());
		}
		Files.move(output, built, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		return built;
	}

	/** Runs the platform's installer in the server folder, unless this build is already installed there. */
	private void install(InstallPlan plan, Path installer, Path dir, ServerInstance.Progress progress)
			throws PrepareException, IOException {
		Path marker = dir.resolve(INSTALLED_MARKER);
		if (Files.exists(marker) && Files.readString(marker, StandardCharsets.UTF_8).strip().equals(plan.build())) {
			return;
		}
		progress.log("Installing " + plan.build());
		List<String> command = new ArrayList<>(List.of(javaBinary, "-jar", installer.toAbsolutePath().toString()));
		command.addAll(plan.setupArgs());
		SetupRunner.run(command, dir, Providers.INSTALL_TIMEOUT_MINUTES, progress);
		Files.writeString(marker, plan.build(), StandardCharsets.UTF_8);
	}

	/** Copies a modpack's overrides in, once per version of the pack, so later edits are kept. */
	private static void applyOverrides(Path pack, Path dir, ServerInstance.Progress progress) throws IOException {
		Path marker = dir.resolve(PACK_MARKER);
		String id = Files.size(pack) + ":" + pack.getParent().getFileName();
		if (Files.exists(marker) && Files.readString(marker, StandardCharsets.UTF_8).strip().equals(id)) {
			return;
		}
		progress.log("Copying the modpack's settings into the server folder");
		ModpackIndex.extractOverrides(pack, dir);
		Files.writeString(marker, id, StandardCharsets.UTF_8);
	}

	private void writeProperties(Path dir) throws IOException {
		boolean proxied = request.asBackend() != null;
		Map<String, String> forced = new LinkedHashMap<>();
		forced.put("server-port", Integer.toString(request.port()));
		forced.put("query.port", Integer.toString(request.port()));
		forced.put("max-players", Integer.toString(request.maxPlayers()));
		// Behind a proxy a server is only reachable from this computer, and trusts the proxy for identity.
		forced.put("server-ip", proxied ? "127.0.0.1" : request.bindAddress());
		forced.put("online-mode", Boolean.toString(!proxied && request.onlineMode()));
		Path marker = dir.resolve(PROXIED_MARKER);
		if (proxied) {
			forced.put("white-list", "false");
			forced.put("enforce-whitelist", "false");
			Files.writeString(marker, "behind a proxy", StandardCharsets.UTF_8);
		} else if (Files.exists(marker)) {
			// Standing alone again: the whitelist switched off for the proxy comes back on.
			forced.put("white-list", "true");
			Files.delete(marker);
		}
		forced.put("accepts-transfers", "true");
		forced.put("enable-rcon", "false");
		Map<String, String> defaults = new LinkedHashMap<>();
		defaults.put("motd", request.motd());
		ServerProperties.apply(dir.resolve("server.properties"), forced, defaults);
	}

	/** Makes the instance folder hold exactly the wanted files, touching only files this mod put there. */
	private void syncContent(Path dir, Map<ContentFile, Path> wanted) throws IOException {
		Path manifestFile = dir.resolve(MANIFEST);
		Set<String> previous = new HashSet<>();
		if (Files.exists(manifestFile)) {
			try {
				JsonElement parsed = JsonParser.parseString(Files.readString(manifestFile, StandardCharsets.UTF_8));
				for (JsonElement element : parsed.getAsJsonArray()) {
					previous.add(element.getAsString());
				}
			} catch (RuntimeException e) {
				// An unreadable manifest means nothing is known to be ours, so nothing is removed.
			}
		}
		Set<String> current = new HashSet<>();
		for (Map.Entry<ContentFile, Path> entry : wanted.entrySet()) {
			ContentFile file = entry.getKey();
			String relative = file.directory() + "/" + file.fileName();
			Path target = dir.resolve(relative).normalize();
			if (!target.startsWith(dir)) {
				throw new IOException("Refusing to write outside the server folder");
			}
			Files.createDirectories(target.getParent());
			place(entry.getValue(), target);
			current.add(relative);
		}
		for (String old : previous) {
			Path target = dir.resolve(old).normalize();
			if (!current.contains(old) && target.startsWith(dir)) {
				Files.deleteIfExists(target);
			}
		}
		JsonArray array = new JsonArray();
		current.stream().sorted().forEach(array::add);
		Files.writeString(manifestFile, new Gson().toJson(array), StandardCharsets.UTF_8);
	}

	/** Hard-links when the file system allows it, so shared files cost no extra disk space. */
	private static void place(Path source, Path target) throws IOException {
		if (Files.exists(target) && Files.size(target) == Files.size(source)) {
			try {
				if (Files.isSameFile(source, target)) {
					return;
				}
			} catch (IOException ignored) {
				// Fall through and replace it.
			}
		}
		Files.deleteIfExists(target);
		try {
			Files.createLink(target, source);
		} catch (IOException | UnsupportedOperationException e) {
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
