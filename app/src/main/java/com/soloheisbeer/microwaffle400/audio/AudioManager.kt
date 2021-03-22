package com.soloheisbeer.microwaffle400.audio

import android.content.Context
import android.media.MediaPlayer

class AudioManager (private val context: Context) {

    private var mute = false

    var isReady = false
    var sounds = mutableMapOf<String, MediaPlayer>()

    fun init() {
        if (isReady) return
        val fields = arrayListOf(
            "alarm",
            "boot",
            "down",
            "loop",
            "up"
        )
        for (field in fields) {
            val mp = MediaPlayer.create(context, context.resources.getIdentifier(field, "raw", context.packageName))
            sounds[field] = mp
        }
        isReady = true
    }

    fun play(name: String, loop: Boolean = false, volume: Float = 1.0f){
        if (sounds[name] == null || !isReady || mute) return
        val sound = sounds[name]!!
        sound.isLooping = loop
        sound.setVolume(volume, volume)

        stop(name)
        sound.start()
    }

    fun stop(name: String){
        if (sounds[name] == null || !isReady) return
        val sound = sounds[name]!!
            sound.stop()
            sound.prepare()
    }

    fun mute(m: Boolean){
        mute = m
        if(m){
            for (key in sounds.keys){
                stop(key)
            }
        }
    }

    fun cleanUp() {
        isReady = false
        for(mp in sounds.values){
            mp.reset()
            mp.release()
        }
        sounds.clear()
    }
}