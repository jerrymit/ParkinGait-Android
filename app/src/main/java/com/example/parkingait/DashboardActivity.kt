package com.example.parkingait

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.database.ktx.database
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import android.widget.TextView

class DashboardActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var lineChart: LineChart
    private lateinit var pieChart: PieChart
    private lateinit var tvGoalStep: TextView

    private var goalStep: Int = 0
    private var value = "Day"
    private var data = mutableListOf<Float>()
    private var goodSteps = 0
    private var totalSteps = 0
    private var percentGood = 0f
    private var asymmetry = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        auth = Firebase.auth
        database = FirebaseDatabase.getInstance()

        lineChart = findViewById(R.id.lineChart)
        pieChart = findViewById(R.id.pieChart)
        tvGoalStep = findViewById(R.id.tvGoalStep)

        // Get the user email
        val currentUserEmail = auth.currentUser?.email!!.replace(".", "~")
        // Get the calibration data
        val CalibrationRef = database.reference.child("users/$currentUserEmail/Calibration")
        // Get the step length data
        val SteplengthRef = database.reference.child("users/$currentUserEmail/WalkingData")
        // Fetch the data
        fetchGoalStep(CalibrationRef)
        fetchStepData(SteplengthRef)

    }

    private fun fetchGoalStep(calibrationRef: DatabaseReference) {
        // The path in the firebase to get the data
        calibrationRef.child("goalStep").get().addOnSuccessListener { snapshot ->
            goalStep = snapshot.getValue(Double::class.java)?.toInt() ?: 0
            tvGoalStep.text = "Goal Step: $goalStep"
        }.addOnFailureListener {
            Log.e("DashboardActivity", "Error fetching goal step", it)
        }
    }

    private fun fetchStepData(stepLengthRef: DatabaseReference) {
        stepLengthRef.get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { childSnapshot ->
                val averageStepLength = childSnapshot.child("averageStepLength").getValue(Double::class.java)
                val stepLengths = childSnapshot.child("stepLengths").getValue(object : GenericTypeIndicator<List<Float>>() {})
                if (averageStepLength != null && stepLengths != null) {
                    processStepData(averageStepLength.toFloat(), stepLengths)
                }
            }
        }.addOnFailureListener {
            Log.e("DashboardActivity", "Error fetching step data", it)
        }
    }

    private fun processStepData(averageStepLength: Float, stepLengths: List<Float>) {
        // Process the fetched data
        Log.d("DashboardActivity", "Step Length: $averageStepLength")
        Log.d("DashboardActivity", "Magnitude Data List: $stepLengths")

        // Your existing data processing logic here
        data.clear()
        totalSteps = 0
        goodSteps = 0
        var leftSum = 0f
        var rightSum = 0f
        var left = true
        val timeInterval = when (value) {
            "Day" -> TimeUnit.DAYS.toMillis(1)
            "Month" -> TimeUnit.DAYS.toMillis(30)
            "Year" -> TimeUnit.DAYS.toMillis(365)
            else -> TimeUnit.DAYS.toMillis(1)
        }

        for (stepLength in stepLengths) {
            // Assuming timestamp is not relevant for magnitude data processing in this context
            if (stepLength < 700) {
                data.add(stepLength)
                if (stepLength > goalStep) goodSteps++
                if (left) leftSum += stepLength else rightSum += stepLength
                left = !left
            }
        }

        percentGood = if (data.isNotEmpty()) goodSteps.toFloat() / data.size else 0.4f
        asymmetry = if ((leftSum + rightSum) > 0) abs((leftSum - rightSum) / (leftSum + rightSum)) else 0f
        totalSteps = data.size

        updateCharts(averageStepLength, data)
    }

    private fun updateCharts(averageStepLength: Float, data: List<Float>) {
        // Update LineChart
        val entries = data.mapIndexed { index, value -> Entry(index.toFloat(), value) }
        val lineDataSet = LineDataSet(entries, "StepLength Data")
        lineDataSet.colors = ColorTemplate.COLORFUL_COLORS.toList()
        val lineData = LineData(lineDataSet)
        lineChart.data = lineData
        lineChart.invalidate()

        // Update PieChart
        val pieEntries = listOf(
            PieEntry(averageStepLength, "Step Length Est."),
            PieEntry(percentGood, "Percent Good Steps"),
            PieEntry(asymmetry, "Percent Asymmetry")
        )
        val pieDataSet = PieDataSet(pieEntries, "Step Data Distribution")
        pieDataSet.colors = ColorTemplate.COLORFUL_COLORS.toList()
        val pieData = PieData(pieDataSet)
        pieChart.data = pieData
        pieChart.invalidate()
    }
}
