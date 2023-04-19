package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.query.QueryExpression


open class BaseQueryValue(
    val name: String
)

class SimpleQueryValue(name: String, val expr: QueryExpression) : BaseQueryValue(name)
