package com.example.eventreminder.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.eventreminder.logging.DEBUG_TAG
import timber.log.Timber
import java.io.OutputStream
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
 * IMPORTANT ARCHITECTURE NOTE:
 * - This function is PURELY a renderer.
 * - It does NOT decide where the PDF is stored.
 * - It writes ONLY to the provided OutputStream.
 *
 * @param context Android context (used only for rendering-related needs)
 * @param title Title text for each page
 * @param headers List of header labels
 * @param colWidths List of column widths (must match headers size)
 * @param rows List of rows, each row is a list of PdfCell
 * @param layout Layout configuration (page size, margins, spacing)
 * @param outputStream OutputStream provided by the caller (MediaStore, etc.)
 */
fun generateGenericPdf(
    context: Context,
    title: String,
    headers: List<String>,
    colWidths: List<Float>,
    rows: List<List<PdfCell>>,
    layout: PdfLayoutConfig,
    outputStream: OutputStream
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
    val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
    val headerPaint = Paint().apply { textSize = 14f; isFakeBoldText = true }
    val bodyPaint = Paint().apply { textSize = 14f }
    val footerPaint = Paint().apply { textSize = 12f }

    // Date string for footer
    val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

    var pageNumber = 1
    data class PageCtx(val page: PdfDocument.Page, val canvas: Canvas, val startY: Float)

    /**
     * Truncates text with ellipsis so it fits within the given width.
     * Minimal, safe behavior to avoid column overflow.
     */
    fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        // Keep a small gap so text doesn't touch the next column
        val effectiveWidth = maxWidth - 4f
        if (effectiveWidth <= 0f) return "…"

        if (paint.measureText(text) <= effectiveWidth) return text

        val ellipsis = "…"
        var end = text.length

        while (
            end > 0 &&
            paint.measureText(text.substring(0, end) + ellipsis) > effectiveWidth
        ) {
            end--
        }

        return if (end > 0) text.substring(0, end) + ellipsis else ellipsis
    }

    // Helper: start a new page and draw title + header
    fun startNewPage(): PageCtx {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Title
        val headerY = margin + titleSpacing
        canvas.drawText(title, margin, headerY, titlePaint)
        //canvas.drawText(title, margin, headerY, headerPaint)

        // Table header row
        val tableHeaderY = headerY + afterTitleSpacing
        var x = margin
        headers.forEachIndexed { i, text ->
            canvas.drawText(text, x, tableHeaderY, headerPaint)
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
    rows.forEach { row ->
        // if not enough space print footer
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
                    val safeText = ellipsize(
                        text = cell.text,
                        paint = bodyPaint,
                        maxWidth = colWidths[i]
                    )
                    ctx.canvas.drawText(safeText, x, y, bodyPaint)
                }

                is PdfCell.ImageCell -> {
                    // Scale image to fit column
                    val imgHeight = cell.bitmap.height * cell.scale
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

    // Write PDF content to the provided OutputStream
    pdfDocument.writeTo(outputStream)
    pdfDocument.close()

    Timber.tag(DEBUG_TAG).d("PDF render completed. [generateGenericPdf.kt::generateGenericPdf]")
}
