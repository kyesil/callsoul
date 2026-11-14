package com.bettercallsoul

import com.bettercallsoul.AppLook
import android.content.Context
import com.bettercallsoul.ui.Route
import com.bettercallsoul.widget.Widgets
import com.bettercallsoul.widget.WidgetActionActivity
import android.Manifest
import android.app.role.RoleManager
import android.content.ContentResolver
import com.bettercallsoul.diag.DebugLog
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
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
import com.bettercallsoul.ui.Suggestion
import com.bettercallsoul.ui.SetupStep
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

/** Survives a theme change, not the process: whether this activity's launch intent was acted on. */
class LaunchState : ViewModel() {
    var intentHandled = false
}

class MainActivity : ComponentActivity() {
    private var permissionsGranted by mutableStateOf(false)
    private var isDefaultDialer by mutableStateOf(false)
    private var askForDefault by mutableStateOf(false)

    /** The welcome screen's step now, or none once the app is set up; see [refreshSetup]. */
    private var setupStep by mutableStateOf<SetupStep?>(SetupStep.Role)

    /** A permission gone missing after the setup, for the home screen's banner; see [refreshSuggestion]. */
    private var suggestion by mutableStateOf<Suggestion?>(null)

    /** A banner or the default-app question sent the user to a page: look again once back from it. */
    private var recheckSuggestion = false

