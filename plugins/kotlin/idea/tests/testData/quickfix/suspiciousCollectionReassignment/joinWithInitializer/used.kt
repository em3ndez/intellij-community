// "Join with initializer" "false"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SuspiciousCollectionReassignmentInspection
// ACTION: Change type to mutable
// ACTION: Replace overloaded operator with function call
// ACTION: Replace with ordinary assignment
// WITH_STDLIB
fun test(otherList: List<Int>) {
    var list = createList()
    foo(list)
    list <caret>+= otherList
}

fun createList(): List<Int> = listOf(1, 2, 3)

fun foo(list: List<Int>) {}