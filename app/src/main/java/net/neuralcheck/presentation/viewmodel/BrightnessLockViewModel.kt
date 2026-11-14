package net.neuralcheck.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import net.neuralcheck.service.stress.BrightnessLock
import net.neuralcheck.service.stress.BrightnessLockHolder
import javax.inject.Inject

/**
 * fieldsVersion 71 (A-P6-13): hands the scan screen's report of the brightness lock to the Hilt-singleton [BrightnessLockHolder], which the scan service drains when it
 * builds the footer. The screen reaches the holder through this view model (as it reaches every other injected piece); it observes nothing and changes nothing else.
 */
@HiltViewModel
class BrightnessLockViewModel @Inject constructor(
    private val holder: BrightnessLockHolder
) : ViewModel() {

    fun report(result: BrightnessLock.Result) {
        holder.report(result)
    }
}
