// "Create class 'Foo'" "true"

class A(n: Int)

fun test() = <caret>Foo(abc = 1, ghi = A(2), def = "s")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction