package com.soloheisbeer.microwaffle400

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.soloheisbeer.microwaffle400.audio.AudioManager
import com.soloheisbeer.microwaffle400.network.ConnectionUpdateInterface
import com.soloheisbeer.microwaffle400.network.NetworkManager
import com.soloheisbeer.microwaffle400.network.StatusUpdateInterface
import com.soloheisbeer.microwaffle400.service.MicroService
import com.soloheisbeer.microwaffle400.service.MicroState
import com.soloheisbeer.microwaffle400.utils.MicroUtils
import org.json.JSONObject
import kotlin.math.abs


class MainActivity : AppCompatActivity(),
    ConnectionUpdateInterface,
    StatusUpdateInterface {

    private val TAG = "MAIN"
    private var timeInSeconds = 0
    private var microState = MicroState.IDLE
    private var enableUIOnStateChange = false
    private lateinit var timerText: TextView
    private val audioManager = AudioManager(this@MainActivity)
    private val networkManager = NetworkManager
    private  val microService = MicroService
    private var connectingDialog: AlertDialog? = null

    private var startButton: Button? = null
    private var stopButton: Button? = null

    private var mpButton: Button? = null
    private var spButton: Button? = null
    private var mmButton: Button? = null
    private var smButton: Button? = null

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (action == microService.ACTION_TIMER_TICK) {
                val tis = intent.getIntExtra(microService.DATA_TIMER_TIME_LEFT, 0)
                onTimerTick(tis)
            }
            else if (action == microService.ACTION_TIMER_FINISH) {
                onTimerFinish()
            }
        }
    }

    private val minSteps = 60 * 1
    private val secSteps = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager.init()
        audioManager.play("boot")

        timerText = findViewById(R.id.timer_text)
        updateTimerText()

        startButton = findViewById(R.id.button_start)
        stopButton = findViewById(R.id.button_stop)

        mpButton = findViewById(R.id.button_mp)
        spButton = findViewById(R.id.button_sp)
        mmButton = findViewById(R.id.button_mm)
        smButton = findViewById(R.id.button_sm)

        // set on-click listener
        startButton!!.setOnClickListener {
            if(timeInSeconds > 0) {

                if (microState == MicroState.IDLE || microState == MicroState.PAUSE) {
                    startMicrowave()
                    enableUIOnStateChange = true
                    setEnableUI(false)
                }
                else if (microState == MicroState.RUNNING) {
                    pauseMicrowave()
                    enableUIOnStateChange = true
                    setEnableUI(false)
                }
            }
            audioManager.play("up")
        }

        stopButton!!.setOnClickListener {
            if(microState == MicroState.RUNNING || microState == MicroState.PAUSE) {
                stopMicrowave()
                stopTimeService()
                enableUIOnStateChange = true
                setEnableUI(false)
            }
        }

        mpButton!!.setOnClickListener {
            if(timeInSeconds < minSteps * 99) {
                timeInSeconds += minSteps
                if(microState == MicroState.RUNNING || microState == MicroState.PAUSE) {
                    networkManager.addTimeToMicrowave(minSteps)
                    microService.addTime(this, minSteps)
                }
            }
            updateTimerText()
            audioManager.play("up")
        }

        spButton!!.setOnClickListener {
            if(timeInSeconds < minSteps * 99) {
                timeInSeconds += secSteps
                if(microState == MicroState.RUNNING || microState == MicroState.PAUSE) {
                    networkManager.addTimeToMicrowave(secSteps)
                    microService.addTime(this, secSteps)
                }
            }
            audioManager.play("up")
            updateTimerText()
        }

        mmButton!!.setOnClickListener {
            if(timeInSeconds - minSteps >= 0) {
                timeInSeconds -= minSteps
                if(microState == MicroState.RUNNING || microState == MicroState.PAUSE) {
                    networkManager.addTimeToMicrowave(minSteps * -1)
                    microService.addTime(this, minSteps * -1)
                }
            }
            audioManager.play("down")
            updateTimerText()
        }


        smButton!!.setOnClickListener {
            if(timeInSeconds - secSteps >= 0) {
                timeInSeconds -= secSteps
                if(microState == MicroState.RUNNING || microState == MicroState.PAUSE) {
                    networkManager.addTimeToMicrowave(secSteps * -1)
                    microService.addTime(this, secSteps * -1)
                }
            }
            audioManager.play("down")
            updateTimerText()
        }

        networkManager.addConnectionUpdateCallback(this)
        networkManager.addStatusUpdateCallback(this)
        networkManager.connectToMicrowave()

        if(!networkManager.isConnected)
            showConnectingDialog()
    }

    override fun onStart() {
        super.onStart()

        audioManager.mute(false)
        networkManager.sendStatusUpdateRequest()

        val filter = IntentFilter()
        filter.addAction(microService.ACTION_TIMER_TICK)
        filter.addAction(microService.ACTION_TIMER_FINISH)
        registerReceiver(receiver, filter)
    }

    private fun startTimeService(tis: Int){
        microService.startTimer(this, tis)
        audioManager.play("loop", true, 0.5f)
        updateTimerText()
    }

    private fun startMicrowave(){
        if (microState == MicroState.IDLE || microState == MicroState.PAUSE) {
            networkManager.startMicrowave(timeInSeconds)
        }
    }

    private fun pauseTimeService(tis: Int){
        microService.pauseTimer(this, tis)
        audioManager.stop("loop")
        updateTimerText()
    }

    private fun pauseMicrowave(){
        if (microState == MicroState.RUNNING) {
            networkManager.pauseMicrowave()
        }
    }

    private fun stopTimeService(){
        microService.stopTimerAndService(this)
        timeInSeconds = 0
        audioManager.play("down")
        audioManager.stop("loop")
        updateTimerText()
    }

    private fun stopMicrowave(){
        if(microState == MicroState.RUNNING || microState == MicroState.PAUSE) {
            networkManager.stopMicrowave()
        }
    }

    private fun showConnectingDialog(){
        connectingDialog = AlertDialog.Builder(this@MainActivity).create()
        connectingDialog!!.setTitle(
            getString(R.string.connecting_dialog_title, getString(R.string.app_name)))
        connectingDialog!!.setMessage(
            getString(R.string.connecting_dialog_content, getString(R.string.app_name)))
        connectingDialog!!.setCancelable(false)
        connectingDialog!!.setButton(
            AlertDialog.BUTTON_NEUTRAL, getString(R.string.connecting_dialog_dismiss)) {
                dialog, _ -> dialog.dismiss(); this.finishAffinity()
        }
        connectingDialog!!.show()
    }

    override fun connectedToMicrowave() {
        this@MainActivity.runOnUiThread {
            connectingDialog?.cancel()
        }
    }

    override fun disconnectedToMicrowave() {
        this@MainActivity.runOnUiThread {
            showConnectingDialog()
        }
    }

    override fun onStatusUpdate(status: JSONObject){
        val tempState = MicroUtils.intToState(status["state"] as Int, MicroState.IDLE)

        if (tempState != microState) {
            val tempTis = status["timeInSeconds"] as Int

            if(enableUIOnStateChange)
                setEnableUI(true)

            when (tempState) {
                MicroState.RUNNING -> {
                    timeInSeconds = if(abs(timeInSeconds - tempTis) > 2) tempTis else timeInSeconds
                    startTimeService(timeInSeconds)
                }
                MicroState.PAUSE -> {
                    timeInSeconds = if(abs(timeInSeconds - tempTis) > 2) tempTis else timeInSeconds
                    pauseTimeService(timeInSeconds)
                }
                MicroState.IDLE -> {
                    //Don't stop current timer if status update is close to 0
                    if(timeInSeconds - tempTis > 5)
                        stopTimeService()
                }
            }

            microState = tempState
            timeInSeconds =  tempTis
            updateTimerText()
            updateStartButton(tempState)
        }
    }

    private fun onTimerTick(tis: Int){
        timeInSeconds = tis
        updateTimerText()
        audioManager.play("down")
    }

    private fun onTimerFinish(){
        audioManager.stop("loop")
        timeInSeconds = 0
        updateTimerText()
    }

    private fun updateTimerText(){
        timerText.text = MicroUtils.secondsToTimeString(this, timeInSeconds)
    }

    private fun updateStartButton(state: MicroState){
        if(state == MicroState.RUNNING) {
            startButton!!.text = getString(R.string.pause)
            ViewCompat.setBackgroundTintList(
                startButton!!,
                ContextCompat.getColorStateList(this, R.color.button_pause))
        }
        else{
            startButton!!.text = getString(R.string.start)
            ViewCompat.setBackgroundTintList(
                startButton!!,
                ContextCompat.getColorStateList(this, R.color.button_start))
        }
    }

    private fun setEnableUI(enable: Boolean){
        this@MainActivity.runOnUiThread {
            startButton!!.isEnabled = enable
            stopButton!!.isEnabled = enable

            mpButton!!.isEnabled = enable
            spButton!!.isEnabled = enable
            mmButton!!.isEnabled = enable
            smButton!!.isEnabled = enable
        }
    }

    override fun onPause() {
        super.onPause()
        audioManager.mute(true)
        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.cleanUp()
        networkManager.removeConnectionUpdateCallback(this)
        networkManager.removeStatusUpdateCallback(this)
    }
}
