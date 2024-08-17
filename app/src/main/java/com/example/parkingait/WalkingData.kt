package com.example.parkingait

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class WalkingData(
    var isWalking: Boolean = false,
    var accelerometerData: List<FloatArray> = listOf(),
    var stepLength: Float = 0f,
    var stepLengthFirebase: List<Float> = listOf(),
    var peakTimes: List<Long> = listOf(),
    var waitingFor1stValue: Boolean = false,
    var waitingFor2ndValue: Boolean = false,
    var waitingFor3rdValue: Boolean = false,
    var sound1: Any? = null,
    var sound2: Any? = null,
    var goalStep: Int = 0,
    var vibrateOption: String = "Over Step Goal",
    var vibrateValue: String = "Vibrate Phone",
    var isEnabled: Boolean = false,
    var range: Int = 30,
    var lastPeakSign: Int = -1,
    var lastPeakIndex: Int = 0
)

class WalkingViewModel : ViewModel() {
    private val _walkingData = MutableLiveData(WalkingData())
    val walkingData: LiveData<WalkingData> = _walkingData

    fun updateIsWalking(isWalking: Boolean) {
        _walkingData.value = _walkingData.value?.copy(isWalking = isWalking)
    }

    // Additional methods to update other properties...
    fun updateStepLength(stepLength: Float) {
        _walkingData.value = _walkingData.value?.copy(stepLength = stepLength)
    }

    fun updateAccelerometerData(data: List<FloatArray>) {
        _walkingData.value = _walkingData.value?.copy(accelerometerData = data)
    }

    // More methods for each property...
}