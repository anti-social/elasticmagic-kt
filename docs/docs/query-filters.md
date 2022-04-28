# Query Filters

Query filters allow you to describe search query modifications declaratively:

```kotlin
--8<-- "../samples/src/commonMain/kotlin/samples/bikeshop/BikeShopQueryFilters.kt"
```

## Run query filters sample

JVM version:

```shell
./gradlew :samples:runBikeshop -q --console=plain
```

Native version:

```shell
./gradlew :samples:linkBikeshopDebugExecutableNative
./samples/build/bin/native/bikeshopDebugExecutable/bikeshop.kexe
```