# AGENTS.md

本ドキュメントは、本リポジトリで作業する AI コーディングエージェントおよび開発者向けのシステムガイドライン、アーキテクチャ設計原則、および移行注意事項（Gotchas）をまとめた指示書です。

---

## 1. プロジェクト概要

- **目的**: AWS SDK for Java v1 (`DynamoDBMapper`) から AWS SDK for Java v2 (`DynamoDbEnhancedClient`) への安全かつ段階的な移行リファレンス実装。
- **主要な特徴**:
  - CRUD、楽観的ロック、バッチ処理 (BatchWrite/BatchGet)、トランザクション処理 (TransactWriteItems)、クエリ (PK/SK, GSI)、スキャン、ページネーションの実装。
  - v1 と v2 の振る舞いが完全に一致することを共通の契約テスト (`OrderRepositoryContractTest`) で検証。

---

## 2. 移行アーキテクチャ原則

大規模・稼働中のシステムにおいて SDK を安全に移行するため、以下の 3 つの原則を厳格に維持してください。

```
                  ┌──────────────────────────────┐
                  │    Domain Model (Order)      │  ← 純粋な POJO / Record (SDK非依存)
                  └──────────────▲───────────────┘
                                 │
                  ┌──────────────┴───────────────┐
                  │ <<Interface>> OrderRepository│  ← 統一リポジトリインターフェース
                  └──────────────▲───────────────┘
                                 │
              ┌───────────────────┴───────────────────┐
              │                                       │
┌────────────────────────────┐       ┌────────────────────────────┐
│ V1DynamoDbOrderRepository  │       │ V2DynamoDbEnhancedOrder    │
│  (AWS SDK v1 DynamoDBMapper│       │  Repository                │
│   + OrderItemV1 DTO)       │       │  (AWS SDK v2 Enhanced      │
│                            │       │   + OrderItemV2 DTO)       │
└────────────────────────────┘       └────────────────────────────┘
```

1. **ドメイン層の完全な SDK 非依存 (Domain Independence)**:
   - `com.example.dynamodb.domain` 配下のクラス（`Order`, `OrderKey`, `OrderLineItem`, `OrderStatus`）には、AWS SDK のクラス・アノテーションを絶対に持ち込んではいけません。
2. **Repository パターンによるカプセル化 (Repository Encapsulation)**:
   - 外部（Service 層等）との接点は `OrderRepository` インターフェースのみとし、SDK 固有の型（`DynamoDBMapper`, `DynamoDbEnhancedClient`, `AttributeValue` 等）を露出させないでください。
3. **契約テストによる等価性保証 (Contract Testing)**:
   - 新しい操作やメソッドを追加した場合は、必ず `OrderRepositoryContractTest` にテストケースを追加し、v1 実装 (`V1OrderRepositoryTest`) と v2 実装 (`V2OrderRepositoryTest`) の両方で同一の検証を実行してください（全 12 シナリオ、合計 24 テストケース）。

---

## 3. 前提条件 & 技術スタック

| 項目 | バージョン / 要件 | 備考 |
| :--- | :--- | :--- |
| **Java (JDK)** | **Java 25** | Gradle Toolchain で管理 |
| **Gradle** | **9.1.0** | プロジェクト同梱の Gradle Wrapper (`./gradlew`) を使用 |
| **依存関係管理** | **Gradle Version Catalog** | `gradle/libs.versions.toml` で一元管理（`libs.xxx` を使用） |
| **ボイラープレート削減** | **Lombok 1.18.46** | `@Data`, `@Builder`, `@AllArgsConstructor` を活用 |
| **テスト基盤** | **JUnit 5, AssertJ, Testcontainers** | Docker コンテナ上で `amazon/dynamodb-local:latest` を自動起動 |

---

## 4. コーディング規約 & ベストプラクティス

