package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.BaseTest
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.usingIndex

import io.kotest.matchers.types.shouldBeInstanceOf

@Suppress("UnnecessaryAbstractClass")
abstract class BaseCompilerTest<T: BaseCompiler>(
    compilerFactory: (ElasticsearchVersion) -> T
) : BaseTest() {
    protected open val compilers: List<T> = ElasticsearchFeatures.VERSION_FEATURES
        .map { compilerFactory(it.first) }

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
        val compiled = this@compile.compile(serializer, query.usingIndex("test"))
        return CompiledSearchQuery(
            params = compiled.parameters,
            body = compiled.body.shouldBeInstanceOf<TestSerializer.ObjectCtx>().toMap(),
        )
    }
}