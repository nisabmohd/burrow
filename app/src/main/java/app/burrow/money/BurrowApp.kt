package app.burrow.money

import android.app.Application

class BurrowApp : Application() {
    /** Created lazily; nothing heavy runs before the first frame. */
    val container by lazy { AppContainer(this) }
}
