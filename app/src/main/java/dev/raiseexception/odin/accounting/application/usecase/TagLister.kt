package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.repository.TagRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow

class TagLister(private val tagRepository: TagRepository) {

    fun list(): Flow<Outcome<List<Tag>>> = this.tagRepository.getAll()
}
