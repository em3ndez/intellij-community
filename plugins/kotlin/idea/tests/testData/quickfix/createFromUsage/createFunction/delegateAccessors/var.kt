// "Create member functions 'F.getValue', 'F.setValue'" "true"
class F {

}

class X {
    var f: Int by F()<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix