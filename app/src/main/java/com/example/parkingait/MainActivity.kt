package com.example.parkingait


import android.os.Bundle
import android.os.Vibrator
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import android.util.Log
//ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider

// line chart
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries


class MainActivity : AppCompatActivity() {
    // Firebase
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var auth: FirebaseAuth
    //private lateinit var database: DatabaseReference
    private lateinit var database: FirebaseDatabase
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var vibrator: Vibrator
    private lateinit var sensorEventListener: SensorEventListener
    // UI elements
    private lateinit var btnRecalibrateButton: Button
    private lateinit var btnStartWalkingButton: Button
    private lateinit var btnDashboardButton: Button

    ///Calibration data ///
    private var gaitConstant: Double = 0.5 // Now mutable
    private var placement = ""
    private var threshold = 1.0 // Also can be mutable if needed
    private var goalStep = 0.0

    ///DEFINING CONSTANTS ///
    private val METERS_TO_INCHES: Float = 39.3701f
    private val DISTANCE_THRESHOLD = 0  // Change this value as needed
    private val accelerometerHz = 100f
    private val windowSize = 5

    private val _recentAccelData = MutableLiveData<List<FloatArray>>()
    val recentAccelData: LiveData<List<FloatArray>> = _recentAccelData
    private val accelerometerData: MutableLiveData<List<SensorEventData>> = MutableLiveData()
    private val isWalking = MutableLiveData(false)
    private val locationPlacement = MutableLiveData("In Pocket/In Front")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Firebase initialization
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Sensor initialization
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        initSensorListener()

        /// Buttons
        btnStartWalkingButton = findViewById(R.id.btnStartWalking)
        btnRecalibrateButton = findViewById(R.id.btnRecalibrate)
        btnDashboardButton = findViewById(R.id.btnGoToDashboard)


        // Set click listeners
        btnStartWalkingButton.setOnClickListener {
            StartWalk()
        }

        btnRecalibrateButton.setOnClickListener {
            goToCalibratePage()
        }

        btnDashboardButton.setOnClickListener {
            goToDashboardPage()
        }

