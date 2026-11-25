package org.example.slippy.foundation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.StringResource

/** A message for the snackbar: either [text], or [resId] that the UI resolves. */
data class UserMessage(val id: Long, val text: String?, val resId: StringResource?, val isError: Boolean)

/** The queue of messages; App shows the first one, then calls [shown]. */
class MessageStore {
    val messages: StateFlow<List<UserMessage>>
        field = MutableStateFlow(emptyList())

    private var nextId = 0L

    fun show(text: String, isError: Boolean = false) = add(text, null, isError)

    fun show(resId: StringResource, isError: Boolean = false) = add(null, resId, isError)

    fun show(throwable: Throwable) {
        when (throwable) {
            is CancellationException -> return
            is ExceptionWithStringResource -> show(throwable.resId, isError = true)
            is ApiException -> show(errorResource(throwable.code), isError = true)
            else -> {
                log("Unexpected failure: $throwable")
                show(errorResource(-1), isError = true)
            }
        }
    }

    fun shown(message: UserMessage) {
        messages.update { list -> list.filter { it.id != message.id } }
    }

    private fun add(text: String?, resId: StringResource?, isError: Boolean) {
        val message = UserMessage(nextId++, text, resId, isError)
        messages.update { it + message }
    }
}
