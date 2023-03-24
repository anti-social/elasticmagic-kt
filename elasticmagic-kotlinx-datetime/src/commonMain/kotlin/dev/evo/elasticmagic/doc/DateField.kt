package dev.evo.elasticmagic.doc

import dev.evo.elasticmagic.Params
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

import kotlinx.datetime.LocalDateTime

fun FieldSet.instant(
    name: String? = null,
    docValues: Boolean? = null,
    index: Boolean? = null,
    store: Boolean? = null,
    params: Params? = null,
): FieldSet.Field<Instant, Instant> {
    @Suppress("NAME_SHADOWING")
    val params = Params(
        params,
        "doc_values" to docValues,
        "index" to index,
        "store" to store,
    )
    return FieldSet.Field(name, InstantType, params)
}

fun FieldSet.datetime(
    name: String? = null,
    docValues: Boolean? = null,
    index: Boolean? = null,
    store: Boolean? = null,
    params: Params? = null,
): FieldSet.Field<LocalDateTime, LocalDateTime> {
    @Suppress("NAME_SHADOWING")
    val params = Params(
        params,
        "doc_values" to docValues,
        "index" to index,
        "store" to store,
    )
    return FieldSet.Field(name, DateTimeType, params)
}

fun FieldSet.date(
    name: String? = null,
    docValues: Boolean? = null,
    index: Boolean? = null,
    store: Boolean? = null,
    params: Params? = null,
): FieldSet.Field<LocalDate, LocalDate> {
    @Suppress("NAME_SHADOWING")
    val params = Params(
        params,
        "doc_values" to docValues,
        "index" to index,
        "store" to store,
    )
    return FieldSet.Field(name, DateType, params)
}