    /** The role was asked for on the setup screen and not given. */
    private var roleDeclined by mutableStateOf(false)
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
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refresh()
            if (!permissionsGranted) {
                // On the setup screen: with the role, straight on to the permissions.
                if (isDefaultDialer) requestPermissions.launch(REQUIRED_PERMISSIONS) else roleDeclined = true
            }
        }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLook.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLook.matchFormats(this)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Recreated for a theme change, the activity gets the same intent again: not a new request. The
        // flag lives in a ViewModel, which a theme change keeps; after the process was killed it starts
        // over, and the intent that brought the activity back (a notification, say) is then a new one.
        // Brought back from the recent apps it carries its old intent, which was already acted on.
        val launch: LaunchState by viewModels()
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (!launch.intentHandled && !(savedInstanceState != null && fromHistory)) handleDial(intent)
        launch.intentHandled = true
        followFavorites()
        // Known before the first frame, so the question doesn't flash up for an app that already has the role.
        isDefaultDialer = holdsDialerRole()
        askForDefault = !container.defaultPhoneAsked
        // Known before the first frame too, so the welcome doesn't flash up for an app already set up.
        permissionsGranted = REQUIRED_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        container.noteSetupStart(alreadySetUp = permissionsGranted)
        refreshSetup()
        refreshSuggestion()
        setContent {
            BcsTheme {
                val step = setupStep
                if (step == null) {
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
                        suggestion = suggestion,
                        onSuggestion = ::acceptSuggestion,
                        onCloseSuggestion = ::closeSuggestion,
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
                    SetupScreen(
                        step = step,
                        roleDeclined = roleDeclined,
                        onRequestRole = ::askForDialerRole,
                        onOpenDefaultApps = ::openDefaultApps,
                        onRequestPermissions = { requestPermissions.launch(REQUIRED_PERMISSIONS) },
                        onAccept = ::acceptStep,
                        onSkip = ::skipStep,
                    )
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
        // The link sent to bring up the system's question: it has done its job by arriving.
        if (intent.getBooleanExtra(TelLinks.EXTRA_SETUP, false)) {
            DebugLog.i("TelLinks", "picked: ${TelLinks.holder(this)}")
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW, ContactsContract.QuickContact.ACTION_QUICK_CONTACT, ACTION_QUICK_CONTACT_OLD ->
                if (intent.data?.scheme == ContentResolver.SCHEME_CONTENT) openContact(intent.data!!)
                else dialRequest = intent.data?.schemeSpecificPart
            Intent.ACTION_DIAL -> dialRequest = intent.data?.schemeSpecificPart
            FavoriteShortcuts.ACTION_CALL -> callFavorite(intent)
            // Opened again from the recent apps, the shortcut's intent comes back: that isn't a new contact.
            FavoriteShortcuts.ACTION_ADD_CONTACT ->
                if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) openRequest = Route.NewContact(null)
            WidgetActionActivity.ACTION_OPEN -> {
                val contact = intent.getLongExtra(WidgetActionActivity.EXTRA_CONTACT, -1)
                val number = intent.getStringExtra(WidgetActionActivity.EXTRA_NUMBER).orEmpty()
                openRequest = if (contact >= 0) Route.Contact(contact) else number.takeIf(String::isNotBlank)?.let(Route::Number)
            }
            ACTION_NEW_CONTACT ->
                if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
                    openRequest = Route.NewContact(intent.getStringExtra(EXTRA_NUMBER))
                }
            MissedCallReceiver.ACTION_OPEN_MISSED -> {
                // One person's notification counts only their calls as seen; the summary, all of them.
                val number = intent.getStringExtra(MissedCallReceiver.EXTRA_NUMBER).orEmpty()
                if (number.isEmpty()) MissedCallReceiver.clear(this) else MissedCallReceiver.markSeen(this, number)
                missedRequest = number
            }
        }
    }

    private fun refresh() {
        permissionsGranted = REQUIRED_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        isDefaultDialer = holdsDialerRole()
        // The welcome screen follows each step as it is done. The home screen's banners are looked at
        // only when the app opens, and once more on coming back from a page one of them opened.
        if (setupStep != null) {
            refreshSetup()
            if (setupStep == null) refreshSuggestion()
        }
        if (recheckSuggestion) {
            recheckSuggestion = false
            refreshSuggestion()
        }
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

    /**
     * A contact sent from elsewhere, such as the launcher's search: a lookup, contact or raw contact
     * URI, opened on the person's page. Nothing happens for one that can't be found.
     */
    private fun openContact(uri: Uri) {
        DebugLog.i("Open", "contact from ${referrer?.host ?: "?"}: ${uri.pathSegments.firstOrNull()}")
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) { runCatching { contactIdOf(uri) }.getOrNull() }
            if (id != null) openRequest = Route.Contact(id)
        }
    }

    private fun contactIdOf(uri: Uri): Long? {
        val raw = uri.pathSegments.firstOrNull() == "raw_contacts" || uri.authority == "contacts"
        val target = if (raw) {
            val rawId = uri.lastPathSegment?.toLongOrNull() ?: return null
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId)
        } else {
            ContactsContract.Contacts.lookupContact(contentResolver, uri) ?: return null
        }
        val column = if (raw) ContactsContract.RawContacts.CONTACT_ID else ContactsContract.Contacts._ID
        return contentResolver.query(target, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
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

    /**
     * The welcome screen's step, in [SetupStep]'s order: the role, the permissions, then each of the
     * rest only while it is still needed and wasn't skipped. Back from a step's settings page with it
     * done, the next one comes; once none is left the setup is over for good.
     */
    private fun refreshSetup() {
        setupStep = when {
            !permissionsGranted -> if (isDefaultDialer) SetupStep.Permissions else SetupStep.Role
            container.setupDone || !isDefaultDialer -> null
            !container.lockCallsAsked && !LockScreenCalls.allowed(this) -> SetupStep.LockCalls
            !container.whatsAppAccessAsked && !WhatsAppCallListener.hasAccess(this) &&
                WhatsAppCallListener.whatsAppInstalled(this) -> SetupStep.WhatsApp
            !container.telLinksAsked && TelLinks.holder(this) != TelLinks.Holder.Us -> SetupStep.TelLinks
            else -> {
                container.setupDone = true
                null
            }
        }
    }

    private fun acceptStep(step: SetupStep) {
        when (step) {
            SetupStep.Role, SetupStep.Permissions -> {}
            SetupStep.LockCalls -> LockScreenCalls.open(this)
            SetupStep.WhatsApp -> {
                container.saveWhatsAppCalls(true)
                WhatsAppCallListener.openAccess(this)
            }
            SetupStep.TelLinks -> TelLinks.choose(this)
        }
    }

    /** Skipped, a step's feature counts as not wanted, so no banner asks for it later either. */
    private fun skipStep(step: SetupStep) {
        when (step) {
            SetupStep.Role, SetupStep.Permissions -> {}
            SetupStep.LockCalls -> container.lockCallsAsked = true
            SetupStep.WhatsApp -> {
                container.whatsAppAccessAsked = true
                container.saveWhatsAppCalls(false)
            }
            SetupStep.TelLinks -> container.telLinksAsked = true
        }
        refresh()
    }

    /**
     * After the setup: a permission gone while its feature is still wanted, in [Suggestion]'s order.
     * Calls on the lock screen are wanted unless skipped; WhatsApp calls while Settings' switch is on;
     * links to numbers once they have opened here.
     */
    private fun refreshSuggestion() {
        // Once allowed, however it came (the setup, Settings, the phone itself), losing it is a problem.
        if (container.lockCallsAsked && LockScreenCalls.allowed(this) && setupStep == null) container.lockCallsAsked = false
        // Links count as wanted once they open here; losing that afterwards (a new app that opens
        // links resets the choice) is a problem too.
        val links = TelLinks.holder(this)
        if (links == TelLinks.Holder.Us && !container.telLinksWanted) container.telLinksWanted = true
        suggestion = when {
            setupStep != null || !isDefaultDialer -> null
            !container.lockCallsAsked && !LockScreenCalls.allowed(this) -> Suggestion.LockCalls
            container.whatsAppCallsEnabled && !WhatsAppCallListener.hasAccess(this) &&
                WhatsAppCallListener.whatsAppInstalled(this) -> Suggestion.WhatsApp
            links != TelLinks.Holder.Us && container.telLinksWanted -> Suggestion.TelLinks
            else -> null
        }
    }

    private fun acceptSuggestion(step: Suggestion) {
        recheckSuggestion = true
        when (step) {
            Suggestion.LockCalls -> LockScreenCalls.open(this)
            Suggestion.WhatsApp -> WhatsAppCallListener.openAccess(this)
            Suggestion.TelLinks -> TelLinks.choose(this)
        }
    }

    /** The cross gives the feature up; Settings turns it back on. */
    private fun closeSuggestion(step: Suggestion) {
        when (step) {
            Suggestion.LockCalls -> container.lockCallsAsked = true
            Suggestion.WhatsApp -> container.saveWhatsAppCalls(false)
            Suggestion.TelLinks -> container.telLinksWanted = false
        }
        refreshSuggestion()
    }

    private fun openDefaultApps() {
        runCatching { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    }

    private fun answerDefaultQuestion() {
        container.defaultPhoneAsked = true
        askForDefault = false
    }

    private fun askForDialerRole() {
        recheckSuggestion = true
        requestRole.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER))
    }

    companion object {
        /** From the screen after a call: the new-contact page with [EXTRA_NUMBER] filled in. */
        const val ACTION_NEW_CONTACT = "com.bettercallsoul.action.NEW_CONTACT"
        const val EXTRA_NUMBER = "number"
        private const val EXTRA_DEBUG_SCAN = "debug_scan"

        /** The QuickContact action older apps and launchers still send. */
        private const val ACTION_QUICK_CONTACT_OLD = "com.android.contacts.action.QUICK_CONTACT"
        private val REQUIRED_PERMISSIONS = listOfNotNull(
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
