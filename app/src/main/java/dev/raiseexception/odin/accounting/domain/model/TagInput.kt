package dev.raiseexception.odin.accounting.domain.model

sealed interface TagInput {
    data class Existing(val tagId: String) : TagInput
    data class New(val tagName: String) : TagInput
}
