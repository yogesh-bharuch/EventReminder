package com.example.eventreminder.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.eventreminder.logging.DEBUG_TAG
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Holds page setup and spacing configuration for the PDF generator.
 *
 * You can change values here to adjust layout:
 * - pageWidth / pageHeight for different paper sizes
 * - margin for padding
 * - titleSpacing, afterTitleSpacing, afterHeaderSpacing for vertical gaps
 * - rowSpacing for row height (e.g., 70f if you want to fit profile images)
 * - footerBreathing for space above footer
 */
data class PdfLayoutConfig(
    val pageWidth: Int = 595,       // A4 width in points
    val pageHeight: Int = 842,      // A4 height in points
    val margin: Float = 40f,        // Margin from edges
    val titleSpacing: Float = 20f,  // Gap below top margin before title
    val afterTitleSpacing: Float = 40f, // Gap below title before table header
    val afterHeaderSpacing: Float = 30f, // Gap below table header before first row
    val rowSpacing: Float = 25f,    // Vertical spacing between rows
    val footerBreathing: Float = 40f // Breathing space above footer
)

/**
 * A sealed class representing a cell in the PDF table.
 * Each type of cell can define its own rendering logic.
 */
sealed class PdfCell {
    data class TextCell(val text: String) : PdfCell()
    data class ImageCell(val bitmap: Bitmap, val scale: Float = 1.0f) : PdfCell()
    // scale lets you shrink/enlarge the image inside its column
}

/**
 * Generic PDF generator that accepts headers, column widths, rows, and layout configuration.
 *
 * - Title, table header, and footer are repeated on each page.
 * - Automatically starts a new page when rows exceed available space.
 * - Supports both TextCell and ImageCell.
 *
 * @param context Android context
 * @param title Title text for each page
 * @param headers List of header labels
 * @param colWidths List of column widths (must match headers size)
 * @param rows List of rows, each row is a list of PdfCell
 * @param layout Layout configuration (page size, margins, spacing)
 * @param fileName Output file name
 */
fun generateGenericPdf(
    context: Context,
    title: String,
    headers: List<String>,
    colWidths: List<Float>,
    rows: List<List<PdfCell>>,
    layout: PdfLayoutConfig,
    fileName: String = "table.pdf"
) {

    Timber.tag(DEBUG_TAG).d("Entered in. [generateGenericPdf.kt::generateGenericPdf]")
    val pdfDocument = PdfDocument()

    // Use values from layout config
    val pageWidth = layout.pageWidth
    val pageHeight = layout.pageHeight
    val margin = layout.margin
    val titleSpacing = layout.titleSpacing
    val afterTitleSpacing = layout.afterTitleSpacing
    val afterHeaderSpacing = layout.afterHeaderSpacing
    val rowSpacing = layout.rowSpacing
    val footerBreathing = layout.footerBreathing

    // Paints
    val headerPaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
    val bodyPaint = Paint().apply { textSize = 14f }
    val footerPaint = Paint().apply { textSize = 12f }

    // Date string for footer
    val date = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())

    var pageNumber = 1
    data class PageCtx(val page: PdfDocument.Page, val canvas: Canvas, val startY: Float)

    // Helper: start a new page and draw title + header
    fun startNewPage(): PageCtx {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Title
        val headerY = margin + titleSpacing
        canvas.drawText(title, margin, headerY, headerPaint)

        // Table header row
        val tableHeaderY = headerY + afterTitleSpacing
        var x = margin
        headers.forEachIndexed { i, text ->
            canvas.drawText(text, x, tableHeaderY, bodyPaint)
            x += colWidths[i]
        }

        val firstRowY = tableHeaderY + afterHeaderSpacing
        return PageCtx(page, canvas, firstRowY)
    }

    // Start first page
    var ctx = startNewPage()
    var y = ctx.startY

    val footerY = pageHeight - margin
    val contentBottom = footerY - footerBreathing

    // Iterate rows and draw cells
    rows.forEachIndexed { _, row ->
        if (y + rowSpacing > contentBottom) {
            val footerText = "Report generated on $date | Page $pageNumber"
            ctx.canvas.drawText(footerText, margin, footerY, footerPaint)
            pdfDocument.finishPage(ctx.page)

            pageNumber++
            ctx = startNewPage()
            y = ctx.startY
        }

        var x = margin
        row.forEachIndexed { i, cell ->
            when (cell) {
                is PdfCell.TextCell -> {
                    ctx.canvas.drawText(cell.text, x, y, bodyPaint)
                }
                is PdfCell.ImageCell -> {
                    // Scale image to fit column
                    val imgWidth = cell.bitmap.width * cell.scale
                    val imgHeight = cell.bitmap.height * cell.scale

                    // Draw image inside column rectangle
                    val rect = RectF(x, y - imgHeight, x + colWidths[i], y)
                    ctx.canvas.drawBitmap(cell.bitmap, null, rect, null)
                }
            }
            x += colWidths[i] // move to next column
        }

        y += rowSpacing
    }

    // Footer on last page
    val footerText = "Report generated on $date | Page $pageNumber"
    ctx.canvas.drawText(footerText, margin, footerY, footerPaint)
    pdfDocument.finishPage(ctx.page)

    // Save PDF
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
    pdfDocument.writeTo(FileOutputStream(file))
    Timber.tag(DEBUG_TAG).d("$file Saved. [generateGenericPdf.kt::generateGenericPdf]")
    pdfDocument.close()
}

