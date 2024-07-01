# Mastering Concurrency with Java and Kotlin: ThreadLocal, ScopedValues, and CoroutineContext Compared

> Concurrency management is a critical aspect of modern software development, especially in environments where multiple tasks need to be executed simultaneously.

Java and Kotlin provide several tools that assist in working with concurrency by enabling data sharing across different contexts and ensuring proper propagation of information once processes complete. Among these tools are [ThreadLocal](https://www.baeldung.com/java-threadlocal), [ScopedValues](https://openjdk.org/jeps/446) (_introduced as a preview feature in Java 21_), and [CoroutineContext](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html) (_Kotlin_). Understanding these tools and their differences is essential for writing efficient and maintainable concurrent applications using these two languages.

For those unfamiliar with Kotlin, it is built upon the JVM and can seamlessly use Java libraries. The Kotlin Compiler compiles Kotlin source code into Java bytecode, which is then executed by the JVM. For this article, we will use the same example to demonstrate the use of ThreadLocal, ScopedValues, and CoroutineContext.

## Why Use Thread-Specific Variables Instead of Method Arguments?
In many concurrent applications, there is a need to share context-specific data across different components of the application without cluttering the method signatures with additional parameters. This approach simplifies method calls and makes the code cleaner and more maintainable.

One practical example is the `TransactionSynchronizationManager` in Spring Boot. This utility manages transaction resources and synchronizations per thread, ensuring that resources like JDBC connections or Hibernate sessions are correctly managed and shared within the context of a transaction. Without such a mechanism, every method involved in the transaction would need to explicitly receive and pass these resources as parameters, leading to cumbersome and error-prone code.

## Sharing Context Across Threads with Spring Boot's `TransactionSynchronizationManager`

Before diving into the specifics of ThreadLocal, ScopedValues, and CoroutineContext, let's look at a practical example of context sharing in a real-world application using Spring Boot. The [`TransactionSynchronizationManager`](https://github.com/spring-projects/spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/support/TransactionSynchronizationManager.java) is a powerful utility that helps manage transaction resources and synchronizations per thread in Spring applications.

### What is TransactionSynchronizationManager?

**TransactionSynchronizationManager** manages transaction resources and synchronizations per thread. It ensures that resources like JDBC connections or Hibernate sessions are correctly managed and shared across different threads within the context of a transaction. This manager binds resources to the current thread at the beginning of a transaction and unbinds them upon completion, ensuring proper resource cleanup and preventing leaks.

## ThreadLocal

Let's kick things off with the `ThreadLocal` construct from the _java.lang_ package. It's the dinosaur here, having roamed the Java landscape for nearly 25 years (considering it's June 2024 as this article is written). Despite its age, ThreadLocal remains a handy tool in our concurrency toolbox.

### What is ThreadLocal?

ThreadLocal provides a way to maintain variables that are accessible only to the current thread. Unlike normal variable storage, where variables are shared among threads, ThreadLocal variables are unique to each thread and are not shared. This feature makes ThreadLocal particularly useful for managing context-specific data throughout a request's lifecycle in a web application.

### How Does ThreadLocal Work?

A **ThreadLocal** is a variable that's local to a thread—hence the name. Each thread accessing a ThreadLocal has its own independent copy of the variable. You interact with a **ThreadLocal** using its `get()` and `set()` methods to retrieve and set the value, respectively. This approach avoids the need to pass variables as method arguments, effectively serving as hidden method arguments.

Think of it this way: each thread can set and get its own value without worrying about other threads. Pretty simple, right? But there are a few things to keep in mind:

- **Memory Footprint**: If you have many ThreadLocal variables and you spawn a new thread, you might need to copy these variables to the new thread, potentially causing a big memory footprint.

- **Mutable Values**: Although the ThreadLocal itself is immutable, the values it stores are not. Users can still call set() to change the value.

- **Lifetime Management**: ThreadLocal variables can live as long as the thread lives unless you explicitly remove them using the remove() method. If you forget to do this, it can lead to memory leaks.

- **Child Threads**: There is a special type of ThreadLocal called `InheritableThreadLocal`, which is designed to carry ThreadLocal state from parent threads to child threads. However, any changes to the parent thread's value do not propagate to the child thread, and vice versa.

Here's a simple example to illustrate how ThreadLocal works: [Github Repo](https://github.com/brentquackenbush/coroutinecontext_threadlocal_scopedvalue/blob/main/ThreadLocal/src/main/kotlin/Main.kt)

```kotlin
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
```

## ScopedValue

ScopedValues, introduced as a preview feature in Java 21, provide a way to manage variables within a specific scope of execution. This feature is designed to be safer and more efficient than ThreadLocal for passing data in a controlled manner. ScopedValues ensure that data is only available within a defined scope, making it easier to manage and preventing potential memory leaks.

### What is ScopedValues?

ScopedValues offer a mechanism to bind values to a specific scope of execution within a thread. Unlike ThreadLocal, which provides a thread-wide context, ScopedValues are confined to a particular block of code. This makes them particularly useful for managing temporary context data that needs to be cleaned up automatically after execution.

### How Do ScopedValues Work?

ScopedValues work by creating a new scope in which the value is available. You interact with a ScopedValue using its enter() method to set the value and get() method to retrieve it. This ensures that the value is only accessible within the defined scope and is automatically cleaned up after the scope is exited.

Think of it this way: ScopedValues allow you to set and get values within a specific block of code, ensuring that the values are confined to that block and do not leak outside. Pretty straightforward, right? But there are a few things to consider:

- **Scoped Execution**: ScopedValues are only available within the block where they are defined. Once the block is exited, the values are automatically cleaned up.

- **Memory Management**: By confining values to a specific scope, ScopedValues help prevent memory leaks that can occur with ThreadLocal if not properly managed.

- **Immutability**: While the ScopedValue itself is immutable, the values it holds can still be changed within the scope. This allows for flexibility in managing context-specific data.

Here's a simple example to illustrate how ScopedValues work: [Github Repo](https://github.com/brentquackenbush/coroutinecontext_threadlocal_scopedvalue/blob/main/ScopedValue/src/main/kotlin/Main.kt)

```kotlin
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
```

## CoroutineContext

Kotlin's CoroutineContext provides a powerful way to manage variables and context-specific data within coroutines. Unlike ThreadLocal and ScopedValues, which are more thread-focused, CoroutineContext is designed specifically for coroutines, offering a more flexible and efficient way to handle concurrency.

### What is CoroutineContext?

CoroutineContext is a fundamental concept in Kotlin's coroutines. It provides a context for the lifecycle of a coroutine, allowing you to manage various aspects of coroutine execution, such as job cancellation, dispatcher, and thread-local data. CoroutineContext enables structured concurrency, ensuring that coroutine hierarchies are managed cleanly and efficiently.

### How Does CoroutineContext Work?
CoroutineContext works by allowing you to attach context elements to a coroutine. These elements can include information about the coroutine's execution environment, such as a dispatcher, and any user-defined context like transaction data. When a coroutine is launched, it inherits its parent's context, making it easy to propagate context-specific data.

Think of it this way: **CoroutineContext** allows you to attach additional information to a coroutine, ensuring that this information is available throughout the coroutine's lifecycle. Pretty neat, right? Here are a few things to consider:

- **Context Propagation**: CoroutineContext elements are automatically propagated to child coroutines, ensuring consistent context across coroutine hierarchies.

- **Structured Concurrency**: CoroutineContext enables structured concurrency, which helps manage coroutine lifecycles and prevents common pitfalls like resource leaks.

- **Flexibility**: You can define custom context elements to store and manage any context-specific data required by your application.

Here's a simple example to illustrate how CoroutineContext works: [Github Repo](https://github.com/brentquackenbush/coroutinecontext_threadlocal_scopedvalue/blob/main/CoroutineContext/src/main/kotlin/Main.kt)

```kotlin
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
```

## In-Depth Comparison

### Purpose

**ThreadLocal**:
- **Purpose**: `ThreadLocal` is a construct designed to store data specific to each thread. Each thread accessing a `ThreadLocal` variable has its own, independent copy of that variable.
- **Use Cases**: `ThreadLocal` shines in scenarios where data must be isolated to individual threads. Think of user sessions in a web application, where each thread handles a unique session and needs its own set of data. It's also invaluable in maintaining context information such as database connections and transaction IDs, which must remain thread-confined to avoid race conditions and ensure thread safety.

**ScopedValues**:
- **Purpose**: Introduced as a preview feature in Java 21, `ScopedValues` aim to manage variables within a specific execution scope. This feature is tailored to provide a safer, more efficient alternative to `ThreadLocal` for passing data in a controlled manner.
- **Use Cases**: Ideal for managing temporary context data that needs to be cleaned up after execution. This makes `ScopedValues` perfect for scenarios like temporary configurations or contextual data that must be confined to a block of code, ensuring automatic cleanup and preventing memory leaks.

**CoroutineContext**:
- **Purpose**: `CoroutineContext` is fundamental to Kotlin’s coroutine system, managing various aspects of coroutine execution such as job control, dispatcher specification, and user-defined context data.
- **Use Cases**: Perfect for structured concurrency in coroutine-based applications. `CoroutineContext` ensures that data can be propagated automatically within coroutine hierarchies, making it suitable for managing coroutine-specific data like transaction contexts or user sessions, especially in large-scale, concurrent systems.

### Scope and Lifetime

**ThreadLocal**:
- **Scope**: A `ThreadLocal` variable is limited to the thread it is created in. Each thread maintains its own instance of the variable, providing thread-specific data isolation.
- **Lifetime**: The variable lives as long as the thread lives. However, developers must manually remove the variable using the `remove()` method to prevent memory leaks, particularly in environments with thread pools or long-lived threads.

**ScopedValues**:
- **Scope**: `ScopedValues` are confined to the specific code block they are defined in. Once the block is exited, the values are no longer accessible.
- **Lifetime**: Automatically cleaned up at the end of the execution block. This automatic cleanup mechanism ensures no lingering data, reducing the risk of memory leaks and simplifying memory management compared to `ThreadLocal`.

**CoroutineContext**:
- **Scope**: Extends to the lifetime of the coroutine and its child coroutines. Context elements are inherited by child coroutines, ensuring consistent context management across coroutine hierarchies.
- **Lifetime**: The context elements are cleaned up when the coroutine and all its children complete. This lifecycle management supports structured concurrency and efficient resource management, promoting clean and maintainable code in complex concurrent applications.

### Memory Management

**ThreadLocal**:
- **Memory Management**: Each thread maintains its own copy of the variable, leading to potentially high memory usage, especially with many threads.
- **Considerations**: Memory leaks are a significant risk if variables are not explicitly removed when no longer needed. This is particularly problematic in thread pools, where threads are reused, and old data might inadvertently persist across tasks.

**ScopedValues**:
- **Memory Management**: Confines variables to a specific scope, ensuring efficient memory usage by automatically cleaning up variables after the execution block.
- **Considerations**: This design greatly reduces the risk of memory leaks and simplifies memory management, providing a robust alternative to `ThreadLocal` for managing short-lived data.

**CoroutineContext**:
- **Memory Management**: Managed within the coroutine’s lifecycle, with automatic propagation and cleanup of context elements.
- **Considerations**: Efficiently manages context data within coroutines, minimizing memory leaks and ensuring data integrity. The structured concurrency model of `CoroutineContext` supports robust and scalable concurrent programming practices.

### Data Propagation

**ThreadLocal**:
- **Propagation**: Variables are confined to the thread and do not propagate to child threads unless `InheritableThreadLocal` is used.
- **Limitations**: Changes in parent thread values do not propagate to child threads, and vice versa. This limitation can complicate the design of applications requiring context propagation across thread boundaries, necessitating careful design and management.

**ScopedValues**:
- **Propagation**: Variables are confined to the specific execution block, ensuring no unintended propagation outside the scope.
- **Limitations**: This strict confinement simplifies reasoning about data flow and lifecycle management, making `ScopedValues` ideal for managing execution-specific data within a well-defined context.

**CoroutineContext**:
- **Propagation**: Context elements are automatically propagated to child coroutines, ensuring consistent context across coroutine hierarchies.
- **Advantages**: Facilitates structured concurrency, making it easier to manage context-specific data within coroutine-based applications. The automatic propagation mechanism supports clean and maintainable code, particularly in large-scale, concurrent systems.

### Conclusion

While `ThreadLocal`, `ScopedValues`, and `CoroutineContext` all serve the purpose of managing context-specific data in concurrent environments, they each bring unique strengths and address specific challenges:

- **ThreadLocal**: A reliable solution for thread-specific data but requires careful management to avoid memory leaks. Its inability to propagate data easily to child threads can complicate design in more complex concurrency scenarios.
- **ScopedValues**: Provide a safer and more efficient alternative for temporary data within specific execution scopes. The automatic cleanup reduces the risk of memory leaks, and the scoped design ensures clean and predictable data management.
- **CoroutineContext**: Offers a flexible and powerful way to manage context-specific data within coroutines. It supports structured concurrency and efficient context propagation, making it ideal for coroutine-based applications that require robust concurrency management.
