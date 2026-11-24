package com.vingame.glswing.node.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Thread-safe searchable node catalog with favorites, recents, and port filtering. */
public final class GLNodeCatalog {
    private java.util.function.BiPredicate<String,String> valueTypeCompatibility=String::equals;
    public synchronized void setValueTypeCompatibility(java.util.function.BiPredicate<String,String> policy){valueTypeCompatibility=java.util.Objects.requireNonNull(policy);}

	private final Map<String, GLNodeCatalogEntry> entries = new LinkedHashMap<>();
	private final Set<String> favorites = new LinkedHashSet<>();
	private final ArrayDeque<String> recent = new ArrayDeque<>();
	private int recentLimit = 12;
    private List<String> categoryOrder = List.of();
    /** Explicit category order; unlisted categories follow alphabetically. Empty restores the default. */
    public synchronized void setCategoryOrder(List<String> categories) {
        categoryOrder = List.copyOf(categories);
    }
    private int categoryIndex(String category) {
        int index = categoryOrder.indexOf(category);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

	public synchronized void register(GLNodeCatalogEntry entry) {
		if (entry == null) throw new IllegalArgumentException("Catalog entry required");
		if (entries.putIfAbsent(entry.type().id(), entry) != null)
			throw new IllegalArgumentException("Node type is already cataloged: "
					+ entry.type().id());
	}
	/** Replaces presentation metadata for an already cataloged node type. */
	public synchronized void replace(GLNodeCatalogEntry entry) {
		if (entry == null) throw new IllegalArgumentException("Catalog entry required");
		String typeId = entry.type().id();
		if (!entries.containsKey(typeId))
			throw new IllegalArgumentException("Node type is not cataloged: " + typeId);
		entries.put(typeId, entry);
	}
	public synchronized Optional<GLNodeCatalogEntry> find(String typeId) {
		return Optional.ofNullable(entries.get(typeId));
	}
	public synchronized boolean unregister(String typeId) {
		favorites.remove(typeId);
		recent.remove(typeId);
		return entries.remove(typeId) != null;
	}
	public synchronized List<GLNodeCatalogEntry> entries() {
		return List.copyOf(entries.values());
	}
	public synchronized void setRecentLimit(int limit) {
		if (limit < 0) throw new IllegalArgumentException("Recent limit must be nonnegative");
		recentLimit = limit;
		trimRecent();
	}
	public synchronized boolean toggleFavorite(String typeId) {
		require(typeId);
		if (!favorites.add(typeId)) favorites.remove(typeId);
		return favorites.contains(typeId);
	}
	public synchronized boolean isFavorite(String typeId) {
		return favorites.contains(typeId);
	}
	public synchronized void markUsed(String typeId) {
		require(typeId);
		recent.remove(typeId);
		recent.addFirst(typeId);
		trimRecent();
	}
	public synchronized List<String> recentTypeIds() { return List.copyOf(recent); }

	public synchronized List<GLNodeCatalogEntry> search(String query,
			GLNodePortDescriptor compatiblePort) {
		String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
		List<GLNodeCatalogEntry> result = new ArrayList<>();
		for (GLNodeCatalogEntry entry : entries.values()) {
			if (!needle.isEmpty() && !searchText(entry).contains(needle)) continue;
			if (compatiblePort != null && entry.type().ports().stream()
					.noneMatch(candidate -> compatible(compatiblePort, candidate))) continue;
			result.add(entry);
		}
		result.sort(Comparator
				.comparing((GLNodeCatalogEntry entry) -> !favorites.contains(entry.type().id()))
				.thenComparingInt(entry -> recentIndex(entry.type().id()))
				.thenComparingInt(entry -> categoryIndex(entry.category()))
				.thenComparing(GLNodeCatalogEntry::category, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(GLNodeCatalogEntry::displayName,
						String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(result);
	}
	public List<GLNodeCatalogEntry> search(String query) { return search(query, null); }

	private int recentIndex(String typeId) {
		int index = 0;
		for (String value : recent) {
			if (value.equals(typeId)) return index;
			index++;
		}
		return Integer.MAX_VALUE;
	}
	private void trimRecent() {
		while (recent.size() > recentLimit) recent.removeLast();
	}
	private GLNodeCatalogEntry require(String typeId) {
		GLNodeCatalogEntry entry = entries.get(typeId);
		if (entry == null) throw new IllegalArgumentException("Unknown node type: " + typeId);
		return entry;
	}
	private boolean compatible(GLNodePortDescriptor left,
			GLNodePortDescriptor right) {
		return left.direction() != right.direction()
				&& valueTypeCompatibility.test(left.valueTypeId(),right.valueTypeId());
	}
	private static String searchText(GLNodeCatalogEntry entry) {
		return (entry.displayName() + " " + entry.category() + " "
				+ entry.type().id() + " " + String.join(" ", entry.keywords()))
				.toLowerCase(Locale.ROOT);
	}
}