/**
 * Example usage:
 *
 * val headers = listOf("Sr.No", "Name", "Lastname", "Profile")
 * val colWidths = listOf(60f, 120f, 120f, 100f)
 *
 * // Suppose you already have a Bitmap profilePic loaded
 * val rows = listOf(
 *     listOf(
 *         PdfCell.TextCell("1"),
 *         PdfCell.TextCell("Yogesh"),
 *         PdfCell.TextCell("Vyas"),
 *         PdfCell.ImageCell(profilePic, scale = 0.5f) // shrink to fit column
 *     )
 * )
 *
 * // Custom layout config with rowSpacing = 70f to fit profile images
 * val layout = PdfLayoutConfig(
 *     pageWidth = 595,
 *     pageHeight = 842,
 *     margin = 50f,
 *     titleSpacing = 30f,
 *     afterTitleSpacing = 50f,
 *     afterHeaderSpacing = 35f,
 *     rowSpacing = 70f,   // ✅ taller rows for images
 *     footerBreathing = 30f
 * )
 *
 * generateGenericPdf(context, "My Contacts", headers, colWidths, rows, layout, "contacts.pdf")
 *
 * // NOTE: Preserve comments if you want to add/change values later.
 * //       Most welcome to adjust margins, spacing, or widths as needed.
 */


























/*
package com.example.eventreminder.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Holds page setup and spacing configuration for the PDF generator.
 *
 * You can change values here to adjust layout:
 * - pageWidth / pageHeight for different paper sizes
 * - margin for padding
 * - titleSpacing, afterTitleSpacing, afterHeaderSpacing for vertical gaps
 * - rowSpacing for row height
 * - footerBreathing for space above footer
 */
data class PdfLayoutConfig(
    val pageWidth: Int = 595,       // A4 width in points
    val pageHeight: Int = 842,      // A4 height in points
    val margin: Float = 40f,        // Margin from edges
    val titleSpacing: Float = 20f,  // Gap below top margin before title
    val afterTitleSpacing: Float = 40f, // Gap below title before table header
    val afterHeaderSpacing: Float = 30f, // Gap below table header before first row
    val rowSpacing: Float = 25f,    // Vertical spacing between rows
    val footerBreathing: Float = 40f // Breathing space above footer
)

/**
 * A sealed class representing a cell in the PDF table.
 * Each type of cell can define its own rendering logic.
 */
sealed class PdfCell {
    data class TextCell(val text: String) : PdfCell()
    // Future: add ImageCell, NumberCell, etc.
}

/**
 * Generic PDF generator that accepts headers, column widths, rows, and layout configuration.
 *
 * - Title, table header, and footer are repeated on each page.
 * - Automatically starts a new page when rows exceed available space.
 *
 * @param context Android context
 * @param title Title text for each page
 * @param headers List of header labels
 * @param colWidths List of column widths (must match headers size)
 * @param rows List of rows, each row is a list of PdfCell
 * @param layout Layout configuration (page size, margins, spacing)
 * @param fileName Output file name
 */
