package com.example.eventreminder.util

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.core.net.toUri

fun openPdf(context: android.content.Context, uriString: String) {
    val uri = uriString.toUri()

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    }

    context.startActivity(intent)
    // v1.2
}
