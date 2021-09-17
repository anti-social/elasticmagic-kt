# Querying

To build a search query use a [SearchQuery](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-search-query/index.html) builder.

In these samples we will utilize following document:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/querying/UserDoc.kt"
```

## Query

You can pass a query to `SearchQuery` directly via its constructor:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/querying/Constructor.kt"
```

Also it is possible to replace existing query using a `query` method: 

```kotlin
--8<-- "../samples/src/main/kotlin/samples/querying/QueryMethod.kt"
```


## Filtering

## Sorting

## Aggregations

## Query Nodes
