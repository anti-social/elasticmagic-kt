# Document source

Document source represents an Elasticsearch document in Kotlin. Document source is
responsible for data serialization/deserialization. For example, almost all
Elasticsearch data types can be multi-valued and a mapping doesn't reflect that fact.
But in our programs we want to operate with concrete types, as we work differently with
`String` or `List<String>`. Also all fields in a mapping are optional that requires `null`-checks
in the code. Specifying document source we can set up proper (de)serialization of underlying data.

!!! Warning
    This API is a subject to change. 
    See more info at [issue](https://github.com/anti-social/elasticmagic-kt/issues/1)

Suppose we have following `Document`:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/docsource/UserDoc.kt"
```

## Dynamic

The most simple way to work with source documents is just to use `DynDocSource`:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/docsource/dynamic/DynDocSource.kt"
```

## User defined

This is the recommended way. You can explicitly specify document source:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/docsource/custom/UserDocSource.kt"
```
