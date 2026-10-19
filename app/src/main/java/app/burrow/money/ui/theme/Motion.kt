package app.burrow.money.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation.NavBackStackEntry

/*
 * Motion, the Material 3 way (docs/MOTION.md). Every transition in the app comes from here.
 * Rules: one motion per kind of change; nothing loops; numbers and charts animate once.
 * Respect "Remove animations": read Settings.Global.ANIMATOR_DURATION_SCALE and skip when 0.
 */
object Easing {
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

object Duration {
    const val SHORT = 150
    const val MEDIUM = 300
    const val LONG = 450
}

fun <T> standardSpec(ms: Int = Duration.MEDIUM): FiniteAnimationSpec<T> = tween(ms, easing = Easing.standard)

/** Shared axis X: pushing a screen (Today → Accounts) and back. 30 dp slide + fade. */
private const val AXIS_SHIFT = 30

typealias Enter = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
typealias Exit = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

object NavMotion {
    val push: Enter = {
        slideInHorizontally(tween(Duration.LONG, easing = Easing.emphasizedDecelerate)) { (it * AXIS_SHIFT / 400) } +
            fadeIn(tween(Duration.MEDIUM, delayMillis = 60))
    }
    val pushExit: Exit = {
        slideOutHorizontally(tween(Duration.LONG, easing = Easing.emphasizedAccelerate)) { -(it * AXIS_SHIFT / 400) } +
            fadeOut(tween(Duration.SHORT))
    }
    val pop: Enter = {
        slideInHorizontally(tween(Duration.LONG, easing = Easing.emphasizedDecelerate)) { -(it * AXIS_SHIFT / 400) } +
            fadeIn(tween(Duration.MEDIUM, delayMillis = 60))
    }
    val popExit: Exit = {
        slideOutHorizontally(tween(Duration.LONG, easing = Easing.emphasizedAccelerate)) { (it * AXIS_SHIFT / 400) } +
            fadeOut(tween(Duration.SHORT))
    }

    /** Fade through: switching bottom tabs. Old fades out fast, new fades + scales in from 92%. */
    val tabEnter: Enter = { fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.92f) }
    val tabExit: Exit = { fadeOut(tween(90)) }
}

/** For AnimatedContent inside a screen (Plan's Bills/Budgets/Goals, filter results). */
fun fadeThrough(): ContentTransform =
    (fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.96f)) togetherWith fadeOut(tween(90))

/** Count-up for hero numbers: once, on first show, 600 ms standard. Not on every recomposition. */
const val COUNT_UP_MS = 600

/** Chart bars grow from 0 with a 40 ms stagger, once per data set. */
const val BAR_STAGGER_MS = 40

