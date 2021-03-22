package com.soloheisbeer.microwaffle400.utils

import android.content.Context
import com.soloheisbeer.microwaffle400.R
import com.soloheisbeer.microwaffle400.service.MicroState

object MicroUtils {
    fun secondsToTimeString(context: Context, tis: Int): String{
        val min  = tis / 60
        val sec = tis % 60
        return context.getString(R.string.timer, min, sec)
    }

    fun intToState(stateAsInt: Int, default: MicroState): MicroState {
        return when(stateAsInt){
            0 -> MicroState.IDLE
            1 -> MicroState.RUNNING
            2 -> MicroState.PAUSE
            else -> default
        }
    }
}