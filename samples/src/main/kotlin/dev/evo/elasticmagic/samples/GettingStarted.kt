package dev.evo.elasticmagic.samples

import dev.evo.elasticmagic.Document

object UserDoc : Document() {
    val id by int()
}

fun gettingStarted() {
    println("Getting started")
}
