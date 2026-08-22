package com.example.dynamodb.repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * ページネーション付き検索結果を表す汎用クラス（AWS SDK 非依存）
 *
 * @param <T> アイテムの型
 */
public class PageResult<T> {

    private final List<T> items;
    private final String nextToken;

    /**
     * ページネーション結果を生成するコンストラクタ。
     *
     * @param items     取得されたアイテム一覧
     * @param nextToken 次ページ取得用トークン（最終ページまたは次ページがない場合は null）
     */
    public PageResult(List<T> items, String nextToken) {
        this.items = items != null ? Collections.unmodifiableList(items) : Collections.emptyList();
        this.nextToken = nextToken;
    }

    /**
     * 取得されたアイテム一覧を取得します。
     *
     * @return 変更不可能なアイテムリスト
     */
    public List<T> getItems() {
        return items;
    }

    /**
     * 次のページを取得するためのトークンを取得します。
     *
     * @return 次ページトークン（存在しない場合は {@link Optional#empty()}）
     */
    public Optional<String> getNextToken() {
        return (nextToken != null && !nextToken.trim().isEmpty())
                ? Optional.of(nextToken)
                : Optional.empty();
    }

    /**
     * 次ページが存在するかどうかを判定します。
     *
     * @return 次ページが存在する場合は true、そうでない場合は false
     */
    public boolean hasNextPage() {
        return getNextToken().isPresent();
    }

    @Override
    public String toString() {
        return "PageResult{" +
                "itemsCount=" + items.size() +
                ", hasNextPage=" + hasNextPage() +
                '}';
    }
}
