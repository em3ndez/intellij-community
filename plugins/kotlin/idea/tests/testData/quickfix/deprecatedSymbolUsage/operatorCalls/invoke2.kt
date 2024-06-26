// "Replace with 'Foo.execute(action)'" "true"

class Executor {
    @Deprecated("Use Executor.execute(Runnable) instead.", ReplaceWith("Foo.execute(action)"))
    operator fun invoke(action: () -> Unit) {}

    fun execute(action: () -> Unit) {}
}

object Foo {
    fun execute(action: () -> Unit) {}
}

fun usage(executor: Executor) {
    <caret>executor {
        // do something
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix