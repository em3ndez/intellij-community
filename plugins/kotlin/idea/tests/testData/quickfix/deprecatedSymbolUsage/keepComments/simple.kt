// "Replace with 'newFun(p)'" "true"

class X {
    @Deprecated("", ReplaceWith("newFun(p)"))
    fun oldFun(p: Int) {
        newFun(p)
    }

    fun newFun(p: Int){}
}

fun foo(x: X) {
    x/*receiver*/.<caret>oldFun(1/*parameter*/)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix