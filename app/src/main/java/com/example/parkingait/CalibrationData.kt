package com.example.parkingait

// This class is used to store the calibration data
data class CalibrationData(
    val gaitConstant: Double = 0.0,
    val threshold: Double = 0.0,
    val placement: String = "",
    val goalStep: Double = 0.0
)