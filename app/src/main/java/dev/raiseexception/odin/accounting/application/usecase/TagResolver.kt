package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.TagNameError
import dev.raiseexception.odin.accounting.domain.TagResolutionError
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.accounting.domain.repository.TagRepository
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Clock

class TagResolver(
    private val tagRepository: TagRepository,
    private val clock: Clock = Clock.System
) {

    suspend fun resolve(tagInputs: List<TagInput>): Outcome<List<String>> {
        val resolvedTagIds = mutableListOf<String>()
        val tagIdsByNormalizedName = mutableMapOf<String, String>()
        for (tagInput in tagInputs) {
            val resolution = when (tagInput) {
                is TagInput.Existing -> this.resolveExisting(tagInput.tagId)
                is TagInput.New -> this.resolveNew(tagInput.tagName, tagIdsByNormalizedName)
            }
            when (resolution) {
                is Outcome.Failure -> return resolution
                is Outcome.Success -> resolution.value?.let { resolvedTagIds.add(it) }
            }
        }
        return Outcome.Success(resolvedTagIds.toList())
    }

    private suspend fun resolveExisting(tagId: String): Outcome<String?> =
        when (val lookupOutcome = this.tagRepository.findById(tagId)) {
            is Outcome.Failure -> this.storageFailure(lookupOutcome.error)
            is Outcome.Success -> lookupOutcome.value?.let { Outcome.Success(it.id) }
                ?: Outcome.Failure(TagResolutionError.StorageFailure(internalMessage = "Tag with id $tagId not found"))
        }

    private suspend fun resolveNew(
        tagName: String,
        tagIdsByNormalizedName: MutableMap<String, String>
    ): Outcome<String?> {
        val trimmedName = when (val validation = Tag.validateName(tagName)) {
            is Outcome.Success -> validation.value
            is Outcome.Failure -> return this.invalidName(validation.error as TagNameError)
        }
        val normalizedName = Tag.normalize(trimmedName)
        val alreadyResolvedTagId = tagIdsByNormalizedName[normalizedName]
        if (alreadyResolvedTagId != null) return Outcome.Success(alreadyResolvedTagId)
        val resolution = this.findOrAdd(trimmedName, normalizedName)
        if (resolution is Outcome.Success) tagIdsByNormalizedName[normalizedName] = resolution.value
        return resolution
    }

    private suspend fun findOrAdd(trimmedName: String, normalizedName: String): Outcome<String> {
        val existingTag = when (val lookupOutcome = this.tagRepository.findByNormalizedName(normalizedName)) {
            is Outcome.Success -> lookupOutcome.value
            is Outcome.Failure -> return this.storageFailure(lookupOutcome.error)
        }
        if (existingTag != null) return Outcome.Success(existingTag.id)
        val newTag = (Tag.create(trimmedName, this.clock) as Outcome.Success).value
        return when (val addOutcome = this.tagRepository.add(newTag)) {
            is Outcome.Success -> Outcome.Success(newTag.id)
            is Outcome.Failure -> this.storageFailure(addOutcome.error)
        }
    }

    private fun invalidName(nameError: TagNameError): Outcome<String?> =
        if (nameError is TagNameError.Blank) {
            Outcome.Success(null)
        } else {
            Outcome.Failure(TagResolutionError.InvalidName(nameError))
        }

    private fun storageFailure(error: DomainError): Outcome.Failure =
        Outcome.Failure(TagResolutionError.StorageFailure(internalMessage = error.internalMessage))
}
