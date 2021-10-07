package samples.querying

import dev.evo.elasticmagic.Sort

// Sort by user id with ascending order
val sortedByIdQuery = q.sort(UserDoc.id)

// Sort by user id descending
val sortedByIdDescQuery = q.sort(UserDoc.id.desc())

// Sort by several criteria
val multipleSortedQuery = q.sort(UserDoc.isActive.desc(), UserDoc.id.asc())

// Use Sort explicitly to customize sort behaviour
val sortedByRatingQuery = q.sort(Sort(UserDoc.rating, missing = Sort.Missing.First))
