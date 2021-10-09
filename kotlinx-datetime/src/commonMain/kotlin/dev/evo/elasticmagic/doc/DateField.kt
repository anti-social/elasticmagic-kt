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
): FieldSet.Field<Instant> {
    return field(
        name, InstantType,
        docValues = docValues,
        index = index,
        store = store,
        params = params,
    )
}

fun FieldSet.datetime(
    name: String? = null,
    docValues: Boolean? = null,
    index: Boolean? = null,
    store: Boolean? = null,
    params: Params? = null,
): FieldSet.Field<LocalDateTime> {
    return field(
        name, DateTimeType,
        docValues = docValues,
        index = index,
        store = store,
        params = params,
    )
}

fun FieldSet.date(
    name: String? = null,
    docValues: Boolean? = null,
    index: Boolean? = null,
    store: Boolean? = null,
    params: Params? = null,
): FieldSet.Field<LocalDate> {
    return field(
        name, DateType,
        docValues = docValues,
        index = index,
        store = store,
        params = params,
    )
}
