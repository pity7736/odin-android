package dev.raiseexception.odin.shared.presentation

import dev.raiseexception.odin.accounting.domain.TagNameError
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.shared.domain.Outcome

data class SelectedTag(val name: String, val input: TagInput)

data class TagSelection(
    val selected: List<SelectedTag> = emptyList(),
    val text: String = "",
    val error: String? = null
) {

    val isAtLimit: Boolean get() = this.selected.size >= Expense.MAX_TAGS

    val inputs: List<TagInput> get() = this.selected.map { it.input }

    fun withText(text: String): TagSelection = this.copy(text = text, error = null)

    fun withTextAdded(allTags: List<Tag>): TagSelection {
        val trimmedName = when (val validation = Tag.validateName(this.text)) {
            is Outcome.Success -> validation.value
            is Outcome.Failure -> return this.withNameError(validation.error as TagNameError)
        }
        val normalizedName = Tag.normalize(trimmedName)
        if (this.isAtLimit || this.contains(normalizedName)) return this.copy(text = "", error = null)
        val existingTag = allTags.firstOrNull { it.normalizedName == normalizedName }
        val addedTag = existingTag?.let { SelectedTag(it.name, TagInput.Existing(it.id)) }
            ?: SelectedTag(trimmedName, TagInput.New(trimmedName))
        return this.withSelected(this.selected + addedTag)
    }

    fun withTagPicked(tag: Tag): TagSelection {
        if (this.isAtLimit || this.contains(tag.normalizedName)) return this.copy(text = "", error = null)
        return this.withSelected(this.selected + SelectedTag(tag.name, TagInput.Existing(tag.id)))
    }

    fun withTagRemoved(index: Int): TagSelection =
        this.withSelected(this.selected.filterIndexed { selectedIndex, _ -> selectedIndex != index }, this.text)

    fun suggestions(allTags: List<Tag>): List<Tag> {
        val query = Tag.normalize(this.text)
        return allTags
            .filter { it.normalizedName.contains(query) && !this.contains(it.normalizedName) }
            .sortedWith(Tag.ALPHABETICAL_ORDER)
    }

    private fun withNameError(nameError: TagNameError): TagSelection =
        if (nameError is TagNameError.Blank) this else this.copy(error = nameError.externalMessage)

    private fun contains(normalizedName: String): Boolean =
        this.selected.any { Tag.normalize(it.name) == normalizedName }

    private fun withSelected(selected: List<SelectedTag>, text: String = ""): TagSelection =
        TagSelection(selected = selected, text = text)

    companion object {
        fun of(savedTags: List<Tag>): TagSelection {
            val selected = savedTags
                .sortedWith(Tag.ALPHABETICAL_ORDER)
                .map { SelectedTag(it.name, TagInput.Existing(it.id)) }
            return TagSelection().withSelected(selected)
        }
    }
}
