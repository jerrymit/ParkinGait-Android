package com.example.parkingait

import android.os.Bundle
import android.os.Vibrator
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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
import android.widget.Toast
import android.util.Log
//Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.getValue
import com.google.firebase.auth.FirebaseUser
//ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
// DisplayMetrics
import android.util.DisplayMetrics
import androidx.lifecycle.Observer
import android.text.TextWatcher
import android.text.Editable

import kotlin.reflect.KClass
import android.os.Handler
// button color
import androidx.core.content.ContextCompat


data class SensorEventData(val timestamp: Long, val values: FloatArray)
class CalibrationViewModel : ViewModel() {
    // Defined the states as LiveData to observe changes
    val isCollecting = MutableLiveData(false)
//    val accelerometerData = MutableLiveData(listOf<FloatArray>())
//    val accelerometerData = MutableLiveData<List<SensorEvent>>(emptyList())
    val accelerometerData: MutableLiveData<List<SensorEventData>> = MutableLiveData()
    val goalStep = MutableLiveData(0)
    val newGoalStep = MutableLiveData(0)
//    val feedbackData = MutableLiveData(FeedbackData(0, 0.0, 0.0))
    val showFeedback = MutableLiveData(false)
    val btnlocationPlacement = MutableLiveData("In Pocket/In Front")

    // You can define an initial state for location placements as in React
    val locationPlacements = listOf("In Pocket/In Front", "In Waist/On Side")

    fun addSensorEvent(event: SensorEvent) {
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

    // Update states like in React's useState
    fun setGoalStep(step: Int) {
        goalStep.postValue(step)
    }

    // Additional functions to update other states
}

object Constants {
    const val ACCELEROMETER_TIMING = 100 // ms
    val ACCELEROMETER_HZ = 1000 / ACCELEROMETER_TIMING
    const val DISTANCE_TRAVELED = 5
    const val DISTANCE_THRESHOLD = 3
    const val USER_HEIGHT = 1.778 // m
    const val METERS_TO_INCHES = 39.3701 // constant, no units

    fun getScreenHeight(context: Context): Int {
        val metrics = DisplayMetrics()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val display = context.display
            display?.getRealMetrics(metrics)
        } else {
            @Suppress("DEPRECATION")
            val display = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            @Suppress("DEPRECATION")
            display.defaultDisplay.getMetrics(metrics)
        }
        return metrics.heightPixels
    }
}


// Here is the main function
class CalibrateActivity : AppCompatActivity() {
    // ViewModel
    private lateinit var viewModel: CalibrationViewModel
    // Firebase Auth instance
    private lateinit var auth: FirebaseAuth

    //Firebase db
    private lateinit var db: DatabaseReference

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var sensorEventListener: SensorEventListener

    private lateinit var btnInPocket: Button
    private lateinit var btnInWaist: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The layout xml file name is activity_calibration
        setContentView(R.layout.activity_calibration)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        // This is related to the Realtime Firebase rules
        db = FirebaseDatabase.getInstance().getReference("users")

        viewModel = ViewModelProvider(this)[CalibrationViewModel::class.java]
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val currentUser = auth.currentUser

        // check the calibration data in the database
        checkCalibration()
        // For user to set up the GoalSteps
        setupNewGoalSteps(findViewById(R.id.editTextGoalSteps))
        // Event listener to trigger the sensor
        initSensorListener()
        Log.d("CheckCalibration", "After initSensorListener function.")
        // Start to gather the data
        StartCalibrate()

        // button to select location placement, you can define the button view in the layout folder
        btnInPocket = findViewById(R.id.btnInPocket)
        btnInWaist = findViewById(R.id.btnInWaist)

        btnInPocket.setOnClickListener {
            setActiveButton(btnInPocket, "In Pocket/In Front")
        }

        btnInWaist.setOnClickListener {
            setActiveButton(btnInWaist, "In Waist/On Side")
        }

