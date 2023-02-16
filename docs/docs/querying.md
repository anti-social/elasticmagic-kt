# Querying

To build a search query use a [SearchQuery](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-search-query/index.html) builder.

In these samples we will utilize following document:

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/querying/UserDoc.kt"
```

## Query

You can pass a query to `SearchQuery` directly via its constructor:

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/querying/Constructor.kt"
```

Also it is possible to replace existing query using a `query` method: 

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/querying/QueryMethod.kt"
```

See full list of available [query expressions](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.query/index.html).

## Cloning

In the last example `q` and `q2` variables point to the same object. For efficiency all
`SearchQuery` methods modify current instance and return it for chaining method calls.

If you want to get independent query instance you should clone it explicitly:

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/querying/Clone.kt"
```

Now you can modify `clonedQuery` without touching its ancestor `q`.

## Filtering

Using [filter](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-base-search-query/filter.html)
method you can filter your query. All filters passed to the `filter` method will be combined
using `AND` operation.

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/querying/Filter.kt"
```

See [Query Filter Context](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-filter-context.html)

## Sorting

[sort](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-base-search-query/sort.html)
method has 2 flavors:

- a shortcut that accepts document fields
- and full-featured version accepting [Sort](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-sort/index.html)
  expressions

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/querying/Sort.kt"
```

See:

  - [Sort Search Results](https://www.elastic.co/guide/en/elasticsearch/reference/current/sort-search-results.html)

## Aggregations

Use [aggs](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-base-search-query/aggs.html)
method to define aggregations:

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/querying/Aggs.kt"
```

See [Search Aggregations](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations.html)

## Query Nodes

Sometimes you don't know final form of your query when creating it. For such a usecase it is
possible to replace parts of the query after creation using special query expressions and
[queryNode](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-base-search-query/query-node.html)
method:

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/querying/Nodes.kt"
```
