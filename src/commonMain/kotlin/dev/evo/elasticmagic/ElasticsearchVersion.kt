package dev.evo.elasticmagic

data class ElasticsearchVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<ElasticsearchVersion> {
    override fun compareTo(other: ElasticsearchVersion): Int = when {
        major > other.major -> 1
        major < other.major -> -1
        minor > other.minor -> 1
        minor < other.minor -> -1
        patch > other.patch -> 1
        patch < other.patch -> -1
        else -> 0
    }
}
