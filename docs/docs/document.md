# Document (aka mapping)

Roughly speaking [Document](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-document/index.html)
represents Elasticsearch's mapping. However, it is possible to merge multiple documents into 
a single mapping.

It is convenient to describe `Document` subclasses as singleton objects.

Read more about 
[Elasticsearch mapping types](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-types.html)

## Simple fields

### General way

You can use [field](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-field-set/field.html)
method to describe a field in a document:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/field/Field.kt"
```

Fields can be used when building a search query:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/field/SearchQuery.kt"
```

### Using shortcuts

There are some nice shortcuts for popular field types. 
You don't need to import all those field types:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/shortcuts/Shortcuts.kt"
```

Full list of available shortcuts can be found 
[here](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-base-document/index.html)

### Sub fields

It is possible to define [sub-fields](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-sub-fields/index.html)
for any simple field:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/subfields/SubFields.kt"
```

Sub-fields also can be used in search queries:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/subfields/SearchQuery.kt"
```

!!! Note
    It is a common mistake to make sub-fields a singleton object. Following example
    will fail at runtime.

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/subfields/mistake/Mistake.kt"
```

<details>
  <summary>Show an error</summary>
  
```
Exception in thread "main" java.lang.ExceptionInInitializerError
        at samples.document.subfields.mistake.MistakeKt.main(Mistake.kt:17)
        at samples.document.subfields.mistake.MistakeKt.main(Mistake.kt)
Caused by: java.lang.IllegalStateException: [description] sub-fields has already been initialized as [about] sub-fields
        at dev.evo.elasticmagic.SubFields$SubFieldsDelegate.provideDelegate(Document.kt:339)
        at samples.document.subfields.mistake.UserDoc.<clinit>(Mistake.kt:13)
        ... 2 more
```
</details>

## Object fields

### Object

Object type just represent hierarchical structure. It is similar to sub-fields but every field
in a sub-document can have its own source value:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/object/Object.kt"
```

!!! Note
    The same as with the sub-fields sub-document should not be a singleton object. 

Read more:

  * [Elasticsearch object type](https://www.elastic.co/guide/en/elasticsearch/reference/current/object.html)
  * [Object API](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-base-document/obj.html)

### Nested

Using nested type it you can work with sub-documents independently. 
In the example below we find all users that have a `moderator` role with both 
`article` and `order` permissions:

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/nested/Nested.kt"
```

If we tried to make it with `object` type, we would find users that have 
a `moderator` role with `article` permission and `view` role with `order` permission.

Read more:
  
  * [Elasticsearch nested type](https://www.elastic.co/guide/en/elasticsearch/reference/current/nested.html)
  * [Nested API](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-base-document/nested.html)

## Parent-child relationship

Parent/child relationship allows you to define a link between documents inside an index.

### Join field

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/join/Join.kt"
```

Read more:
  
  * [Elasticsearch join type](https://www.elastic.co/guide/en/elasticsearch/reference/current/parent-join.html)
  * [Join API](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/-field-set/join.html)

### Merge multiple documents

To create mapping for multiple documents you can use
[mergeDocuments](https://anti-social.github.io/elasticmagic-kt/api/latest/elasticmagic/dev.evo.elasticmagic/merge-documents.html)
function. Documents that are merged should not contradict each other.

```kotlin
--8<-- "../samples/src/main/kotlin/samples/document/join/Merge.kt"
```

Resulting document can be used when creating an index or updating an existing mapping. 
