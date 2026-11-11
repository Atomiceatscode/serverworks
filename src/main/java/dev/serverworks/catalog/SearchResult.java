package dev.serverworks.catalog;

import dev.serverworks.catalog.model.ProjectSummary;

import java.util.List;

/** One page of search hits plus where it sits in the full result set. */
public record SearchResult(List<ProjectSummary> hits, int offset, int limit, int totalHits) {
	public SearchResult {
		hits = List.copyOf(hits);
	}

	public boolean hasMore() {
		return offset + hits.size() < totalHits;
	}
}
