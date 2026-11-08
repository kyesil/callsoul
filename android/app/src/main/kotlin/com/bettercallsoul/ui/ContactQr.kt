package com.bettercallsoul.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.bettercallsoul.R
import com.bettercallsoul.calllog.ContactDetail
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

/** How many pixels each module of the shared picture takes. */
private const val SHARE_SCALE = 16
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/**
 * The contact as a vCard small enough for a QR code: name, numbers, e-mails and work, no photo.
 * A phone camera reading it offers to add the contact.
 */
internal fun contactCardText(detail: ContactDetail): String {
    fun esc(value: String) = value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
    return buildString {
        append("BEGIN:VCARD\nVERSION:3.0\n")
        append("N:;").append(esc(detail.name)).append(";;;\n")
        append("FN:").append(esc(detail.name)).append('\n')
        detail.phones.forEach { append("TEL:").append(esc(it.number)).append('\n') }
        detail.emails.forEach { append("EMAIL:").append(esc(it.value)).append('\n') }
        detail.company?.let { append("ORG:").append(esc(it)).append('\n') }
        detail.jobTitle?.let { append("TITLE:").append(esc(it)).append('\n') }
        append("END:VCARD")
    }
}

/** The code as a picture, one pixel a module, with a narrow quiet edge. */
private fun qrBitmap(text: String): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 2,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) BLACK else WHITE
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}.getOrNull()

/** Shares the code as a sharp PNG through the share sheet. */
private fun shareQr(context: Context, code: Bitmap, name: String, title: String) {
    runCatching {
        val folder = File(context.cacheDir, "shared").apply {
            deleteRecursively()
            mkdirs()
        }
        val fileName = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(60).ifEmpty { "contact" }
        val file = File(folder, "$fileName.png")
        val big = Bitmap.createScaledBitmap(code, code.width * SHARE_SCALE, code.height * SHARE_SCALE, false)
        file.outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.logs", file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title))
    }
}

/** The contact's QR code, to show to another phone's camera or to share as a picture. */
@Composable
fun ContactQrDialog(detail: ContactDetail, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val code = remember(detail) { qrBitmap(contactCardText(detail)) }
    val shareTitle = stringResource(R.string.qr_share)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.name, maxLines = 2) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                code?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = stringResource(R.string.qr_title),
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                    )
                }
                Text(
                    stringResource(R.string.qr_hint),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            if (code != null) TextButton(onClick = { shareQr(context, code, detail.name, shareTitle) }) { Text(shareTitle) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
