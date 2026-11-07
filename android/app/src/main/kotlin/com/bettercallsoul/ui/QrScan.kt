package com.bettercallsoul.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettercallsoul.R
import com.bettercallsoul.codes.ScanResult
import com.bettercallsoul.diag.DebugLog
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Opens Google Play services' code scanner: its own camera screen, so the app needs no camera
 * permission. [onRead] gets the code's text; a cancelled scan just returns.
 */
fun startQrScan(context: Context, onRead: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    try {
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.takeIf(String::isNotBlank)?.let(onRead) }
            .addOnFailureListener { error ->
                DebugLog.w("Scan", "scanner failed", error)
                Toast.makeText(context, R.string.scan_unavailable, Toast.LENGTH_LONG).show()
            }
    } catch (error: RuntimeException) {
        DebugLog.w("Scan", "scanner unavailable", error)
        Toast.makeText(context, R.string.scan_unavailable, Toast.LENGTH_LONG).show()
    }
}

/** What a scanned link or text shows at the bottom of the screen: open it, copy it or close. */
@Composable
fun ScanResultBar(result: ScanResult, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val link = (result as? ScanResult.Link)?.url
    val text = when (result) {
        is ScanResult.Link -> result.url
        is ScanResult.Text -> result.text
        is ScanResult.Phone -> result.number
        is ScanResult.Contact -> return
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (link != null) Icons.Outlined.Link else Icons.Outlined.Notes,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (link != null) R.string.scan_link else R.string.scan_text),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text,
                fontSize = 16.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 10.dp),
            )
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (link != null) {
                    FilledIconButton(onClick = {
                        open(context, link)
                        onDismiss()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.scan_open))
                    }
                }
                OutlinedIconButton(onClick = { copy(context, text) }) {
                    Icon(Icons.Outlined.ContentCopy, stringResource(R.string.scan_copy))
                }
            }
        }
    }
}

/** The system shows its own note when something is copied, so there is no toast of ours. */
private fun copy(context: Context, text: String) = copyText(context, text, null)

/**
 * Puts [text] on the clipboard. Android 13 and later say so themselves; before that [note], when
 * given, shows as a toast.
 */
fun copyText(context: Context, text: String, note: String?) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(null, text))
    if (note != null && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        android.widget.Toast.makeText(context, note, android.widget.Toast.LENGTH_SHORT).show()
    }
}
