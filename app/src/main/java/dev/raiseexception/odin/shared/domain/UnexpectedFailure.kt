package dev.raiseexception.odin.shared.domain

@ConsistentCopyVisibility
data class UnexpectedFailure private constructor(
    val operation: String,
    val failureType: String,
    val causeTypes: List<String>,
    val stackFrames: List<String>,
) {

    companion object {
        fun from(operation: String, failure: Throwable): UnexpectedFailure {
            val causes = causeChainOf(failure)
            return UnexpectedFailure(
                operation = operation,
                failureType = failure.javaClass.name,
                causeTypes = causes.map { it.javaClass.name },
                stackFrames = (listOf(failure) + causes).flatMap { framesOf(it) },
            )
        }

        private fun causeChainOf(failure: Throwable): List<Throwable> {
            val visited = mutableSetOf(failure)
            return generateSequence(failure.cause) { it.cause }
                .takeWhile { visited.add(it) }
                .toList()
        }

        private fun framesOf(failure: Throwable): List<String> =
            failure.stackTrace.map { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
    }
}
