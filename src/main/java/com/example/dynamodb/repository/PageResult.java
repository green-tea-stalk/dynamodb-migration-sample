package com.example.dynamodb.repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Generic container for paginated query and scan results (independent of AWS
 * SDK).
 *
 * @param <T>
 *            Type of the item
 */
public class PageResult<T> {

    private final List<T> items;
    private final String nextToken;

    /**
     * Constructs a paginated result container.
     *
     * @param items
     *            List of items retrieved
     * @param nextToken
     *            Next page pagination token (null if last page or no more results)
     */
    public PageResult(List<T> items, String nextToken) {
        this.items = items != null ? Collections.unmodifiableList(items) : Collections.emptyList();
        this.nextToken = nextToken;
    }

    /**
     * Returns the list of retrieved items.
     *
     * @return Unmodifiable list of items
     */
    public List<T> getItems() {
        return items;
    }

    /**
     * Returns the pagination token for fetching the next page.
     *
     * @return Next page token wrapped in {@link Optional} (empty if no next page)
     */
    public Optional<String> getNextToken() {
        return (nextToken != null && !nextToken.trim().isEmpty()) ? Optional.of(nextToken) : Optional.empty();
    }

    /**
     * Determines whether a subsequent page exists.
     *
     * @return true if a next page exists; false otherwise
     */
    public boolean hasNextPage() {
        return getNextToken().isPresent();
    }

    @Override
    public String toString() {
        return "PageResult{" + "itemsCount=" + items.size() + ", hasNextPage=" + hasNextPage() + '}';
    }
}