fun generateGenericPdf(
    context: Context,
    title: String,
    headers: List<String>,
    colWidths: List<Float>,
    rows: List<List<PdfCell>>,
    layout: PdfLayoutConfig,
    fileName: String = "table.pdf"
) {
    // -------------------------------
    // Create PDF document
    // -------------------------------
    val pdfDocument = PdfDocument()

    // -------------------------------
    // Use values from layout config
    // -------------------------------
    val pageWidth = layout.pageWidth
    val pageHeight = layout.pageHeight
    val margin = layout.margin
    val titleSpacing = layout.titleSpacing
    val afterTitleSpacing = layout.afterTitleSpacing
    val afterHeaderSpacing = layout.afterHeaderSpacing
    val rowSpacing = layout.rowSpacing
    val footerBreathing = layout.footerBreathing

    // -------------------------------
    // Paints for text rendering
    // -------------------------------
    val headerPaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
    val bodyPaint = Paint().apply { textSize = 14f }
    val footerPaint = Paint().apply { textSize = 12f }

    // -------------------------------
    // Date string for footer
    // -------------------------------
    val date = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())

    // -------------------------------
    // Page state variables
    // -------------------------------
    var pageNumber = 1

    data class PageCtx(val page: PdfDocument.Page, val canvas: Canvas, val startY: Float)

    // -------------------------------
    // Helper: start a new page and draw title + header
    // -------------------------------
    fun startNewPage(): PageCtx {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Title
        val headerY = margin + titleSpacing
        canvas.drawText(title, margin, headerY, headerPaint)

        // Table header row
        val tableHeaderY = headerY + afterTitleSpacing
        var x = margin
        headers.forEachIndexed { i, text ->
            canvas.drawText(text, x, tableHeaderY, bodyPaint)
            x += colWidths[i]
        }

        val firstRowY = tableHeaderY + afterHeaderSpacing
        return PageCtx(page, canvas, firstRowY)
    }

    // -------------------------------
    // Start first page
    // -------------------------------
    var ctx = startNewPage()
    var y = ctx.startY

    val footerY = pageHeight - margin
    val contentBottom = footerY - footerBreathing

    // -------------------------------
    // Iterate rows and draw cells
    // -------------------------------
    rows.forEachIndexed { rowIndex, row ->
        if (y + rowSpacing > contentBottom) {
            val footerText = "Report generated on $date | Page $pageNumber"
            ctx.canvas.drawText(footerText, margin, footerY, footerPaint)
            pdfDocument.finishPage(ctx.page)

            pageNumber++
            ctx = startNewPage()
            y = ctx.startY
        }

        // Draw each cell in the row
        var x = margin
        row.forEachIndexed { i, cell ->
            when (cell) {
                is PdfCell.TextCell -> ctx.canvas.drawText(cell.text, x, y, bodyPaint)
                // Future: handle other cell types here
            }
            x += colWidths[i] // move to next column
        }

        y += rowSpacing
    }

    // -------------------------------
    // Footer on last page
    // -------------------------------
    val footerText = "Report generated on $date | Page $pageNumber"
    ctx.canvas.drawText(footerText, margin, footerY, footerPaint)
    pdfDocument.finishPage(ctx.page)

    // -------------------------------
    // Save PDF
    // -------------------------------
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()
}

/**
 * Example usage:
 *
 * val headers = listOf("Sr.No", "Name", "Lastname", "Phone")
 * val colWidths = listOf(60f, 120f, 120f, 200f)
 * val rows = listOf(
 *     listOf(PdfCell.TextCell("1"), PdfCell.TextCell("Yogesh"), PdfCell.TextCell("Vyas"), PdfCell.TextCell("9998000000")),
 *     listOf(PdfCell.TextCell("2"), PdfCell.TextCell("Rahul"), PdfCell.TextCell("Sharma"), PdfCell.TextCell("8888000000"))
 * )
 *
 * // Custom layout config (e.g., bigger margins, tighter row spacing)
 * val layout = PdfLayoutConfig(
 *     pageWidth = 595,
 *     pageHeight = 842,
 *     margin = 50f,
 *     titleSpacing = 30f,
 *     afterTitleSpacing = 50f,
 *     afterHeaderSpacing = 35f,
 *     rowSpacing = 20f,
 *     footerBreathing = 30f
 * )
 *
 * generateGenericPdf(context, "My Contacts", headers, colWidths, rows, layout, "contacts.pdf")
 *
 * // NOTE: Preserve comments if you want to add/change values later.
 * //       Most welcome to adjust margins, spacing, or widths as needed.
 */

/*
To extend it for images (like a profile photo) you’d add a new type to the sealed class and handle it in the drawing loop.

🟦 Step 1: Extend the sealed class
sealed class PdfCell {
    data class TextCell(val text: String) : PdfCell()
    data class ImageCell(val bitmap: android.graphics.Bitmap, val scale: Float = 1.0f) : PdfCell()
    // scale lets you shrink/enlarge the image inside its column
}



🟦 Step 2: Handle ImageCell in the row drawing loop
row.forEachIndexed { i, cell ->
    when (cell) {
        is PdfCell.TextCell -> {
            ctx.canvas.drawText(cell.text, x, y, bodyPaint)
        }
        is PdfCell.ImageCell -> {
            // Measure image size
            val imgWidth = cell.bitmap.width * cell.scale
            val imgHeight = cell.bitmap.height * cell.scale

            // Draw image at (x, y) baseline adjusted
            // Here we align the bottom of the image with the text baseline
            ctx.canvas.drawBitmap(cell.bitmap, null,
                android.graphics.RectF(x, y - imgHeight, x + imgWidth, y),
                null
            )
        }
    }
    x += colWidths[i] // move to next column
}



🟦 Step 3: Example usage
val headers = listOf("Sr.No", "Name", "Lastname", "Profile")
val colWidths = listOf(60f, 120f, 120f, 100f)

// Suppose you already have a Bitmap profilePic loaded
val rows = listOf(
    listOf(
        PdfCell.TextCell("1"),
        PdfCell.TextCell("Yogesh"),
        PdfCell.TextCell("Vyas"),
        PdfCell.ImageCell(profilePic, scale = 0.2f) // shrink to fit
    )
)



✅ Key Points
- Sealed class makes it easy to add new cell types (ImageCell, NumberCell, etc.).
- Scaling ensures the image fits inside its column width.
- Alignment: you can adjust RectF to align top, center, or baseline.
- Flexibility: same engine, just extended to handle images.

👉 So in the future, you’d simply add ImageCell to your sealed class and extend the drawing loop. Everything else (pagination, layout config, headers, widths) stays the same.



 */

