# Query Filters

Query filters allow you to describe search query modifications declaratively. It is possible to
filter, sort, paginate and build facets using query filters.

Let's describe our new document:

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/qf/BikeDoc.kt"
```

Now we can describe query filters for the `BikeDoc`:

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/qf/BikeQueryFilters.kt"
```

To apply it to a search query you need query filter parameters. They are just a mapping where keys
are a pair of filter name and an operation, and values are a list of strings. For example, it
could be transformed from http query parameters.

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/qf/Apply.kt"
```

After executing query we are able to process its results:

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/qf/Process.kt"
```

## Run a full-fledged query filters sample

JVM version:

```shell
./gradlew :samples:runBikeshop -q --console=plain
```

Native version:

```shell
./gradlew :samples:linkBikeshopDebugExecutableNative
./samples/build/bin/native/bikeshopDebugExecutable/bikeshop.kexe
```