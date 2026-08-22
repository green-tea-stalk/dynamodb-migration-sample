# DynamoDB v1 to v2 移行サンプルプロジェクト

[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Gradle](https://img.shields.io/badge/Gradle-9.1.0-02303A.svg?logo=gradle)](https://gradle.org)
[![Lombok](https://img.shields.io/badge/Lombok-1.18.46-red.svg?logo=lombok)](https://projectlombok.org/)
[![AWS SDK v1](https://img.shields.io/badge/AWS%20SDK%20v1-1.12.797-232F3E.svg?logo=amazon-aws)](https://aws.amazon.com/sdk-for-java/)
[![AWS SDK v2](https://img.shields.io/badge/AWS%20SDK%20v2-2.54.2-232F3E.svg?logo=amazon-aws)](https://aws.amazon.com/sdk-for-java/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-1.21.4-blue.svg?logo=docker)](https://testcontainers.com/)

本リポジトリは、**AWS SDK for Java v1 (`DynamoDBMapper`)** から **AWS SDK for Java v2 (`DynamoDbEnhancedClient`)** への段階的かつ安全な移行を実証・解説するサンプルプロジェクトです。

実務で頻出する各種 DynamoDB 操作（CRUD、楽観的ロック、バッチ処理、トランザクション、PK/SK クエリ、GSI クエリ、フィルタ付きスキャン、ページネーション）を網羅し、共通の契約テスト（Contract Test）によって **v1 と v2 の振る舞いが 100% 等価であること** を自動検証しています。

> 📖 **設計原則・規約・移行の落とし穴の完全ガイド**:
> システム詳細設計、コーディング規約、詳細な移行注意事項（Gotchas）については [AGENTS.md](AGENTS.md) を参照してください。

---

## 📑 目次

- [🎯 移行アーキテクチャ方針](#-移行アーキテクチャ方針)
- [📊 v1 vs v2 徹底比較](#-v1-vs-v2-徹底比較)
  - [1. 依存関係 (Gradle Version Catalog)](#1-依存関係-gradle-version-catalog)
  - [2. クライアント初期化](#2-クライアント初期化)
  - [3. Entity / DTO 定義 (アノテーション比較)](#3-entity--dto-定義-アノテーション比較)
  - [4. 操作別実装比較](#4-操作別実装比較)
- [⚠️ 移行時の重要ポイント (Gotchas)](#️-移行時の重要ポイント-gotchas)
- [📁 プロジェクト構成](#-プロジェクト構成)
- [🚀 ビルド & テスト実行手順](#-ビルド--テスト実行手順)

---

## 🎯 移行アーキテクチャ方針

稼働中のシステムにおいて SDK を安全に移行するため、以下の 3 原則を採用しています（詳細は [AGENTS.md](AGENTS.md#2-移行アーキテクチャ原則) を参照）。

1. **ドメイン層の完全分離 (Domain Independence)**:
   - `Order`, `OrderKey`, `OrderLineItem`, `OrderStatus` は純粋な POJO / Record で設計し、AWS SDK のアノテーションや型を一切持ち込みません。
2. **Repository パターンによるカプセル化 (Repository Encapsulation)**:
   - 共通の `OrderRepository` インターフェースを介してデータアクセスを行い、呼び出し元に SDK の差異を漏らしません。
3. **契約テストによる等価性保証 (Contract Test for Parity)**:
   - Testcontainers (`amazon/dynamodb-local`) を用いて、全く同一のテストスイート（`OrderRepositoryContractTest`）を v1 実装と v2 実装の双方に実行します（全 12 シナリオ、合計 24 テストケース）。

---

## 📊 v1 vs v2 徹底比較

### 1. 依存関係 (Gradle Version Catalog)

本プロジェクトでは Gradle 標準の **Version Catalog** (`gradle/libs.versions.toml`) を用いて依存関係を一元管理しています。AWS SDK v2 はモジュール化されており、必要な機能のみを個別に追加できます。また BOM (Bill of Materials) を利用してバージョンを一元管理します。

```toml
# gradle/libs.versions.toml
[versions]
aws-sdk-v1 = "1.12.797"
aws-sdk-v2 = "2.54.2"
lombok = "1.18.46"

[libraries]
# AWS SDK v1
aws-sdk-v1-dynamodb = { module = "com.amazonaws:aws-java-sdk-dynamodb", version.ref = "aws-sdk-v1" }

# AWS SDK v2
aws-sdk-v2-bom = { module = "software.amazon.awssdk:bom", version.ref = "aws-sdk-v2" }
aws-sdk-v2-dynamodb = { module = "software.amazon.awssdk:dynamodb" }
aws-sdk-v2-dynamodb-enhanced = { module = "software.amazon.awssdk:dynamodb-enhanced" }
aws-sdk-v2-url-connection-client = { module = "software.amazon.awssdk:url-connection-client" }
```

```kotlin
// build.gradle.kts
dependencies {
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // AWS SDK v1
    implementation(libs.aws.sdk.v1.dynamodb)

    // AWS SDK v2
    implementation(platform(libs.aws.sdk.v2.bom))
    implementation(libs.aws.sdk.v2.dynamodb)
    implementation(libs.aws.sdk.v2.dynamodb.enhanced)
    implementation(libs.aws.sdk.v2.url.connection.client) // HTTP クライアント
    implementation(libs.jackson.databind)
}
```

### 2. クライアント初期化

| 項目 | SDK v1 | SDK v2 |
| :--- | :--- | :--- |
| **基本クライアント** | `AmazonDynamoDBClientBuilder.standard()...build()` | `DynamoDbClient.builder()...build()` |
| **高レベルマッパー** | `new DynamoDBMapper(amazonDynamoDB)` | `DynamoDbEnhancedClient.builder().dynamoDbClient(client).build()` |
| **HTTP クライアント** | Apache HTTP Client (固定) | プラガブル (`url-connection-client`, `apache-client`, `netty-nio-client`) |
| **エンドポイント設定** | `builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))` | `builder.endpointOverride(URI.create(endpoint))` |

### 3. Entity / DTO 定義 (アノテーション比較)

| 機能 | SDK v1 (`OrderItemV1`) | SDK v2 (`OrderItemV2`) |
| :--- | :--- | :--- |
| **クラス宣言** | `@DynamoDBTable(tableName = "orders")` | `@DynamoDbBean`（テーブル名はコード側でバインド） |
| **Partition Key (PK)** | `@DynamoDBHashKey(attributeName = "customerId")` | `@DynamoDbPartitionKey`<br>`@DynamoDbAttribute("customerId")` |
| **Sort Key (SK)** | `@DynamoDBRangeKey(attributeName = "orderId")` | `@DynamoDbSortKey`<br>`@DynamoDbAttribute("orderId")` |
| **GSI PK** | `@DynamoDBIndexHashKey(globalSecondaryIndexName = "...", attributeName = "...")` | `@DynamoDbSecondaryPartitionKey(indexNames = "...")` |
| **GSI SK** | `@DynamoDBIndexRangeKey(globalSecondaryIndexName = "...", attributeName = "...")` | `@DynamoDbSecondarySortKey(indexNames = "...")` |
| **楽観的ロック** | `@DynamoDBVersionAttribute(attributeName = "version")` | `@DynamoDbVersionAttribute`<br>`@DynamoDbAttribute("version")` |
| **日時型 (`Instant`)** | `@DynamoDBTypeConverted(converter = InstantTypeConverter.class)` (要自作) | デフォルトで ISO-8601 文字列として自動変換 |
| **Enum 型** | `@DynamoDBTypeConvertedEnum` | デフォルトで Enum の `name()` 文字列として自動変換 |
| **ネストオブジェクト** | `@DynamoDBDocument` | `@DynamoDbBean` |

### 4. 操作別実装比較

#### (1) テーブル参照
- **v1**: DTO クラスに `@DynamoDBTable(tableName = "orders")` を静的に記述。
- **v2**: `enhancedClient.table("orders", TableSchema.fromBean(OrderItemV2.class))` を使用して動的にバインド。

#### (2) 主キー検索 (GetItem / findById)
- **v1**:
  ```java
  OrderItemV1 item = mapper.load(OrderItemV1.class, customerId, orderId);
  ```
- **v2**:
  ```java
  Key key = Key.builder().partitionValue(customerId).sortValue(orderId).build();
  OrderItemV2 item = table.getItem(r -> r.key(key).consistentRead(true));
  ```

#### (3) 楽観的ロック更新 (updateStatus)
- **v1**:
  ```java
  item.setStatus(newStatus);
  item.setVersion(expectedVersion); // 期待するバージョンをセット
  mapper.save(item); // 不一致なら ConditionalCheckFailedException
  ```
- **v2**:
  ```java
  item.setStatus(newStatus);
  item.setVersion(expectedVersion); // 期待するバージョンをセット
  table.putItem(item); // 自動でバージョン検証 & インクリメント
  ```

#### (4) バッチ処理 (batchSave / batchFindByIds)
- **v1 (Batch Save / Batch Load)**:
  ```java
  // 一括保存
  mapper.batchSave(items);

  // 一括取得
  Map<String, List<Object>> results = mapper.batchLoad(itemsToLoad);
  ```
- **v2 (BatchWriteItem / BatchGetItem)**:
  ```java
  // 一括保存
  WriteBatch<OrderItemV2> writeBatch = WriteBatch.builder(OrderItemV2.class)
          .mappedTableResource(table)
          .addPutItem(item1)
          .addPutItem(item2)
          .build();
  enhancedClient.batchWriteItem(r -> r.writeBatches(writeBatch));

  // 一括取得
  ReadBatch readBatch = ReadBatch.builder(OrderItemV2.class)
          .mappedTableResource(table)
          .addGetItem(key1)
          .addGetItem(key2)
          .build();
  enhancedClient.batchGetItem(r -> r.readBatches(readBatch)).resultsForTable(table);
  ```

#### (5) トランザクション処理 (executeInTransaction)
- **v1 (TransactionWriteRequest)**:
  ```java
  TransactionWriteRequest txRequest = new TransactionWriteRequest();
  txRequest.addPut(item1);
  txRequest.addPut(item2);
  mapper.transactionWrite(txRequest);
  ```
- **v2 (TransactWriteItemsEnhancedRequest)**:
  ```java
  TransactWriteItemsEnhancedRequest txRequest = TransactWriteItemsEnhancedRequest.builder()
          .addPutItem(table, item1)
          .addPutItem(table, item2)
          .build();
  enhancedClient.transactWriteItems(txRequest);
  ```

#### (6) GSI クエリ (findByStatus)
- **v1**:
  ```java
  OrderItemV1 hashKeyValues = new OrderItemV1();
  hashKeyValues.setStatus(status);
  DynamoDBQueryExpression<OrderItemV1> expr = new DynamoDBQueryExpression<OrderItemV1>()
          .withIndexName("status-orderDate-index")
          .withHashKeyValues(hashKeyValues)
          .withConsistentRead(false);
  List<OrderItemV1> results = mapper.query(OrderItemV1.class, expr);
  ```
- **v2**:
  ```java
  DynamoDbIndex<OrderItemV2> index = table.index("status-orderDate-index");
  QueryConditional conditional = QueryConditional.keyEqualTo(
          Key.builder().partitionValue(status.name()).build()
  );
  List<OrderItemV2> results = new ArrayList<>();
  index.query(r -> r.queryConditional(conditional))
       .forEach(page -> results.addAll(page.items()));
  ```

#### (7) ページネーション付きスキャン (scanOrdersPaged)
- **v1**: `mapper.scanPage(OrderItemV1.class, scanExpression)` から `page.getLastEvaluatedKey()` (`Map<String, com.amazonaws.services.dynamodbv2.model.AttributeValue>`) を取得。
- **v2**: `table.scan(request).iterator().next()` から `page.lastEvaluatedKey()` (`Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>`) を取得。

---

## ⚠️ 移行時の重要ポイント (Gotchas)

移行時に注意すべき代表的なポイントは以下の通りです（詳しいコード例や対策は [AGENTS.md#6-dynamodb-移行時の重要注意事項-gotchas](AGENTS.md#6-dynamodb-移行時の重要注意事項-gotchas) を参照）。

1. **`Instant`（日時型）のシリアライズ仕様**: v1 では要カスタムコンバーター、v2 では ISO-8601 文字列へ自動変換。
2. **楽観的ロック更新時のバージョン値**: v1 は引数オブジェクトをインプレース更新、v2 はオブジェクトを変更しないため再取得が必要。
3. **`AttributeValue` などの同名クラス競合**: パッケージを `repository.v1` と `repository.v2` で完全に分離して回避。
4. **HTTP クライアントの選択**: v2 では軽量な `url-connection-client` を推奨採用。

---

## 📁 プロジェクト構成

```
.
├── build.gradle.kts                                  # ビルド定義 (Java 25, Version Catalog 参照)
├── settings.gradle.kts                               # プロジェクト設定
├── gradlew / gradlew.bat                             # Gradle Wrapper スクリプト
├── gradle/
│   ├── libs.versions.toml                            # 【Version Catalog】依存関係・バージョン一元管理
│   └── wrapper/                                      # Gradle 9.1.0 Wrapper 設定
├── README.md                                         # 本ドキュメント (概要 & v1/v2 比較)
├── AGENTS.md                                         # 【開発指示書】アーキテクチャ詳細・規約・Gotchas
└── src/
    ├── main/java/com/example/dynamodb/
    │   ├── domain/                                   # 【ドメイン層】SDK非依存の純粋なPOJO / Record
    │   │   ├── Order.java                            # 注文ドメインエンティティ
    │   │   ├── OrderKey.java                         # 注文主キー Record
    │   │   ├── OrderLineItem.java                    # 注文明細値オブジェクト
    │   │   └── OrderStatus.java                      # 注文ステータス Enum
    │   ├── repository/
    │   │   ├── OrderRepository.java                  # 統一リポジトリインターフェース
    │   │   ├── PageResult.java                       # ページネーション共通結果オブジェクト
    │   │   ├── v1/                                   # 【AWS SDK v1 実装】
    │   │   │   ├── OrderItemV1.java                  # DynamoDBMapper 用 DTO
    │   │   │   ├── OrderLineItemV1.java              # DynamoDBMapper 用 ネストDTO
    │   │   │   └── V1DynamoDbOrderRepository.java    # v1 リポジトリ実装 (CRUD, Query, Scan, Batch, Tx)
    │   │   └── v2/                                   # 【AWS SDK v2 実装】
    │   │       ├── OrderItemV2.java                  # DynamoDbEnhancedClient 用 DTO
    │   │       ├── OrderLineItemV2.java              # DynamoDbEnhancedClient 用 ネストDTO
    │   │       └── V2DynamoDbEnhancedOrderRepository.java # v2 リポジトリ実装 (CRUD, Query, Scan, Batch, Tx)
    │   └── config/                                   # 【クライアント設定】
    │       ├── DynamoDbV1Config.java                 # AmazonDynamoDB クライアント生成
    │       └── DynamoDbV2Config.java                 # DynamoDbClient / EnhancedClient 生成
    └── test/java/com/example/dynamodb/
        ├── AbstractDynamoDbContainerTest.java        # Testcontainers DynamoDB Local 初期化基底クラス
        ├── OrderRepositoryContractTest.java          # v1/v2 共通契約テストスイート (全12ケース)
        ├── V1OrderRepositoryTest.java                # v1 実装テストランナー (12ケース実行)
        └── V2OrderRepositoryTest.java                # v2 実装テストランナー (12ケース実行)
```

---

## 🚀 ビルド & テスト実行手順

```bash
# ビルド
./gradlew build -x test

# 全契約テストの実行 (※ Docker デーモンが起動している環境で実行)
./gradlew test
```

`Testcontainers` により DynamoDB Local (`amazon/dynamodb-local:latest`) コンテナが自動起動し、以下の 12 シナリオが **v1 実装と v2 実装の双方（合計 24 テストケース）** に対し実行されます。

1. **CRUD: 注文の保存と主キー（customerId, orderId）による取得** (`testSaveAndFindById`)
2. **CRUD: 存在しないキーでの検索時に empty が返ることの確認** (`testFindByIdNotFound`)
3. **CRUD: 楽観的ロック（@Version）による更新とバージョン競合検知** (`testOptimisticLockingSuccessAndConflict`)
4. **CRUD: 注文の削除** (`testDelete`)
5. **Batch: 複数注文の一括保存（batchSave）と一括取得（batchFindByIds）** (`testBatchSaveAndBatchFindByIds`)
6. **Batch: 存在しない主キーを含む一括取得での正常返却** (`testBatchFindByIds_PartialAndNotFound`)
7. **Transaction: 複数注文のアトミックな一括書き込み** (`testExecuteInTransaction_Success`)
8. **Query: 顧客ID（Partition Key）での全件取得** (`testFindByCustomerId`)
9. **Query: 顧客ID + 注文日時範囲（Filter / Date Range）での取得** (`testFindByCustomerIdAndDateRange`)
10. **Query (GSI): 注文ステータスによる GSI 検索** (`testFindByStatusGsi`)
11. **Scan: 金額条件（FilterExpression）付きスキャン** (`testScanWithMinAmount`)
12. **Scan: ページネーション付きスキャンによる全件走査** (`testScanOrdersPaged`)



