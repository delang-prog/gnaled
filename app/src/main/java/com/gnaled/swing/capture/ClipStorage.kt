package com.gnaled.swing.capture

import android.content.Context
import java.io.File
import java.util.UUID

object ClipStorage {
    private const val DIR = "clips"

    fun directory(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun newClipFile(context: Context): ClipHandle {
        val id = UUID.randomUUID().toString()
        val file = File(directory(context), "$id.mp4")
        return ClipHandle(id = id, file = file)
    }
}

data class ClipHandle(val id: String, val file: java.io.File)