### 4.1. Javadoc 規約
- すべての `public` クラス、インターフェース、メソッド、定数、コンストラクタには正確な Javadoc を記述してください。
- サブクラスでの用途やオーバーライドが見込まれる `protected` メソッド・フィールドにも Javadoc を記述してください。
- `@param`, `@return`, `@throws` を省略せず、明確に意味を記述してください。
- Gradle の `javadoc` タスク（`./gradlew javadoc`）を実行した際に、**警告（Warning）が 0 件** である状態を維持してください。

### 4.2. Lombok 規約
- ドメインモデルやネスト DTO には `@Data`, `@Builder`, `@AllArgsConstructor` を活用してボイラープレートを削減してください。
- Javadoc ツールがデフォルトコンストラクタのコメント欠落警告を出すのを防ぐため、`@NoArgsConstructor` 単体ではなく、**Javadoc 付きの明示的なデフォルトコンストラクタ** を定義してください。
- 不変クラス用のアノテーション `@Value` は、DynamoDB ORM の JavaBean 規約（引数なしコンストラクタと Setter の要求）に反するため、DTO クラスには使用しないでください。

### 4.3. DynamoDB DTO 規約
- **v1 (`OrderItemV1`, `OrderLineItemV1`)**:
  - クラスに `@DynamoDBTable(tableName = "...")` / `@DynamoDBDocument` を付与。
  - アクセサに `@DynamoDBHashKey`, `@DynamoDBRangeKey`, `@DynamoDBVersionAttribute` 等を付与。
- **v2 (`OrderItemV2`, `OrderLineItemV2`)**:
  - クラスに `@DynamoDbBean` を付与。
  - `TableSchema.fromBean` がスキャンできるように、Getter メソッドに `@DynamoDbPartitionKey`, `@DynamoDbSortKey`, `@DynamoDbVersionAttribute` 等を付与。

---

## 5. 開発 & 検証コマンド

エージェントおよび開発者が変更を行った後は、以下のコマンドを実行して品質を検証してください。

```bash
# 1. コードフォーマット検証・適用 (Spotless)
./gradlew spotlessCheck   # フォーマット検査
./gradlew spotlessApply   # 自動フォーマット適用

# 2. ソースコードおよびテストコードのコンパイル検証
./gradlew compileJava compileTestJava

# 3. Javadoc 生成検証（警告 0 件であることを確認）
./gradlew javadoc

# 4. テストを除外した全体ビルドチェック
./gradlew check -x test

# 5. 全契約テストの実行（※ Docker デーモンが起動している環境のみ）
./gradlew test
```

---

## 6. DynamoDB 移行時の重要注意事項 (Gotchas)

1. **`Instant`（日時型）のシリアライズ仕様**:
   - v1 (`DynamoDBMapper`): デフォルトで `Instant` を扱えないため、`DynamoDBTypeConverter<String, Instant>` によるカスタムコンバーターが必要。
   - v2 (`DynamoDbEnhancedClient`): 標準で ISO-8601 文字列（例: `2026-08-22T01:45:00Z`）へ自動変換されるため、フォーマットの互換性に留意。
2. **楽観的ロック (`@DynamoDbVersionAttribute`) 更新時のバージョン値**:
   - v1: `mapper.save(item)` は渡したオブジェクトの `version` を自動でインクリメント後の値にインプレース更新します。
   - v2: `table.putItem(item)` は引数の Java オブジェクトを変更しません。更新後の最新バージョン値が必要な場合は `getItem` で再取得するか、手動で加算を追跡する必要があります。
3. **同名クラスの競合**:
   - `AttributeValue` 等のクラス名が v1 (`com.amazonaws.services.dynamodbv2.model...`) と v2 (`software.amazon.awssdk.services.dynamodb.model...`) で衝突するため、必ず `repository.v1` と `repository.v2` でパッケージを物理的に隔離してください。
4. **HTTP クライアントの選択**:
   - SDK v2 では軽量な `url-connection-client`、高スループットな `apache-client`、非同期対応の `netty-nio-client` から選択可能。本リポジトリでは依存関係が最も軽量な `url-connection-client` を採用。

