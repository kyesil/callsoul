package com.bettercallsoul

import com.bettercallsoul.ui.Route
import com.bettercallsoul.widget.Widgets
import com.bettercallsoul.widget.WidgetActionActivity
import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.lifecycleScope
import com.bettercallsoul.ui.BcsTheme
import com.bettercallsoul.ui.DefaultPhonePrompt
import com.bettercallsoul.ui.MainScreen
import com.bettercallsoul.ui.SetupScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.bettercallsoul.calllog.ContactRow
import com.bettercallsoul.calllog.favoritesInOrder
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.bettercallsoul.telecom.CallManager
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle

class MainActivity : ComponentActivity() {
    private var permissionsGranted by mutableStateOf(false)
    private var isDefaultDialer by mutableStateOf(false)
    private var askForDefault by mutableStateOf(false)
    private var lastTouch = Offset.Zero
    private var dialRequest by mutableStateOf<String?>(null)

    /** From the missed-call notification: a number to open, or empty for the recents list. */
    private var missedRequest by mutableStateOf<String?>(null)

    /** From a widget: the person's page to open. */
    private var openRequest by mutableStateOf<Route?>(null)

    /** Debug builds only: the text of a QR code, as if it had been scanned, for tests without a camera. */
    private var scanRequest by mutableStateOf<String?>(null)

    /** The favourites last put in the app icon's menu, so coming back to the app doesn't redo them. */
    private var shortcutFavorites: List<ContactRow>? = null

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    private val requestRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Recreated for a theme change, the activity gets the same intent again: not a new request.
        if (savedInstanceState == null) handleDial(intent)
        followFavorites()
        // Known before the first frame, so the question doesn't flash up for an app that already has the role.
        isDefaultDialer = holdsDialerRole()
        askForDefault = !container.defaultPhoneAsked
        setContent {
            BcsTheme {
                if (permissionsGranted) {
                    MainScreen(
                        isDefaultDialer = isDefaultDialer,
                        dialRequest = dialRequest,
                        onDialRequestHandled = { dialRequest = null },
                        missedRequest = missedRequest,
                        onMissedRequestHandled = { missedRequest = null },
                        onRequestDefault = ::askForDialerRole,
                        scanRequest = scanRequest,
                        onScanRequestHandled = { scanRequest = null },
                        openRequest = openRequest,
                        onOpenRequestHandled = { openRequest = null },
                        lastTouch = { lastTouch },
                    )
                    if (askForDefault && !isDefaultDialer) {
                        DefaultPhonePrompt(
                            onAccept = {
                                answerDefaultQuestion()
                                askForDialerRole()
                            },
                            onDismiss = ::answerDefaultQuestion,
                        )
                    }
                } else {
                    SetupScreen(onRequestPermissions = { requestPermissions.launch(REQUIRED_PERMISSIONS) })
                }
            }
        }
    }

    /** Remembered for the burst of a call placed here, which starts where the finger was. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        lastTouch = Offset(event.x, event.y)
        return super.dispatchTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Calls made and contacts changed while here show in the widgets once the app is left. */
    override fun onStop() {
        super.onStop()
        if (permissionsGranted) Widgets.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDial(intent)
    }

    private fun handleDial(intent: Intent) {
        if (BuildConfig.DEBUG) intent.getStringExtra(EXTRA_DEBUG_SCAN)?.let { scanRequest = it }
        when (intent.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> dialRequest = intent.data?.schemeSpecificPart
            FavoriteShortcuts.ACTION_CALL -> callFavorite(intent)
            WidgetActionActivity.ACTION_OPEN -> {
                val contact = intent.getLongExtra(WidgetActionActivity.EXTRA_CONTACT, -1)
                val number = intent.getStringExtra(WidgetActionActivity.EXTRA_NUMBER).orEmpty()
                openRequest = if (contact >= 0) Route.Contact(contact) else number.takeIf(String::isNotBlank)?.let(Route::Number)
            }
            MissedCallReceiver.ACTION_OPEN_MISSED -> {
                MissedCallReceiver.clear(this)
                missedRequest = intent.getStringExtra(MissedCallReceiver.EXTRA_NUMBER).orEmpty()
            }
        }
    }

    private fun refresh() {
        permissionsGranted = REQUIRED_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        isDefaultDialer = holdsDialerRole()
        if (permissionsGranted) {
            // A phone that refuses the contact must not take the app down with it, as One UI 9 did.
            lifecycleScope.launch { runCatching { seedOwnNumber(this@MainActivity) } }
            lifecycleScope.launch { container.history.sync() }
            lifecycleScope.launch { container.trash.purgeOld() }
        }
    }

    /** Keeps the app icon's menu in step with the favourites, once the permissions allow reading them. */
    private fun followFavorites() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                snapshotFlow { permissionsGranted }.first { it }
                container.contacts.observe()
                    .map { rows -> rows.favoritesInOrder() }
                    .distinctUntilChanged()
                    .collect { favorites ->
                        if (favorites != shortcutFavorites) {
                            FavoriteShortcuts.update(this@MainActivity, favorites)
                            Widgets.refresh(this@MainActivity)
                            shortcutFavorites = favorites
                        }
                    }
            }
        }
    }

    /** Opened again from the recent apps, the shortcut's intent comes back: that must not call. */
    private fun callFavorite(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val contact = intent.getLongExtra(FavoriteShortcuts.EXTRA_CONTACT, -1)
        FavoriteShortcuts.reportUsed(this, contact)
        lifecycleScope.launch {
            runCatching {
                container.contacts.numbers(contact).firstOrNull()?.let { CallManager.place(this@MainActivity, it) }
            }
        }
    }

    private fun holdsDialerRole() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

    private fun answerDefaultQuestion() {
        container.defaultPhoneAsked = true
        askForDefault = false
    }

    private fun askForDialerRole() {
        requestRole.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER))
    }

    private companion object {
        const val EXTRA_DEBUG_SCAN = "debug_scan"
        val REQUIRED_PERMISSIONS = listOfNotNull(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
        ).toTypedArray()
    }
}
