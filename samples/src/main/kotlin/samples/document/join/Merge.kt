package samples.document.join

import dev.evo.elasticmagic.doc.mergeDocuments

val QAMapping = mergeDocuments(QuestionDoc, AnswerDoc)