*/









































/*
package com.example.eventreminder.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates a PDF document of contacts with dynamic pagination.
 *
 * - Title, table header, and footer are repeated on each page.
 * - Columns: Sr.No, Name, Lastname, Phone Number (declared once with widths).
 * - Automatically starts a new page when rows exceed available space.
 *
 * @param context Android context used to access file storage
 * @param contacts List of contacts, each as [Name, Lastname, PhoneNumber]
 */
fun generateContactsPdf(context: Context, contacts: List<List<String>>) {
    // -------------------------------
    // Create PDF document
    // -------------------------------
    val pdfDocument = PdfDocument()

    // -------------------------------
    // Page setup (A4-like dimensions)
    // -------------------------------
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f

    // -------------------------------
    // Spacing constants (relative gaps)
    // -------------------------------
    val titleSpacing = 20f
    val afterTitleSpacing = 40f
    val afterHeaderSpacing = 30f
    val rowSpacing = 25f
    val footerBreathing = 40f

    // -------------------------------
    // Column headers and widths
    // -------------------------------
    val headers = listOf("Sr. No.", "Name", "Lastname", "Phone Number")
    val colWidths = listOf(60f, 120f, 120f, 200f)

    // -------------------------------
    // Paints for text rendering
    // -------------------------------
    val headerPaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
    val bodyPaint = Paint().apply { textSize = 14f }
    val footerPaint = Paint().apply { textSize = 12f }

    // -------------------------------
    // Date string for footer
    // -------------------------------
    val date = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())

    // -------------------------------
    // Page state variables
    // -------------------------------
    var pageNumber = 1

    data class PageCtx(val page: PdfDocument.Page, val canvas: Canvas, val startY: Float)

    // -------------------------------
    // Helper: start a new page and draw title + header
    // -------------------------------
    fun startNewPage(): PageCtx {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Title
        val headerY = margin + titleSpacing
        canvas.drawText("My Contacts", margin, headerY, headerPaint)

        // Table header row
        val tableHeaderY = headerY + afterTitleSpacing
        var x = margin
        headers.forEachIndexed { i, text ->
            canvas.drawText(text, x, tableHeaderY, bodyPaint)
            x += colWidths[i]
        }

        val firstRowY = tableHeaderY + afterHeaderSpacing
        return PageCtx(page, canvas, firstRowY)
    }

    // -------------------------------
    // Start first page
    // -------------------------------
    var ctx = startNewPage()
    var y = ctx.startY

    val footerY = pageHeight - margin
    val contentBottom = footerY - footerBreathing

    // -------------------------------
    // Iterate contacts and draw rows
    // -------------------------------
    contacts.forEachIndexed { index, contact ->
        if (y + rowSpacing > contentBottom) {
            val footerText = "Report generated on $date | Page $pageNumber"
            ctx.canvas.drawText(footerText, margin, footerY, footerPaint)
            pdfDocument.finishPage(ctx.page)

            pageNumber++
            ctx = startNewPage()
            y = ctx.startY
        }

        // rowValues = listOf("1", "yogesh", "vyas", "9998000000")
        val rowValues = listOf(
            "${index + 1}",              // serial number as string
            contact.getOrNull(0) ?: "-", // first element = Name
            contact.getOrNull(1) ?: "-", // second element = Lastname
            contact.getOrNull(2) ?: "-"  // third element = Phone Number
        )

        // ✅ Simplified row drawing using colWidths
        var x = margin
        rowValues.forEachIndexed { i, text ->
            ctx.canvas.drawText(text, x, y, bodyPaint)
            x += colWidths[i]
            // after drawing each cell, move the horizontal cursor to the right by the width of that column.
            // y is the baseline for the row.
        }

        y += rowSpacing
    }

    // -------------------------------
    // Footer on last page
    // -------------------------------
    val footerText = "Report generated on $date | Page $pageNumber"
    ctx.canvas.drawText(footerText, margin, footerY, footerPaint)
    pdfDocument.finishPage(ctx.page)

    // -------------------------------
    // Save PDF
    // -------------------------------
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "contacts.pdf")
    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()
}


 */