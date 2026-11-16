package app.burrow.money.capture

/**
 * What auto-capture really does right now: what the user turned on ([smsWanted], [appsWanted])
 * against what Android allows ([smsAllowed], [appsAllowed]). One answer for every screen that shows
 * it (More tile, Today's paused note, Auto-capture), so they never disagree. Pure: unit-tested.
 */
data class CaptureStatus(
    val smsWanted: Boolean,
    val appsWanted: Boolean,
    val smsAllowed: Boolean,
    val appsAllowed: Boolean,
    val smsInBuild: Boolean = true,
) {
    /** Bank SMS is being read. */
    val smsOn: Boolean get() = smsInBuild && smsWanted && smsAllowed

    /** Payment app notifications are being read. */
    val appsOn: Boolean get() = appsWanted && appsAllowed

    /** Something is being recorded. */
    val anyOn: Boolean get() = smsOn || appsOn

    /** The user turned something on (SMS only counts where this build reads SMS). */
    val wanted: Boolean get() = (smsInBuild && smsWanted) || appsWanted

    /**
     * Why nothing records although the user turned something on; notification access first (it
     * covers more apps). Null while either source records: Bank SMS alone is enough, Payment apps is
     * optional, so a missing notification access never warns while SMS works.
     */
    val blocked: Blocked?
        get() = when {
            anyOn -> null
            appsWanted && !appsAllowed -> Blocked.NOTIFICATIONS
            smsInBuild && smsWanted && !smsAllowed -> Blocked.SMS
            else -> null
        }

    /** The More tile: On while anything records, Paused when it was turned on but Android blocks it all. */
    val tile: Tile
        get() = when {
            anyOn -> Tile.ON
            wanted -> Tile.PAUSED
            else -> Tile.OFF
        }

    enum class Blocked { NOTIFICATIONS, SMS }
    enum class Tile { ON, PAUSED, OFF }
}
