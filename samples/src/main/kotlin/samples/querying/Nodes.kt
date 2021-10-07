package samples.querying

import dev.evo.elasticmagic.BaseDocSource
import dev.evo.elasticmagic.Bool
import dev.evo.elasticmagic.DisMaxNode
import dev.evo.elasticmagic.BoundField
import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.FunctionScore
import dev.evo.elasticmagic.FunctionScoreNode
import dev.evo.elasticmagic.NodeHandle
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SubDocument

import kotlin.random.Random

class TranslationDoc(field: BoundField<BaseDocSource>) : SubDocument(field) {
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

val scoringHandle = NodeHandle<FunctionScoreNode>()
val langHandle = NodeHandle<DisMaxNode>()

val searchTerm = "hello world"
val skeletonQuery = SearchQuery(
    FunctionScoreNode(
        scoringHandle,
        DisMaxNode(
            langHandle,
            queries = listOf(
                Bool.should(
                    QuestionDoc.title.en.match(searchTerm),
                    QuestionDoc.text.en.match(searchTerm),
                )
            ),
        ),
        functions = listOf(
            FunctionScore.FieldValueFactor(QuestionDoc.rating),
        ),
        scoreMode = FunctionScore.ScoreMode.MULTIPLY,
    )
)

val boostByNumberOfVotes = Random.nextBoolean()
var boostedQuery = if (boostByNumberOfVotes) {
    skeletonQuery.queryNode(scoringHandle) { node ->
        node.functions += listOf(
            FunctionScore.Weight(
                1.1,
                filter = QuestionDoc.votes.range(gte = 10, lt = 50)
            ),
            FunctionScore.Weight(
                1.5,
                filter = QuestionDoc.votes.gte(50)
            ),
        )
    }
} else {
    skeletonQuery
}

val additionalLanguages = listOf("de", "ru")
val userLang = additionalLanguages[Random.nextInt(additionalLanguages.size)]
val additionalLangFields = listOfNotNull(QuestionDoc.title[userLang], QuestionDoc.text[userLang])
val langQuery = if (additionalLangFields.isNotEmpty()) {
    boostedQuery.queryNode(langHandle) { node ->
        node.queries.add(
            Bool.should(
                *additionalLangFields.map { it.match(searchTerm) }.toTypedArray()
            )
        )
    }
} else {
    boostedQuery
}
