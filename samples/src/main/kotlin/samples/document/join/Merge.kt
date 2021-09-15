package samples.document.join

import dev.evo.elasticmagic.mergeDocuments

val QAMapping = mergeDocuments(QuestionDoc, AnswerDoc)
