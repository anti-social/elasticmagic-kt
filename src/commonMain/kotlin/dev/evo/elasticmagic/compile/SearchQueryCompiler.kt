package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.*
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

open class SearchQueryCompiler<OBJ>(
    private val serializer: Serializer<OBJ>
) : Compiler<BaseSearchQuery<*, *>, SearchQueryCompiler.Result<OBJ>> {

    data class Result<OBJ>(val docType: String?, val body: OBJ)

    override fun compile(searchQuery: BaseSearchQuery<*, *>): Result<OBJ> {
        val preparedSearchQuery = searchQuery.prepare()
        return Result(
            preparedSearchQuery.docType,
            serializer.obj {
                visit(preparedSearchQuery)
            }
        )
    }

    protected fun ObjectCtx.visit(searchQuery: PreparedSearchQuery<*>) {
        val query = searchQuery.query
        val filteredQuery: QueryExpression? = if (searchQuery.filters.isNotEmpty()) {
            if (query != null) {
                Bool(must = listOf(query), filter = searchQuery.filters)
            } else {
                Bool(filter = searchQuery.filters)
            }
        } else {
            searchQuery.query
        }
        if (filteredQuery != null) {
            obj("query") {
                visit(filteredQuery)
            }
        }
    }

    protected fun ObjectCtx.visit(expression: Expression) {
        when (expression) {
            is Term -> visit(expression)
            is Exists -> visit(expression)
            is Range -> visit(expression)
            is Match -> visit(expression)
            is MatchAll -> visit(expression)
            is MultiMatch -> visit(expression)
            is Bool -> visit(expression)
            is FunctionScore -> visit(expression)
            else -> throw IllegalArgumentException(expression.name)
        }
    }

    protected fun ObjectCtx.visit(term: Term) {
        obj(term.name) {
            if (term.boost != null) {
                obj(term.field.getQualifiedFieldName()) {
                    field("value", term.term)
                    field("boost", term.boost)
                }
            } else {
                field(term.field.getQualifiedFieldName(), term.term)
            }
        }
    }

    protected fun ObjectCtx.visit(exists: Exists) {
        obj(exists.name) {
            field("field", exists.field.getQualifiedFieldName())
        }
    }

    protected fun ObjectCtx.visit(range: Range) {
        obj(range.name) {
            obj(range.field.getQualifiedFieldName()) {
                if (range.gt != null) {
                    field("gt", range.gt)
                }
                if (range.gte != null) {
                    field("gte", range.gte)
                }
                if (range.lt != null) {
                    field("lt", range.lt)
                }
                if (range.lte != null) {
                    field("lte", range.lte)
                }
            }
        }
    }

    protected fun ObjectCtx.visit(match: Match) {
        val params = Params(
            match.params,
            "analyzer" to match.analyzer,
            "minimum_should_match" to match.minimumShouldMatch,
        )
        obj(match.name) {
            if (params.isEmpty()) {
                field(match.field.getQualifiedFieldName(), match.query)
            } else {
                obj(match.field.getQualifiedFieldName()) {
                    visit(params)
                }
            }
        }
    }

    protected fun ObjectCtx.visit(match: MatchAll) {
        obj(match.name) {}
    }

    protected fun ObjectCtx.visit(match: MultiMatch) {
        val params = Params(
            match.params,
            "query" to match.query,
            "fields" to match.fields.map { field -> field.getQualifiedFieldName() },
            "type" to match.type?.name?.toLowerCase(),
            "boost" to match.boost,
        )
        obj(match.name) {
            visit(params)
        }
    }

    protected fun ObjectCtx.visit(bool: Bool) {
        obj(bool.name) {
            if (!bool.filter.isNullOrEmpty()) {
                array("filter") {
                    visit(bool.filter)
                }
            }
            if (!bool.should.isNullOrEmpty()) {
                array("should") {
                    visit(bool.should)
                }
            }
            if (!bool.must.isNullOrEmpty()) {
                array("must") {
                    visit(bool.must)
                }
            }
            if (!bool.mustNot.isNullOrEmpty()) {
                array("must_not") {
                    visit(bool.mustNot)
                }
            }
        }
    }

    protected fun ObjectCtx.visit(fs: FunctionScore) {
        obj(fs.name) {
            if (fs.query != null) {
                obj("query") {
                    visit(fs.query)
                }
            }
            if (fs.boost != null) {
                field("boost", fs.boost)
            }
            if (fs.scoreMode != null) {
                field("score_mode", fs.scoreMode.name.toLowerCase())
            }
            if (fs.boostMode != null) {
                field("boost_mode", fs.boostMode.name.toLowerCase())
            }
            array("functions") {
                for (fn in fs.functions) {
                    visit(fn)
                }
            }
        }
    }

    protected fun ArrayCtx.visit(fn: FunctionScore.Function) {
        obj {
            val filter = fn.filter
            if (filter != null) {
                obj("filter") {
                    visit(filter)
                }
            }
            when (fn) {
                is FunctionScore.Weight -> visit(fn)
                is FunctionScore.FieldValueFactor -> visit(fn)
                else -> throw IllegalArgumentException(fn.name)
            }
        }
    }

    protected fun ObjectCtx.visit(weight: FunctionScore.Weight) {
        field("weight", weight.weight)
    }

    protected fun ObjectCtx.visit(factor: FunctionScore.FieldValueFactor) {
        obj(factor.name) {
            field("field", factor.field.getQualifiedFieldName())
            if (factor.factor != null) {
                field("factor", factor.factor)
            }
            if (factor.missing != null) {
                field("missing", factor.missing)
            }
        }
    }

    protected fun ArrayCtx.visit(values: List<*>) {
        for (value in values) {
            when (value) {
                is Map<*, *> -> obj {
                    visit(value)
                }
                is List<*> -> array {
                    visit(value)
                }
                is Expression -> obj {
                    visit(value)
                }
                else -> value(value)
            }
        }
    }

    protected fun ObjectCtx.visit(params: Map<*, *>) {
        for ((name, value) in params) {
            require(name is String) {
                "Expected string but was: ${if (name != null) name::class else null}"
            }
            when (value) {
                is Map<*, *> -> obj(name) {
                    visit(value)
                }
                is List<*> -> array(name) {
                    visit(value)
                }
                is Expression -> field(name, visit(value))
                else -> field(name, value)
            }
        }
    }
}
