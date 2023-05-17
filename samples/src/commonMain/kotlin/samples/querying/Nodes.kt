package samples.querying

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.MappingField
import dev.evo.elasticmagic.doc.ObjectBoundField
import dev.evo.elasticmagic.doc.SubDocument
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.DisMax
import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.query.match
import dev.evo.elasticmagic.query.NodeHandle
import dev.evo.elasticmagic.query.QueryExpressionNode

import kotlin.random.Random

class TranslationDoc(field: ObjectBoundField) : SubDocument(field) {
    val en by text()
    val de by text()
    val ru by text()
}

object QuestionDoc : Document() {
    val title by obj(::TranslationDoc)
    val text by obj(::TranslationDoc)
    val rating by float()
    val votes by int()
}

val scoringHandle = NodeHandle<FunctionScore>()
val langHandle = NodeHandle<DisMax>()

val searchTerm = "hello world"
val skeletonQuery = SearchQuery(
    QueryExpressionNode(
        scoringHandle,
        FunctionScore(
            QueryExpressionNode(
                langHandle,
                DisMax(
                    queries = listOf(
                        Bool.should(
                            QuestionDoc.title.en.match(searchTerm),
                            QuestionDoc.text.en.match(searchTerm),
                        )
                    ),
                )
            ),
            functions = listOf(
                FunctionScore.FieldValueFactor(QuestionDoc.rating),
            ),
            scoreMode = FunctionScore.ScoreMode.MULTIPLY,
        )
    )
)

val boostByNumberOfVotes = Random.nextBoolean()
var boostedQuery = if (boostByNumberOfVotes) {
    skeletonQuery.queryNode(scoringHandle) { node ->
        node.copy(
            functions = node.functions + listOf(
                FunctionScore.Weight(
                    1.1F,
                    filter = QuestionDoc.votes.range(gte = 10, lt = 50)
                ),
                FunctionScore.Weight(
                    1.5F,
                    filter = QuestionDoc.votes.gte(50)
                ),
            )
        )
    }
} else {
    skeletonQuery
}

val additionalLanguages = listOf("de", "ru")
val userLang = additionalLanguages[Random.nextInt(additionalLanguages.size)]
val additionalLangFields: List<MappingField<String>> = listOfNotNull(
    QuestionDoc.title.getFieldByName(userLang),
    QuestionDoc.text.getFieldByName(userLang),
)
val langQuery = if (additionalLangFields.isNotEmpty()) {
    boostedQuery.queryNode(langHandle) { node ->
        node.copy(
            queries = node.queries + listOf(
                Bool.should(
                    *additionalLangFields.map { it.match(searchTerm) }.toTypedArray()
                )
            )
        )
    }
} else {
    boostedQuery
}