        // Initialize with one button active
        setActiveButton(btnInPocket, "In Pocket/In Front")

//        Log.d("CheckCalibration", "Before handle log data.")
//        handleLogData()
//        Log.d("CheckCalibration", "After handle log data.")
    }
    private fun checkCalibration() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("CheckCalibration", "No current user, navigating to login.")
            navigateToLogIn()
        } else {
            val userId = currentUser.email?.replace(".", "~") ?: ""
            val userInfoRef = db.child(userId).child("Info")

            userInfoRef.get().addOnSuccessListener { snapshot ->
                Log.d("CheckCalibration", "Data fetch successful.")
                val registerData = snapshot.getValue<Info>()
                registerData?.let {
                    // Safely handle the possibility of height being null
                    Log.d("CheckCalibration", "Data conversion successful: $registerData")
                    val height = it.height ?: Constants.USER_HEIGHT  // Use a default height if null
                    Log.d("CheckCalibration", "Using height: $height")
                    val calculatedStepGoal = height * 0.414
                    Log.d("CheckCalibration", "Calculated step goal: $calculatedStepGoal")
                    setGoalStep(userId, calculatedStepGoal)
                } ?: run {
                    Log.d("CheckCalibration", "No data found or data is null, setting default step goal.")
                    setDefaultStepGoal(userId)
                }
            }.addOnFailureListener {
                Log.d("CheckCalibration", "Failed to fetch data: ")
                // Handle possible errors, such as a failed read from the database
                setDefaultStepGoal(userId)
            }
        }
    }

    private fun navigateToLogIn() {
        // Example navigation using Intent
        val intent = Intent(this, LogInActivity::class.java)
        startActivity(intent)
        finish()
    }
    private fun setDefaultStepGoal(userId: String,) {
        // Assuming Constants.DEFAULT_HEIGHT and Constants.METERS_TO_INCHES are defined
        setGoalStep(userId,Constants.USER_HEIGHT * Constants.METERS_TO_INCHES * 0.414)
    }

    private fun setGoalStep(userId: String,steps: Double) {
        // Update your UI or ViewModel with the calculated steps
        // Example: textViewSteps.text = "Goal: $steps steps"
        val userInfoRef = db.child(userId).child("Calibration")
        val calibrationData = CalibrationData(goalStep = steps)
        userInfoRef.setValue(calibrationData)
            .addOnSuccessListener {
                Log.d("SetGoalStep", "Goal step set successfully.")
            }
            .addOnFailureListener {
                Log.d("SetGoalStep", "Failed to set goal step.")
            }
        // Show the recommended step length in dynamically
        val stepsInInches = steps.toInt()  // Convert to integer to remove decimals
        val formattedText = "Recommended Step Length: $stepsInInches inches"
        findViewById<TextView>(R.id.tvBaseLabel).text = formattedText

    }

    // Private function to set up TextWatcher for EditText
    private fun setupNewGoalSteps(editText: EditText) {
        val currentUser = auth.currentUser
        val userId = currentUser!!.email?.replace(".", "~") ?: ""

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Optionally implement actions before text changes
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Optionally implement actions when text changes
            }

            override fun afterTextChanged(s: Editable?) {
                val goalSteps = s.toString().toDoubleOrNull()
                if (goalSteps != null) {
                    setGoalStep(userId, goalSteps)
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Please enter a valid number",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun StartCalibrate() {
        val btnCalibrate = findViewById<Button>(R.id.btnCalibrate)
        btnCalibrate.setOnClickListener {
            // Toggle the collection state
            val isCollecting = viewModel.isCollecting.value ?: false
            if (!isCollecting) {
                // Start collecting data
                btnCalibrate.text = "Stop Collecting"
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
                btnCalibrate.text = "Calibrate"

                // Log the collected data
                handleLogData()
                handleAccelerometer(false)

//                // Clear the collected data after logging
//                viewModel.accelerometerData.postValue(emptyList())
            }
            viewModel.isCollecting.postValue(!isCollecting) // Update the collecting state
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

    private fun initSensorListener() {
        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    Log.d("SensorTest", "Sensor data: X=${event.values[0]}, Y=${event.values[1]}, Z=${event.values[2]}")
                    Log.d("SensorTest", "Timestamp: ${System.currentTimeMillis()} Sensor data: X=${event.values[0]}, Y=${event.values[1]}, Z=${event.values[2]}")
                    viewModel.addSensorEvent(event)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Optionally handle accuracy changes
            }
        }
    }

    private fun Double.toFixed(digits: Int) = "%.${digits}f".format(this)
    private fun Float.toFixed(digits: Int) = "%.${digits}f".format(this)

    private fun handleLogData() {
        val xDataList = mutableListOf<Float>()
        val yDataList = mutableListOf<Float>()
        val zDataList = mutableListOf<Float>()
        val magnitudeDataList = mutableListOf<Float>()

        // print each event's data
        viewModel.accelerometerData.value?.forEach { event ->
//            val xData = String.format("%.4f", event.values[0])
//            val yData = String.format("%.4f", event.values[1])
//            val zData = String.format("%.4f", event.values[2])
//            val magnitudeData = sqrt(event.values[0] * event.values[0] +
//                    event.values[1] * event.values[1] +
//                    event.values[2] * event.values[2]).let {
//                String.format("%.4f", it)
//            }
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
        // Log all data points in the event
        Log.d("xData", "X Data List: $xDataList")
        Log.d("yData", "Y Data List: $yDataList")
        Log.d("zData", "Z Data List: $zDataList")
        Log.d("mData", "Magnitude List: $magnitudeDataList")


        viewModel.btnlocationPlacement.observe(this, Observer { placement ->
            when (placement) {
                "In Pocket/In Front" -> {
                    // Handle UI changes or actions when button 'In Pocket' is active
                    Toast.makeText(this, "In Pocket Selected", Toast.LENGTH_SHORT).show()
                    analyzeSteps(zDataList, placement)
                }
                "In Waist/On Side" -> {
                    // Handle UI changes or actions when button 'In Waist' is active
                    Toast.makeText(this, "In Waist Selected", Toast.LENGTH_SHORT).show()
                    analyzeSteps(yDataList, placement)
                }
            }
        })
    }

    private fun setActiveButton(activeButton: Button, placement: String) {
        val greenColor = ContextCompat.getColor(this, R.color.button_color)
        val blackColor = ContextCompat.getColor(this, R.color.black)

        // Reset styles
        resetButtonStyles()

        // Update ViewModel with the new location placement
        viewModel.btnlocationPlacement.value = placement

        // Set active button style
        activeButton.setBackgroundColor(greenColor) // Example active state
        activeButton.setTextColor(blackColor)
    }

    private fun resetButtonStyles() {
        val grayColor = ContextCompat.getColor(this, R.color.default_color)
        val blackColor = ContextCompat.getColor(this, R.color.black)
        // Reset styles for all buttons
        btnInPocket.setBackgroundColor(grayColor)
        btnInWaist.setBackgroundColor(grayColor)
        btnInPocket.setTextColor(blackColor)
        btnInWaist.setTextColor(blackColor)
    }

    private fun analyzeSteps(data: List<Float>, locationPlacement: String) {
        val currentUser = auth.currentUser
        val userId = currentUser!!.email?.replace(".", "~") ?: ""
        //val CalibrateRef = db.child(userId).child("Calibration")

        if (locationPlacement == "In Pocket/In Front") {
            val mean = data.dropLast(10).average()
            val steps = mutableListOf<Int>()
            var index = 0

            data.dropLast(10).forEachIndexed { z, value ->
                if (z - index > Constants.DISTANCE_THRESHOLD &&
                    ((value < mean && data[z - 1] > mean) ||
                            (data[z - 1] < mean && value > mean))) {
                    steps.add((z + z - 1) / 2)
                    index = z
                }
            }

            val times = steps.zipWithNext { a, b -> (b - a).toFloat() / Constants.ACCELEROMETER_HZ }
            val averageTime = times.average()
            val averageStepLength = Constants.DISTANCE_TRAVELED / steps.size
            val averageStepLengthInches = averageStepLength * Constants.METERS_TO_INCHES
            val gaitConstant = averageStepLength / averageTime

            updateFirebaseData(gaitConstant, mean, viewModel.goalStep.value ?: 0, locationPlacement)
        }
        else if (locationPlacement == "In Waist/On Side") {
            // Filter out positive values only and compute the mean after slicing the last 10 items
            val positiveYs = data.filter { it > 0 }
            val meanHalf = if (positiveYs.size > 10) {
                positiveYs.take(positiveYs.size - 10).average() / 2
            } else {
                0.0 // Handle edge case where there aren't enough data points
            }

            // Detect steps based on provided conditions
            val steps = mutableListOf<Int>()
            var index = 0
            for (y in 0 until data.size - 10) {
                if (y - index > Constants.DISTANCE_THRESHOLD && data[y] > meanHalf && data[y] > data.getOrNull(y - 1) ?: 0f && data[y] < data.getOrNull(y + 1) ?: 0f) {
                    steps.add(y)
                    index = y
                }
            }

            // Calculate time differences between steps
            val times = steps.zipWithNext { a, b -> (b - a).toFloat() / Constants.ACCELEROMETER_HZ }
            val averageTime = if (times.isNotEmpty()) times.average() else 0.0
            val averageStepLength = Constants.DISTANCE_TRAVELED / steps.size
            val averageStepLengthInches = averageStepLength * Constants.METERS_TO_INCHES

            val gaitConstant = averageStepLength / averageTime
            updateFirebaseData(gaitConstant, meanHalf, viewModel.goalStep.value ?: 0, locationPlacement)
        }
    }

    private fun updateFirebaseData(gaitConstant: Double, threshold: Double, newGoalStep: Int, locationPlacement: String) {
        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            val safeEmail = user.email?.replace(".", "~") ?: "unknown_user"
            val CalibrateReference = FirebaseDatabase.getInstance().getReference("users/$safeEmail/Calibration")

            val calibrationData = hashMapOf(
                "gaitConstant" to gaitConstant,
                "Threshold" to threshold,
                "GoalStep" to newGoalStep,
                "Placement" to locationPlacement
            )

            CalibrateReference.setValue(calibrationData)
                .addOnSuccessListener {
                    println("Data successfully updated.")
                }
                .addOnFailureListener { e ->
                    println("Error updating data: ${e.message}")
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorEventListener?.let {
            sensorManager.unregisterListener(it)
        }
    }

}