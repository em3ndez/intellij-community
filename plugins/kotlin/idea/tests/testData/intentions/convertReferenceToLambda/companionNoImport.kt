// WITH_STDLIB

val list = listOf(1, 2, 3).map(<caret>Utils.Companion::foo)

class Utils {
    companion object {
        fun foo(x: Int) = x
    }
}

// IGNORE_K2