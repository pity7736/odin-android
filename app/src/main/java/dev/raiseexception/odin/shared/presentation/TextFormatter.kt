package dev.raiseexception.odin.shared.presentation

fun capitalizeFirst(text: String): String =
    text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
