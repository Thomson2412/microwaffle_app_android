package com.soloheisbeer.microwaffle400.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.soloheisbeer.microwaffle400.MainActivity
import com.soloheisbeer.microwaffle400.R
import com.soloheisbeer.microwaffle400.network.NetworkManager
import com.soloheisbeer.microwaffle400.network.StatusUpdateInterface
import com.soloheisbeer.microwaffle400.timer.MicroTimer
import com.soloheisbeer.microwaffle400.timer.MicroTimerState
import com.soloheisbeer.microwaffle400.timer.TimerStatusInterface
import com.soloheisbeer.microwaffle400.utils.MicroUtils
import org.json.JSONObject


class MicroService : Service(),
    StatusUpdateInterface,
    TimerStatusInterface {

    companion object {

        const val ACTION_TIMER_TICK = "ACTION_TIMER_TICK"
        const val ACTION_TIMER_FINISH = "ACTION_TIMER_FINISH"
        const val DATA_TIMER_TIME_LEFT = "DATA_TIMER_TIME_LEFT"

        private const val ACTION_SERVICE_INIT = "ACTION_SERVICE_INIT"
        private const val ACTION_SERVICE_STOP = "ACTION_SERVICE_STOP"
        private const val ACTION_SERVICE_START_TIMER = "ACTION_SERVICE_START_TIMER"
        private const val ACTION_SERVICE_ADD_TIME_TIMER = "ACTION_SERVICE_ADD_TIME_TIMER"
        private const val ACTION_SERVICE_PAUSE_TIMER = "ACTION_SERVICE_PAUSE_TIMER"
        private const val DATA_SERVICE_TIME = "DATA_TIME"

        fun startTimer(context: Context, timeInSeconds: Int) {
            val startIntent = Intent(context, MicroService::class.java)
            startIntent.putExtra(DATA_SERVICE_TIME, timeInSeconds)
            startIntent.action = ACTION_SERVICE_START_TIMER
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun pauseTimer(context: Context, timeInSeconds: Int) {
            val startIntent = Intent(context, MicroService::class.java)
            startIntent.putExtra(DATA_SERVICE_TIME, timeInSeconds)
            startIntent.action = ACTION_SERVICE_PAUSE_TIMER
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopTimerAndService(context: Context) {
            val stopIntent = Intent(context, MicroService::class.java)
            stopIntent.action = ACTION_SERVICE_STOP
            ContextCompat.startForegroundService(context, stopIntent)
        }

        fun addTime(context: Context, timeInSeconds: Int) {
            val startIntent = Intent(context, MicroService::class.java)
            startIntent.putExtra(DATA_SERVICE_TIME, timeInSeconds)
            startIntent.action = ACTION_SERVICE_ADD_TIME_TIMER
            ContextCompat.startForegroundService(context, startIntent)
        }
    }

    private val notificationIDTimer = 1001
    private val channelTimerID = "MicroServiceTimer"
    private val channelTimerName = "Timer"

    private val notificationIDFinish = 1002
    private val channelFinishID = "MicroServiceTimerFinish"
    private val channelFinishName = "Alarm"

    private val networkManager = NetworkManager
    private val microTimer = MicroTimer(this)

    private lateinit var notificationManager: NotificationManager

    private var state = MicroState.IDLE

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)!!
        networkManager.addStatusUpdateCallback(this)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        val tis = intent.getIntExtra(DATA_SERVICE_TIME, 0)
        startForeground(notificationIDTimer, createTimerNotification(tis))

        when(intent.action){
            ACTION_SERVICE_START_TIMER -> start(tis)
            ACTION_SERVICE_ADD_TIME_TIMER -> addTime(tis)
            ACTION_SERVICE_PAUSE_TIMER -> pause()
            ACTION_SERVICE_STOP -> stop()
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun start(tis: Int){
        microTimer.set(tis)
        microTimer.start()
    }

    private fun pause(){
        microTimer.pause()
    }

    private fun stop(){
        microTimer.reset()
        stopForeground(true)
        stopSelf()
    }

    private fun addTime(timeInSeconds: Int){
        microTimer.add(timeInSeconds)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onTimerTick(timeLeftInSeconds: Int){
        notificationManager.notify(notificationIDTimer, createTimerNotification(timeLeftInSeconds))
        Intent().also { intent ->
            intent.action = ACTION_TIMER_TICK
            intent.putExtra(DATA_TIMER_TIME_LEFT, timeLeftInSeconds)
            intent.setPackage(this.packageName)
            sendBroadcast(intent)
        }
    }

    override fun onTimerFinish(){
        stopForeground(true)
        Intent().also { intent ->
            intent.action = ACTION_TIMER_FINISH
            intent.setPackage(this.packageName)
            sendBroadcast(intent)
        }
        stopSelf()
        notificationManager.notify(notificationIDFinish, createFinishNotification())
    }

    override fun onStatusUpdate(status: JSONObject){
        val tempState = MicroUtils.intToState(status["state"] as Int, MicroState.IDLE)
        val tis = status["timeInSeconds"] as Int

        if(tempState != state) {
            state = tempState

            if (state == MicroState.RUNNING && microTimer.state != MicroTimerState.RUNNING) {
                start(tis)
            } else if (state == MicroState.PAUSE && microTimer.state != MicroTimerState.PAUSED) {
                pause()
            } else if (state == MicroState.IDLE && microTimer.state != MicroTimerState.NOT_SET) {
                stop()
            }
        }

        //Sync if too much out of sync
        if (state == MicroState.RUNNING && microTimer.state == MicroTimerState.RUNNING) {
            if (microTimer.timeInSeconds - tis > 10) {
                val diff = tis - microTimer.timeInSeconds
                microTimer.add(diff)
            }
        }
    }

    private fun createTimerNotification(timeInSeconds: Int): Notification? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelTimerID, channelTimerName,
                NotificationManager.IMPORTANCE_LOW
            )

            notificationManager.createNotificationChannel(serviceChannel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )

        return NotificationCompat.Builder(this, channelTimerID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(MicroUtils.secondsToTimeString(this, timeInSeconds))
            .setSmallIcon(R.drawable.logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(getColor(R.color.colorAccent))
            .setColorized(true)
            .setSound(null)
            .build()
    }

    private fun createFinishNotification(): Notification? {

        val sound = Uri.parse("android.resource://" + this.packageName + "/" + R.raw.alarm);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelFinishID, channelFinishName,
                NotificationManager.IMPORTANCE_HIGH
            )
            serviceChannel.description = getString(R.string.notification_finish)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            serviceChannel.setSound(sound, audioAttributes)
            serviceChannel.enableLights(true)
            serviceChannel.lightColor = R.color.colorAccent
            serviceChannel.enableVibration(true)
            serviceChannel.vibrationPattern =
                longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)

            notificationManager.createNotificationChannel(serviceChannel)
        }

        return NotificationCompat.Builder(this, channelFinishID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_finish))
            .setSmallIcon(R.drawable.logo)
            .setColor(getColor(R.color.colorAccent))
            .setColorized(true)
            .setVibrate(longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400))
            .setSound(sound)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(microTimer.state == MicroTimerState.RUNNING) {
            microTimer.reset()
        }
        networkManager.removeStatusUpdateCallback(this)
    }
}
