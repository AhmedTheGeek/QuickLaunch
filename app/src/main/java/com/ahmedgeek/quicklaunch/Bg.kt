package com.ahmedgeek.quicklaunch

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Threading primitives. One background looper for index/frecency I/O, a tiny pool for icon rasterization. */
object Bg {
    val main: Handler = Handler(Looper.getMainLooper())

    private val thread = HandlerThread("ql-bg", Process.THREAD_PRIORITY_DEFAULT).apply { start() }

    /** Serial background handler: all index and frecency reads/writes happen here, so no locking is needed. */
    val bg: Handler = Handler(thread.looper)

    val icons: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "ql-icon").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    fun isMain(): Boolean = Looper.myLooper() === Looper.getMainLooper()

    fun isBg(): Boolean = Looper.myLooper() === thread.looper
}
