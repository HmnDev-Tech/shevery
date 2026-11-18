package rikka.shizuku.server

import android.os.FileObserver
import android.util.Log
import java.io.File

interface ApkChangedListener {
    fun onApkChanged()
}

object ApkChangedObservers {

    private val observers = mutableMapOf<String, ApkChangedObserver>()

    @JvmStatic
    fun start(apkPath: String, listener: ApkChangedListener) {
        // inotify watchs inode, if the there are still processes holds the file, DELTE_SELF will not be triggered
        // so we need to watch the parent folder

        val path = File(apkPath).parent!!
        synchronized(observers) {
            val observer = observers.getOrPut(path) { ApkChangedObserver(path) }
            if (observer.listeners.add(listener)) {
                observer.startWatching()
            }
        }
    }

    @JvmStatic
    fun stop(listener: ApkChangedListener) {
        synchronized(observers) {
            val iterator = observers.values.iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                observer.listeners.remove(listener)
                if (observer.listeners.isEmpty()) {
                    iterator.remove()
                    observer.stopWatching()
                }
            }
        }
    }

    private class ApkChangedObserver(private val directory: String) : FileObserver(directory, DELETE) {
        val listeners = mutableSetOf<ApkChangedListener>()

        override fun onEvent(event: Int, path: String?) {
            Log.d("ShizukuServer", "onEvent: ${eventToString(event)} $path")
            if (event and DELETE == 0 || path != "base.apk") return

            val snapshot = synchronized(ApkChangedObservers.observers) {
                if (ApkChangedObservers.observers[directory] !== this) return
                ApkChangedObservers.observers.remove(directory)
                stopWatching()
                listeners.toList()
            }
            snapshot.forEach { it.onApkChanged() }
        }
    }
}

private fun eventToString(event: Int): String {
    val sb = StringBuilder()
    if (event and FileObserver.ACCESS == FileObserver.ACCESS) {
        sb.append("ACCESS").append(" | ")
    }
    if (event and FileObserver.MODIFY == FileObserver.MODIFY) {
        sb.append("MODIFY").append(" | ")
    }
    if (event and FileObserver.ATTRIB == FileObserver.ATTRIB) {
        sb.append("ATTRIB").append(" | ")
    }
    if (event and FileObserver.CLOSE_WRITE == FileObserver.CLOSE_WRITE) {
        sb.append("CLOSE_WRITE").append(" | ")
    }
    if (event and FileObserver.CLOSE_NOWRITE == FileObserver.CLOSE_NOWRITE) {
        sb.append("CLOSE_NOWRITE").append(" | ")
    }
    if (event and FileObserver.OPEN == FileObserver.OPEN) {
        sb.append("OPEN").append(" | ")
    }
    if (event and FileObserver.MOVED_FROM == FileObserver.MOVED_FROM) {
        sb.append("MOVED_FROM").append(" | ")
    }
    if (event and FileObserver.MOVED_TO == FileObserver.MOVED_TO) {
        sb.append("MOVED_TO").append(" | ")
    }
    if (event and FileObserver.CREATE == FileObserver.CREATE) {
        sb.append("CREATE").append(" | ")
    }
    if (event and FileObserver.DELETE == FileObserver.DELETE) {
        sb.append("DELETE").append(" | ")
    }
    if (event and FileObserver.DELETE_SELF == FileObserver.DELETE_SELF) {
        sb.append("DELETE_SELF").append(" | ")
    }
    if (event and FileObserver.MOVE_SELF == FileObserver.MOVE_SELF) {
        sb.append("MOVE_SELF").append(" | ")
    }

    if (event and 0x00008000 == 0x00008000) {
        sb.append("IN_IGNORED").append(" | ")
    }

    if (event and 0x40000000 == 0x40000000) {
        sb.append("IN_ISDIR").append(" | ")
    }

    return if (sb.isNotEmpty()) {
        sb.substring(0, sb.length - 3)
    } else {
        sb.toString()
    }
}
