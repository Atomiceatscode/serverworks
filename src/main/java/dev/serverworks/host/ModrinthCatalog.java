package dev.serverworks.host;

import dev.serverworks.catalog.CatalogException;
import dev.serverworks.catalog.ModrinthClient;
import dev.serverworks.catalog.model.ProjectVersion;
import dev.serverworks.host.ServerInstance.Failure;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** The Modrinth client seen as the blocking catalog the resolver wants. Call it from worker threads only. */
public final class ModrinthCatalog implements ContentResolver.Catalog {
	private final ModrinthClient client;

	public ModrinthCatalog(ModrinthClient client) {
		this.client = client;
	}

	@Override
	public List<ProjectVersion> versions(String projectId, List<String> loaders, String minecraftVersion) throws PrepareException {
		return await(() -> client.versions(projectId, loaders, List.of(minecraftVersion)));
	}

	@Override
	public ProjectVersion version(String versionId) throws PrepareException {
		return await(() -> client.version(versionId));
	}

	private interface Call<T> {
		CompletableFuture<T> start();
	}

	private static <T> T await(Call<T> call) throws PrepareException {
		try {
			return call.start().get(60, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			String reason = e.getCause() instanceof CatalogException catalog ? catalog.getMessage() : "The catalog could not be read";
			throw new PrepareException(Failure.CONTENT, reason, e.getCause());
		} catch (CatalogException e) {
			throw new PrepareException(Failure.CONTENT, e.getMessage(), e);
		} catch (TimeoutException e) {
			throw new PrepareException(Failure.CONTENT, "The catalog took too long to answer");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PrepareException(Failure.CONTENT, "Interrupted");
		}
	}
}
