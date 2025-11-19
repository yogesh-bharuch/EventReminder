package com.example.eventreminder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File


/*
✔ Accepts real file path
✔ Checks if file exists
✔ Uses FileProvider correctly
✔ Uses correct authority
✔ Uses correct flags
✔ Handles exceptions gracefully
* */
fun openPdf(context: Context, realPath: String) {

    val file = File(realPath)

    if (!file.exists()) {
        Timber.tag("PDF_OPEN").e("File does not exist: $realPath")
        return
    }

    val uri = FileProvider.getUriForFile(
        context,
        "com.example.eventreminder.provider",
        file
    )

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.tag("PDF_OPEN").e(e, "No PDF viewer found")
    }
}
