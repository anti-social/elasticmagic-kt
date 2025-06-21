# Welcome to Elasticmagic

Elasticmagic implements advanced type awareness DSL for Kotlin to construct Elasticsearch queries.

!!! Warning
    The library is in very alpha status. API may change significantly at any time.
    Use it on your own risk

## Getting started

### Setup

Add following dependencies in your `build.gradle.kts` script:

```kotlin
repositories {
    mavenCentral()
}

val elasticmagicVersion = "{{ gradle.elasticmagic_version }}"
val ktorVersion = "{{ gradle.ktor_version }}"

dependencies {
    // Elasticmagic core api
    implementation("dev.evo.elasticmagic:elasticmagic:$elasticmagicVersion")
    // Json serialization using kotlinx.serialization
    implementation("dev.evo.elasticmagic:elasticmagic-serde-kotlinx-json:$elasticmagicVersion")
    // Transport that uses ktor http client
    implementation("dev.evo.elasticmagic:elasticmagic-transport-ktor:$elasticmagicVersion")

    implementation("io.ktor:ktor-client-cio:$ktorVersion")
}
```

### Usage

First you need to describe a document (represents a mapping in terms of Elasticsearch):

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/started/UserDoc.kt"
```

Now create `ElasticsearchCluster` object. It is an entry point for executing search queries:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/started/Cluster.kt"
```

Any `ElasticsearchCluster` needs an `ElasticsearchTransport`. We will use
the `ElasticsearchKtorTransport` that utilises [Ktor](https://ktor.io/docs/create-client.html)
http client.

Here are examples of creating transports for the cluster.

JVM:

```kotlin
--8<-- "samples/src/jvmMain/kotlin/samples/started/ClusterJvm.kt"
```

Native:

```kotlin
--8<-- "samples/src/nativeMain/kotlin/samples/started/ClusterNative.kt"
```

Create our index if it does not exist or update the mapping otherwise:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/started/EnsureIndexExists.kt"
```

Describe document sources and index them: 

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/started/IndexDocs.kt"
```

And finally we can search our data:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/started/Main.kt"
```

### Run the sample

You can find fully working example inside [samples](https://github.com/anti-social/elasticmagic-kt/tree/master/samples/src/commonMain/kotlin/samples/started)

And run it with as JVM application (of cause you need Elasticsearch available at `localhost:9200`):

```shell
./gradlew :samples:run
```

or native:

```shell
./gradlew :samples:runDebugExecutableNative
```