        if (auth.currentUser == null) {
            navigateToLogin()
        } else {
            fetchCalibrationData()
        }

    }

    private fun navigateToLogin() {
        val intent = Intent(this, LogInActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Fetch calibration data from Firebase
    private fun fetchCalibrationData() {
        val currentUser = auth.currentUser
        if (currentUser?.email != null) {
            // Replace dots with tildes (~) in the email to use as a Firebase key
            val userEmailSanitized = currentUser.email!!.replace(".", "~")
            val calibrationRef = database.reference.child("users/$userEmailSanitized/Calibration")

            calibrationRef.get().addOnSuccessListener { dataSnapshot ->
                val calibrationData = dataSnapshot.getValue<CalibrationData>()
                calibrationData?.let {
                    gaitConstant = it.gaitConstant
                    goalStep = it.goalStep
                    threshold = it.threshold
                    placement = it.placement

                    // Log the updated values
                    Log.d("MainActivity", "GaitConstant: $gaitConstant")
                    Log.d("MainActivity", "Threshold: $threshold")
                    Log.d("MainActivity", "Placement: $placement")
                    Log.d("MainActivity", "GoalStep: $goalStep")
                }
            }.addOnFailureListener { exception ->
                // Show a Toast message to the user
                Toast.makeText(this, "Failed to fetch calibration data: ${exception.message}", Toast.LENGTH_LONG).show()

                // Optionally, log the error as well
                Log.e("FirebaseError", "Failed to fetch calibration data", exception)
            }
        } else {
            // Redirect to Login Activity
            val loginIntent = Intent(this, LogInActivity::class.java)
            startActivity(loginIntent)
            finish()  // Close the current activity
        }
    }



    private fun initSensorListener() {
        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    Log.d("SensorTest", "Sensor data: X=${event.values[0]}, Y=${event.values[1]}, Z=${event.values[2]}")
                    Log.d("SensorTest", "Timestamp: ${System.currentTimeMillis()} Sensor data: X=${event.values[0]}, Y=${event.values[1]}, Z=${event.values[2]}")
                    addSensorEvent(event)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Optionally handle accuracy changes
            }
        }
    }

    private fun addSensorEvent(event: SensorEvent) {
        // Create a copy of the event data
        val copiedEventData = SensorEventData(event.timestamp, event.values.clone())

        val currentData = accelerometerData.value?.toMutableList() ?: mutableListOf()

        // Add the copied event data to the list
        currentData.add(copiedEventData)
        accelerometerData.postValue(currentData)

        accelerometerData.value?.forEach { event ->
            // Log the event. You can customize the tag and the log message as needed.
            Log.d("SensorDataLog", "Event: $event")
        }
    }

    private fun handleAccelerometer(isCollecting: Boolean) {
        if (isCollecting) {
            accelerometer?.also {
                Log.d("CalibrationActivity", "Starting accelerometer data collection.")
                sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            Log.d("CalibrationActivity", "Stopping accelerometer data collection.")
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    // When you click the walking button it will start to collect the data
    private fun StartWalk() {
        // Toggle the collection state
        val isCollecting = isWalking.value ?: false
        if (!isCollecting) {
            // Start collecting data
            btnStartWalkingButton.text = "Stop Walking"
            handleAccelerometer(true)

            // Vibrate after 5 seconds to notify user
            val handler = Handler(this.mainLooper!!)
            val vibrateRunnable = Runnable {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(500) // Vibrate for 500 milliseconds
            }
            handler.postDelayed(vibrateRunnable, 5000) // Delay for 5 seconds
        } else {
            // Stop collecting data
            btnStartWalkingButton.text = "Start Walking"

            // Log the collected data
            handleLogData()
            handleAccelerometer(false)

        }
        isWalking.postValue(!isCollecting) // Update the collecting state
    }

    // Handle the data to calculate the step length
    // I only use the y_data (moving forward and backward) to calculate the step length now
    // I am not sure whether it is better to take x_data (left and right) and z_data (up and down) into consideration to calculate the step length data.
    // For now, I am using an algorithm that retrieves the largest step length every 0.5 seconds to count it as a step.
    // The better way I think is to detect the every peak value for every step
    // For example, if the acceleration data is as the list below
    // [10, 10, 50, 52, 55, 57, 40, 20, 10, 10 ,60 ,65, 66]
    // The data we can calculate the step length are 50 and 60
    private fun handleLogData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Replace dots with tildes (~) in the email to use as a Firebase key
            val userEmail = currentUser.email!!.replace(".", "~")
            val walkingDataRef = database.reference.child("users/$userEmail/WalkingData/")

            val xDataList = mutableListOf<Float>()
            val yDataList = mutableListOf<Float>()
            val zDataList = mutableListOf<Float>()
            val magnitudeDataList = mutableListOf<Float>()

            // Assuming accelerometerData is a LiveData or similar holding the accelerometer events
            accelerometerData.value?.forEach { event ->
                val xData = event.values[0]
                val yData = event.values[1]
                val zData = event.values[2]
                val magnitudeData = sqrt(event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2])

                xDataList.add(xData)
                yDataList.add(yData)
                zDataList.add(zData)
                magnitudeDataList.add(magnitudeData)
            }

            // calculate the step length
            val xlist = calculateAbsoluteDifferences(xDataList, METERS_TO_INCHES)
            val ylist = calculateAbsoluteDifferences(yDataList, METERS_TO_INCHES)
            val zlist = calculateAbsoluteDifferences(zDataList, METERS_TO_INCHES)
            val mlist = calculateAbsoluteDifferences(magnitudeDataList, METERS_TO_INCHES)

            // get step value every one seconds
            val xSteplengthlist = getLargestEvery25(xlist)
            val ySteplengthlist = getLargestEvery25(ylist)
            val zSteplengthlist = getLargestEvery25(zlist)
            val mSteplengthlist = getLargestEvery25(mlist)

            // Calculate moving average for x, y, z data
            val xData = movingAverage(xSteplengthlist)
            val yData = movingAverage(ySteplengthlist)
            val zData = movingAverage(zSteplengthlist)

            // Calculate dynamic thresholds
            val zMean = xData.average().toFloat()
            val zStdDev = standardDeviation(zSteplengthlist)
            val dynamicThresholdZ = zMean + zStdDev * 0.5
            Log.d("DEBUG", "dynamicThresholdZ: $dynamicThresholdZ")

            val yMean = yData.average().toFloat()
            val yStdDev = standardDeviation(ySteplengthlist)
            val dynamicThresholdY = yMean + yStdDev * 0.5
            Log.d("DEBUG", "dynamicThresholdY: $dynamicThresholdY")

            // Step detection logic

            var stepLengths = mutableListOf<Float>()
            var totalDistanceWalked: Float? = null
            var totalTime: Double? = null

            // Set startIndex to the first data point (index 0)
            val startIndex = 1

            // Set endIndex to the last data point (size of the list minus one)
            val endIndex = ySteplengthlist.size - 1

            // Calculate the number of remaining data points
            val remainingDataPoints = endIndex - startIndex + 1

            // Calculate the total time in seconds
            totalTime = remainingDataPoints / accelerometerHz.toDouble()

            // Ensure we have enough data to process
            if (startIndex < endIndex) {
                for (i in startIndex until endIndex) {
                    val yDataCurr = ySteplengthlist[i]
                    Log.d("DEBUG", "yDataCurr at index $i: $yDataCurr")  // Log the data here
                    if (yDataCurr > dynamicThresholdY) {
                        stepLengths.add(yDataCurr)
                        Log.d("DEBUG", "Step detected at index $i is $yDataCurr")
                    }
//                    val yDataPrev = yDataList[i - 1]
//                    val yDataCurr = yDataList[i]
//                    val dataTime = (i / accelerometerHz).toLong()
//
//                    if (waitingFor1stValue && ((yDataCurr < dynamicThresholdY && yDataPrev > dynamicThresholdY) || (yDataCurr > dynamicThresholdY && yDataPrev < dynamicThresholdY))) {
//                        if (lastPeakIndex == -1 || i - lastPeakIndex > DISTANCE_THRESHOLD) {
//                            if (lastPeakSign == -1) {
//                                stepLengths.add(yDataCurr)
//                                peakTimes.add(dataTime)
//                                lastPeakIndex = i
//                                lastPeakSign = 1
//                                waitingFor1stValue = false
//                                waitingFor2ndValue = true
//                                Log.d("DEBUG", "Updated values after detecting first peak")
//                            }
//                        }
//                    }

//                    if (waitingFor2ndValue && ((yDataCurr < dynamicThresholdY && yDataPrev > dynamicThresholdY) || (yDataCurr > dynamicThresholdY && yDataPrev < dynamicThresholdY))) {
//                        if (i - lastPeakIndex > DISTANCE_THRESHOLD) {
//                            if (lastPeakSign == 1) {
//                                stepLengths.add(yDataCurr)
//                                peakTimes.add(dataTime)
//                                lastPeakIndex = i
//                                lastPeakSign = -1
//                                waitingFor1stValue = true
//                                waitingFor2ndValue = false
//                                Log.d("DEBUG", "Updated values after detecting second peak")
//                            }
//                        }
//                    }
                }

                // Calculate step length using the filtered data
//                if (peakTimes.size >= 2) {
//                    // Calculate the total time from the first peak to the last peak
//                    totalTime = (peakTimes.last() - peakTimes.first()).toDouble()
//
//                    // Calculate the total number of steps, which is the size of peakTimes
//                    val totalSteps = peakTimes.size.toDouble()
//                    val distanceMoved = (totalSteps - 1) // Assuming one step per peak
//
//                    Log.d("totalTime", "totalTime: $totalTime")
//                    Log.d("totalSteps", "totalSteps: $totalSteps")
//                    Log.d("distanceMoved", "distanceMoved: $distanceMoved")
//
//                    // Calculate the intermediate value: distance per time
//                    val distancePerTime = distanceMoved / totalTime
//                    Log.d("distancePerTime", "distanceMoved / totalTime: $distancePerTime")
//
//                    // Calculate the step length per step
//                    val stepLength = distancePerTime * METERS_TO_INCHES
//                    Log.d("STEP", "Step length per step: $stepLength")
//
//                    // Calculate the total distance walked
//                    val totalDistanceWalked = stepLength * totalSteps
//                    Log.d("TOTAL_DISTANCE", "Total distance walked: $totalDistanceWalked")
//                }
            }
            totalDistanceWalked = stepLengths.sum()
            // Calculate the number of steps
            val numberOfSteps = stepLengths.size
            // Calculate the average step length
            val averageStepLength = if (numberOfSteps > 0) totalDistanceWalked / numberOfSteps else 0.0

            // Log all data points in the event
            Log.d("xData", "X Data List: $xSteplengthlist")
            Log.d("yData", "Y Data List: $ySteplengthlist")
            Log.d("zData", "Z Data List: $zSteplengthlist")
            Log.d("mData", "Magnitude List: $mSteplengthlist")

            val walkingData = mapOf(
                "xDataList" to xSteplengthlist,
                "yDataList" to ySteplengthlist,
                "zDataList" to zSteplengthlist,
                "magnitudeDataList" to magnitudeDataList,
                "stepLengths" to stepLengths,
                "totalDistanceWalked" to totalDistanceWalked,
                "numberOfSteps" to numberOfSteps,
                "averageStepLength" to averageStepLength,
                "totalTime" to totalTime,
                "timestamp" to System.currentTimeMillis() // Optionally add a timestamp
            )

            // Use push() to generate a new unique key for each new data entry
            walkingDataRef.push().setValue(walkingData).addOnSuccessListener {
                Log.d("MainActivity", "Walking data successfully stored")
            }.addOnFailureListener { exception ->
                Log.e("FirebaseError", "Failed to store walking data", exception)
            }
        } else {
            // Handle the case where the user is not authenticated
            Log.e("MainActivity", "User not authenticated")
        }
    }

    private fun getLargestEvery25(dataList: List<Float>): List<Float> {
        if (dataList.isEmpty()) return emptyList()

        val resultList = mutableListOf<Float>()
        val chunkSize = 25

        for (i in 1 until dataList.size step chunkSize) {
            val end = if (i + chunkSize <= dataList.size) i + chunkSize else dataList.size
            val chunk = dataList.subList(i, end)
            val maxInChunk = chunk.maxOrNull() ?: continue
            resultList.add(maxInChunk)
        }

        return resultList
    }

    private fun calculateAbsoluteDifferences(dataList: MutableList<Float>, conversionFactor: Float): MutableList<Float> {
        val resultList = mutableListOf<Float>()
        if (dataList.isNotEmpty()) {
            // Deduct the first value and convert
            val firstValue = dataList.first()
            val convertedFirstValue = firstValue * conversionFactor

            // Convert the first value and add the absolute difference with itself (which is zero)
            resultList.add(0f)

            for (i in 1 until dataList.size) {
                val previousValueInInches = (dataList[i - 1] - firstValue) * conversionFactor / 2.54f
                val currentValueInInches = (dataList[i] - firstValue) * conversionFactor / 2.54f
                resultList.add(kotlin.math.abs(currentValueInInches - previousValueInInches))
            }
        }
        return resultList
    }

    private fun goToCalibratePage() {
        val intent = Intent(this, CalibrateActivity::class.java)
        startActivity(intent)
        finish()
    }
    private fun goToDashboardPage() {
        Log.d("DashboardActivity", "Navigating to Dashboard Page")
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun movingAverage(data: List<Float>): List<Float> {
        val result = mutableListOf<Float>()
        for (i in 0..data.size - windowSize) {
            val currentWindow = data.subList(i, i + windowSize)
            val windowAvg = currentWindow.sum() / windowSize
            result.add(windowAvg)
        }
        return result
    }

    // Function to calculate the standard deviation of a list of doubles
    private fun standardDeviation(arr: List<Float>): Float {
        if (arr.isEmpty()) return 0.0f

        val avg = arr.average()
        val sumOfSquares = arr.sumOf { (it - avg) * (it - avg) }
        return sqrt(sumOfSquares / arr.size).toFloat()
    }

    private fun processSensorData(rawXData: List<Float>, rawYData: List<Float>, rawZData: List<Float>) {
        val xData = movingAverage(rawXData)
        val yData = movingAverage(rawYData)
        val zData = movingAverage(rawZData)

        // Accessing elements safely
        val yDataNext = yData.lastOrNull() ?: 0f
        val yDataCurr = if (yData.size > 1) yData[yData.size - 2] else 0f
        val yDataPrev = if (yData.size > 2) yData[yData.size - 3] else 0f

        val zDataCurr = zData.lastOrNull() ?: 0f
        val zDataPrev = if (zData.size > 1) zData[zData.size - 2] else 0f

        val dataTime = if (zData.isNotEmpty()) zData.size.toFloat() / accelerometerHz else 0f

        val meanZ = zData.average()
        val stdDevZ = standardDeviation(zData)
        val dynamicThresholdZ = meanZ + stdDevZ * 0.5f

        val stdDevY = standardDeviation(yData)

        val currentUser = auth.currentUser
        // Replace dots with tildes (~) in the email to use as a Firebase key
        val userEmailSanitized = currentUser?.email!!.replace(".", "~")
        val postListRef = database.reference.child("users/$userEmailSanitized/StepLength/")
        val newPostRef = postListRef.push()

    }
}

