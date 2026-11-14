package net.neuralcheck.hardware.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one-bit channel on which [SensorTests] asks the technician to cover the light
 * sensor (fieldsVersion 69, contract docs/FV68_CONTRACT.md section 7-h (c), SN-04).
 *
 * The test runs inside the scan service; the screen that can say «غطِّ أعلى الشاشة بيدك»
 * is [net.neuralcheck.presentation.screen.ActiveScanScreen]. A singleton StateFlow is the
 * smallest thing that joins them: the test sets it for the "during cover" window and
 * clears it when the test ends (also when the test is cancelled — see the `finally` in
 * SensorTests.testSensor). Nothing is acknowledged: whether anyone saw the prompt, or
 * covered the sensor, is NOT known here — the app ships the raw readings and the server
 * decides (S-32 (b)).
 */
@Singleton
class CoverPromptChannel @Inject constructor() {

    private val _active = MutableStateFlow(false)

    /** true while the technician is being asked to cover the light sensor. */
    val active: StateFlow<Boolean> = _active

    fun show() { _active.value = true }

    fun hide() { _active.value = false }
}
