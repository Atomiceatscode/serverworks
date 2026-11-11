package dev.serverworks.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.serverworks.catalog.CatalogHosts;
import dev.serverworks.catalog.model.ProjectSummary;
import dev.serverworks.catalog.model.ProjectType;
import dev.serverworks.catalog.model.SideSupport;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What is written on a Mod Disk: the catalog project it stands for.
 *
 * <p>This is the data component that makes one registered item look and act like any of
 * the catalog's projects. It only identifies the project; the files are resolved when a
 * rack starts, so a disk never goes stale.
 *
 * @param iconUrl       CDN address of the project icon, when it has one
 * @param color         packed RGB accent colour, or -1
 * @param pinnedVersion a specific version id to use instead of the newest compatible one
 * @param pinnedName    that version's name as its author wrote it, for showing on the disk
 */
public record DiskLabel(
		String projectId,
		String slug,
		String title,
		String author,
		ProjectType type,
		List<String> loaders,
		Optional<String> iconUrl,
		int color,
		SideSupport clientSide,
		SideSupport serverSide,
		Optional<String> pinnedVersion,
		Optional<String> pinnedName) {

	private static final int MAX_LOADERS = 48;

	private static final Codec<ProjectType> TYPE_CODEC = Codec.STRING.xmap(ProjectType::fromApi, ProjectType::apiName);
	private static final Codec<SideSupport> SIDE_CODEC = Codec.STRING.xmap(SideSupport::fromApi,
			side -> side.name().toLowerCase(Locale.ROOT));

	public static final Codec<DiskLabel> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.string(1, 64).fieldOf("project").forGetter(DiskLabel::projectId),
			Codec.string(1, 64).fieldOf("slug").forGetter(DiskLabel::slug),
			Codec.string(0, 128).fieldOf("title").forGetter(DiskLabel::title),
			Codec.string(0, 64).optionalFieldOf("author", "").forGetter(DiskLabel::author),
			TYPE_CODEC.optionalFieldOf("type", ProjectType.OTHER).forGetter(DiskLabel::type),
			Codec.string(1, 32).listOf(0, MAX_LOADERS).optionalFieldOf("loaders", List.of()).forGetter(DiskLabel::loaders),
			Codec.string(1, 512).optionalFieldOf("icon").forGetter(DiskLabel::iconUrl),
			Codec.INT.optionalFieldOf("color", -1).forGetter(DiskLabel::color),
			SIDE_CODEC.optionalFieldOf("client", SideSupport.UNKNOWN).forGetter(DiskLabel::clientSide),
			SIDE_CODEC.optionalFieldOf("server", SideSupport.UNKNOWN).forGetter(DiskLabel::serverSide),
			Codec.string(1, 64).optionalFieldOf("version").forGetter(DiskLabel::pinnedVersion),
			Codec.string(1, 64).optionalFieldOf("version_name").forGetter(DiskLabel::pinnedName)
	).apply(i, DiskLabel::new));

	public static final StreamCodec<ByteBuf, DiskLabel> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public DiskLabel {
		loaders = List.copyOf(loaders);
		// An icon address that is not on the catalog's CDN is dropped rather than trusted.
		iconUrl = iconUrl.filter(CatalogHosts::isCdnUrl);
	}

	public static DiskLabel of(ProjectSummary project) {
		return new DiskLabel(
				project.id(),
				project.slug(),
				clip(project.title(), 128),
				clip(project.author(), 64),
				project.type(),
				project.loaders().stream().filter(l -> !l.isEmpty() && l.length() <= 32).limit(MAX_LOADERS).toList(),
				Optional.ofNullable(project.iconUrl()),
				project.color(),
				project.clientSide(),
				project.serverSide(),
				Optional.empty(),
				Optional.empty());
	}

	/** The same disk, locked to one version of its project. */
	public DiskLabel pinnedTo(String versionId, String versionName) {
		return new DiskLabel(projectId, slug, title, author, type, loaders, iconUrl, color, clientSide, serverSide,
				Optional.of(versionId), Optional.of(clip(versionName, 64)).filter(name -> !name.isEmpty()));
	}

	/** True when players can join a server running this without installing it themselves. */
	public boolean serverSideOnly() {
		return clientSide != SideSupport.REQUIRED;
	}

	private static String clip(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}
}
