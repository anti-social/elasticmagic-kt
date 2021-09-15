package samples.document.join

import dev.evo.elasticmagic.Document

abstract class BaseQADoc : Document() {
    val id by int()
    val content by text()
    val join by join(relations = mapOf("question" to listOf("answer")))
}

object QuestionDoc : BaseQADoc() {
    val rating by float()
    val title by text()
}

object AnswerDoc : BaseQADoc() {
    val accepted by boolean()
}
