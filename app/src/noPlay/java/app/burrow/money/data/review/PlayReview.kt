// Same signature as the play build's object, whose parameter this stand-in doesn't need.
@file:Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")

package app.burrow.money.data.review

import android.app.Activity

/** The foss and github builds have no Google Play code: this stands in for the play build's object. */
object PlayReview {
    const val enabled = false

    suspend fun launch(activity: Activity): Boolean = false
}
