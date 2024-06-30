import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap

object SimpleTransactionManager {
    private val transactionResources = ThreadLocal.withInitial { ConcurrentHashMap<String, String>() }

    /**
     * Begins a new transaction for the current thread.
     * @param transactionId The ID of the transaction to begin.
     */
    fun beginTransaction(transactionId: String) {
        val resources = ConcurrentHashMap<String, String>()
        resources["transactionId"] = transactionId
        transactionResources.set(resources)
        println("Transaction started: $transactionId")
    }

    /**
     * Retrieves the transaction ID for the current thread.
     * @return The transaction ID, or null if no transaction is active.
     */
    fun getTransactionId(): String? {
        return transactionResources.get()["transactionId"]
    }

    /**
     * Ends the current transaction for the thread.
     * Ensures that resources are cleaned up properly.
     */
    fun endTransaction() {
        val transactionId = getTransactionId() ?: throw IllegalArgumentException("Trying to end transaction that's not there.")

        transactionResources.remove()
        println("Transaction ended: $transactionId")
    }
}

fun main() {
    val task = Runnable {
        try {
            val transactionId = "TXN-${Thread.currentThread().name}"
            SimpleTransactionManager.beginTransaction(transactionId)

            println("Running task in transaction: ${SimpleTransactionManager.getTransactionId()}")
            // Simulate some work with a delay
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            SimpleTransactionManager.endTransaction()
        }
    }

    val thread1 = Thread(task, "1")
    val thread2 = Thread(task, "2")

    thread1.start()
    thread2.start()

    thread1.join()
    thread2.join()
}
