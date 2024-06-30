
object SimpleTransactionManagerScoped {
    // Define a ScopedValue to hold transaction resources as a Map
    private val transactionResources: ScopedValue<Map<String, String>> = ScopedValue.newInstance()

    /**
     * Begins a new transaction for the current thread.
     * @param transactionId The ID of the transaction to begin.
     */
    fun beginTransaction(transactionId: String) {
        // Create a map to hold transaction resources
        val resources = mapOf("transactionId" to transactionId)

        // Use ScopedValue to set the transaction resources within a scope
        ScopedValue.runWhere(transactionResources, resources) {
            println("Transaction started: $transactionId")
            println("Running task in transaction: ${transactionResources.get()["transactionId"]}")

            // Simulate some work with a delay
            Thread.sleep(100)
        }

        println("Transaction ended: $transactionId")
    }
}

fun main(args: Array<String>) {
    // Define a task to be run in separate threads
    val task = Runnable {
        SimpleTransactionManagerScoped.beginTransaction(Thread.currentThread().name)
    }

    // Create and start threads to run the task
    val thread1 = Thread(task, "TXN-1")
    val thread2 = Thread(task, "TXN-2")

    thread1.start()
    thread2.start()

    // Wait for threads to finish
    thread1.join()
    thread2.join()
}
