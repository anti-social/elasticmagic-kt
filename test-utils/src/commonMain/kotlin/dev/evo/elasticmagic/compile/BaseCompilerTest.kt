package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.BaseTest
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.withIndex
import io.kotest.matchers.types.shouldBeInstanceOf

@Suppress("UnnecessaryAbstractClass")
abstract class BaseCompilerTest<T : BaseCompiler>(
    compilerFactory: (ElasticsearchFeatures) -> T
) : BaseTest() {
    protected open val compilers: List<T> = ElasticsearchFeatures.values()
        .map(compilerFactory)

    // TODO: Parametrized tests
    protected fun testWithCompiler(block: T.() -> Unit) {
        for (compiler in compilers) {
            // TODO: Inject Elasticsearch version into assertion message
            block(compiler)
            // try {
            //     block(compiler)
            // } catch (ex: AssertionError) {
            //     // throw RuntimeException(ex.message, ex)
            //     throw ex
            // }
        }
    }

    class CompiledSearchQuery(
        val params: Params,
        val body: Map<String, Any?>,
    )

    protected fun SearchQueryCompiler.compile(query: SearchQuery<*>): CompiledSearchQuery {
        val compiled = this@compile.compile(serde, query.prepareSearch().withIndex("test"))
        return CompiledSearchQuery(
            params = compiled.parameters,
            body = compiled.body.shouldBeInstanceOf<TestSerializer.ObjectCtx>().toMap(),
        )
    }

    protected fun CountQueryCompiler.compile(query: SearchQuery<*>): CompiledSearchQuery {
        val compiled = this@compile.compile(serde, query.prepareCount().withIndex("test"))
        return CompiledSearchQuery(
            params = compiled.parameters,
            body = compiled.body.shouldBeInstanceOf<TestSerializer.ObjectCtx>().toMap(),
        )
    }
}
