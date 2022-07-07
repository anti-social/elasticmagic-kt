package dev.evo.elasticmagic

sealed class Version<T: Version<T>> : Comparable<T> {
    companion object {
        fun compareVersions(
            major: Int, minor: Int, patch: Int,
            otherMajor: Int, otherMinor: Int, otherPatch: Int
        ): Int = when {
            major > otherMajor -> 1
            major < otherMajor -> -1
            minor > otherMinor -> 1
            minor < otherMinor -> -1
            patch > otherPatch -> 1
            patch < otherPatch -> -1
            else -> 0
        }
    }

    data class Elasticsearch(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Version<Elasticsearch>() {
        override fun compareTo(other: Elasticsearch): Int {
            return compareVersions(major, minor, patch, other.major, other.minor, other.patch)
        }
    }

    data class Opensearch(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Version<Opensearch>() {
        override fun compareTo(other: Opensearch): Int {
            return compareVersions(major, minor, patch, other.major, other.minor, other.patch)
        }
    }
}
