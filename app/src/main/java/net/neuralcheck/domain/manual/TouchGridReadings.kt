package net.neuralcheck.domain.manual

import net.neuralcheck.domain.manual.TouchGridContract.AbsentReasons

/**
 * How the surface view turns what the platform answers (or refuses) into [Reading]s — the decisions of the environment, in pure Kotlin so a
 * plain test can walk every API level and every failure (the view only supplies the lambdas that call the platform).
 *
 * The rule (22, 21): a field the platform cannot give is ABSENT with its reason — `api_below_<n>` when the API level is the reason, otherwise
 * `read_failed:<the error's message text>` (never the exception class) — and never a default value standing in for it.
 */
object TouchGridReadings {

    /** The first API level of the gesture-inset and exclusion-rect calls (contract 7-ي ف-02: `api_below_29`). */
    const val FIRST_API_GESTURES = 29

    /** The first API level of `RoundedCorner` (design §4.2: `not_available_api` below it). */
    const val FIRST_API_ROUNDED_CORNERS = 31

    /** [read]'s answer, or the failure's message text as the reason. */
    fun <T> attempt(read: () -> T): Reading<T> = try {
        Reading.Value(read())
    } catch (e: Exception) {
        Reading.Absent(AbsentReasons.readFailed(e))
    }

    /** [attempt], unless [sdkInt] is below [minApi]: then `api_below_<minApi>` and the platform is not asked at all. */
    fun <T> sinceApi(sdkInt: Int, minApi: Int, read: () -> T): Reading<T> =
        if (sdkInt < minApi) Reading.Absent(AbsentReasons.apiBelow(minApi)) else attempt(read)

    /** The four radii when the platform has them (API 31+); otherwise the state — exactly one of the two keys is always written. */
    fun roundedCorners(sdkInt: Int, read: () -> RoundedCornerRadii): RoundedCorners =
        if (sdkInt < FIRST_API_ROUNDED_CORNERS) {
            RoundedCorners.State(TouchGridContract.RoundedCornersStates.NOT_AVAILABLE_API)
        } else try {
            RoundedCorners.Radii(read())
        } catch (e: Exception) {
            RoundedCorners.State(TouchGridContract.RoundedCornersStates.readFailed(e))
        }
}
