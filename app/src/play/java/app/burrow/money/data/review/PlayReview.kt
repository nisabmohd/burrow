package app.burrow.money.data.review

import android.app.Activity
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManagerFactory
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google Play's In-App Review (play build; as Weft): Play's own rating card shows over the app. Play
 * decides whether it really shows (it has a quota), so this only says whether the flow could start.
 */
object PlayReview {
    const val enabled = true

    suspend fun launch(activity: Activity): Boolean {
        val manager = ReviewManagerFactory.create(activity)
        val info = manager.requestReviewFlow().awaitOrNull() ?: return false
        manager.launchReviewFlow(activity, info).awaitOrNull()
        return true
    }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
    }
}
