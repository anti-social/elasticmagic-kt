# Document (aka mapping)

Roughly speaking [Document](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.doc/-document/index.html)
represents Elasticsearch's mapping. However, it is possible to merge multiple documents into 
a single mapping.

It is convenient to describe `Document` subclasses as singleton objects.

Read more about 
[Elasticsearch mapping types](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-types.html)

## Simple fields

### General way

You can use [field](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.doc/-field-set/field.html)
method to describe a field in a document:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/field/Field.kt"
```

Fields can be used when building a search query:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/field/SearchQuery.kt"
```

### Using shortcuts

There are some nice shortcuts for popular field types. 
You don't need to import all those field types:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/shortcuts/Shortcuts.kt"
```

Full list of available shortcuts can be found
[here](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.doc/-base-document/index.html)

### Enums

It is possible to map field values to kotlin enums.
Use [enum](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.doc/enum.html)
extension function for that:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/enums/Enum.kt"
```

Now you are able to use enum variants in your search queries:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/enums/SearchQuery.kt"
```

### Sub fields

It is possible to define [sub-fields](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.doc/-sub-fields/index.html)
for any simple field:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/subfields/SubFields.kt"
```

Sub-fields also can be used in search queries:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/subfields/SearchQuery.kt"
```

!!! Note
    It is a mistake to use sub-fields twice. Following example will fail at runtime.

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/subfields/mistake/Mistake.kt"
```

<details>
  <summary>Show an error</summary>
  
```
Exception in thread "main" java.lang.ExceptionInInitializerError
        at samples.document.subfields.mistake.MistakeKt.main(Mistake.kt:17)
        at samples.document.subfields.mistake.MistakeKt.main(Mistake.kt)
Caused by: java.lang.IllegalStateException: Field [description] has already been initialized as [about]
        at dev.evo.elasticmagic.SubFields$UnboundSubFields.provideDelegate(Document.kt:363)
        at samples.document.subfields.mistake.UserDoc.<clinit>(Mistake.kt:13)
        ... 2 more
```
</details>

## Object fields

### Object

Object type just represents a hierarchical structure. It is similar to sub-fields but every field
in a sub-document can have its own source value:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/object/Object.kt"
```

!!! Note
    The same as with the sub-fields sub-document should not be a singleton object. 

Read more:

  * [Elasticsearch object type](https://www.elastic.co/guide/en/elasticsearch/reference/current/object.html)
  * [Object API](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.doc/-base-document/obj.html)

### Nested

Using nested type it you can work with sub-documents independently. 
In the example below we find all users that have a `moderator` role with both 
`article` and `order` permissions:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/nested/Nested.kt"
```

If we tried to make it with `object` type, we would find users that have 
a `moderator` role with `article` permission and `view` role with `order` permission.

Read more:
  
  * [Elasticsearch nested type](https://www.elastic.co/guide/en/elasticsearch/reference/current/nested.html)
  * [Nested API](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.doc/-base-document/nested.html)

## Parent-child relationship

Parent/child relationship allows you to define a link between documents inside an index.

### Join field

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/join/Join.kt"
```

Read more:
  
  * [Elasticsearch join type](https://www.elastic.co/guide/en/elasticsearch/reference/current/parent-join.html)
  * [Join API](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.doc/-field-set/join.html)

### Meta fields

Elasticsearch mapping has [metadata fields](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-fields.html).
Some of those fields can be customized. In following example we make a value for `_routing`
field required and keep only `name` field in document source:

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/meta/Meta.kt"
```

Now you must provide the required routing value when indexing documents otherwise Elasticsearch
will throw `routing_missing_exceptions`.

### Merge multiple documents

To create a mapping for multiple documents you can use
[mergeDocuments](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic.doc/merge-documents.html)
function. Documents that are merged should not contradict each other.

```kotlin
--8<-- "samples/src/commonMain/kotlin/samples/document/join/Merge.kt"
```

Resulting document can be used when creating an index or updating an existing mapping. 
