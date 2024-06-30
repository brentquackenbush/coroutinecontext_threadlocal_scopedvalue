import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

class TransactionCoroutineContext(
    private val transactionResources: Map<String, String>
) : ThreadContextElement<Map<String, String>?> {

    companion object Key : CoroutineContext.Key<TransactionCoroutineContext>

    override val key: CoroutineContext.Key<TransactionCoroutineContext>
        get() = Key

    /**
     * Update the thread context with the provided transaction resources.
     * @param context The coroutine context.
     * @return The previous transaction resources.
     */
    override fun updateThreadContext(context: CoroutineContext): Map<String, String> {
        val previousResources = currentTransactionResources.get()
        currentTransactionResources.set(transactionResources)
        return previousResources ?: emptyMap()
    }

    /**
     * Restore the previous thread context.
     * @param context The coroutine context.
     * @param oldState The previous transaction resources.
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String>?) {
        if (oldState.isNullOrEmpty()) {
            currentTransactionResources.remove()
        } else {
            currentTransactionResources.set(oldState)
        }
    }
}

// Define a ThreadLocal to hold the current transaction resources
private val currentTransactionResources = ThreadLocal<Map<String, String>>()

fun main() {
    runBlocking {
        // Define a task to be run within a coroutine
        val task: suspend () -> Unit = {
            try {
                val transactionId = currentTransactionResources.get()["transactionId"]
                println("Transaction started: $transactionId")
                println("Running task in transaction: $transactionId")

                // Simulate some work with a delay
                delay(100)

                println("Transaction ended: $transactionId")
            } catch (e: Exception) {
                throw e
            }
        }

        // Launch coroutines with different transaction contexts
        launch(TransactionCoroutineContext(mapOf("transactionId" to "TXN-1"))) {
            task()
        }
        launch(TransactionCoroutineContext(mapOf("transactionId" to "TXN-2"))) {
            task()
        }
    }
}
