package dev.evo.elasticmagic

/**
 * Refresh controls when changes will be searchable.
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-refresh.html>
 */
enum class Refresh : ToValue<String> {
    /**
     * Refresh primary and replica shards immediately after the operation.
     */
    TRUE,

    /**
     * Refresh will be done asynchronously when the `index.refresh_interval` time comes.
     * It is the default behaviour.
     */
    FALSE,

    /**
     * Wait when changes are made visible to search queries.
     */
    WAIT_FOR;

    override fun toValue() = name.lowercase()
}

/**
 * Action on conflicts.
 */
enum class Conflicts : ToValue<String> {
    ABORT, PROCEED;

    override fun toValue() = name.lowercase()
}
