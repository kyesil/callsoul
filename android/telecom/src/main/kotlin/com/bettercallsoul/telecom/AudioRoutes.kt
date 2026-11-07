package com.bettercallsoul.telecom

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.OutcomeReceiver
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import com.bettercallsoul.diag.DebugLog

enum class AudioRouteType { Earpiece, Speaker, Bluetooth, Wired, Other }

/**
 * A place the call's sound can go, the same on every Android version. [native] is what the version
 * needs to go there: a CallEndpoint from Android 14, before that a route constant or a BluetoothDevice.
 */
internal class AudioRoute(val type: AudioRouteType, val id: String, val name: String, val native: Any?) {
    override fun equals(other: Any?) = other is AudioRoute && other.id == id
    override fun hashCode() = id.hashCode()
}

/** Android 14 and later: Telecom's call endpoints. */
@RequiresApi(34)
internal object EndpointRoutes {
    fun routeOf(endpoint: CallEndpoint): AudioRoute {
        val type = when (endpoint.endpointType) {
            CallEndpoint.TYPE_EARPIECE -> AudioRouteType.Earpiece
            CallEndpoint.TYPE_SPEAKER -> AudioRouteType.Speaker
            CallEndpoint.TYPE_BLUETOOTH -> AudioRouteType.Bluetooth
            CallEndpoint.TYPE_WIRED_HEADSET -> AudioRouteType.Wired
            else -> AudioRouteType.Other
        }
        return AudioRoute(type, endpoint.identifier.toString(), endpoint.endpointName.toString(), endpoint)
    }

    fun request(service: InCallService, route: AudioRoute) {
        val endpoint = route.native as? CallEndpoint ?: return
        service.requestCallEndpointChange(
            endpoint,
            service.mainExecutor,
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) = Unit
                override fun onError(error: CallEndpointException) {
                    DebugLog.e("Audio", "route to ${route.type} failed: code ${error.code}", error)
                }
            },
        )
    }
}

/** Android 10 to 13: the older call audio state. */
internal object LegacyRoutes {
    private const val BLUETOOTH = "bluetooth"

    fun routesOf(state: CallAudioState): List<AudioRoute> = buildList {
        val mask = state.supportedRouteMask
        if (mask and CallAudioState.ROUTE_EARPIECE != 0) add(fixed(AudioRouteType.Earpiece, CallAudioState.ROUTE_EARPIECE))
        if (mask and CallAudioState.ROUTE_SPEAKER != 0) add(fixed(AudioRouteType.Speaker, CallAudioState.ROUTE_SPEAKER))
        if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(fixed(AudioRouteType.Wired, CallAudioState.ROUTE_WIRED_HEADSET))
        if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) {
            val devices = state.supportedBluetoothDevices.orEmpty()
            if (devices.isEmpty()) add(fixed(AudioRouteType.Bluetooth, CallAudioState.ROUTE_BLUETOOTH)) else devices.forEach { add(device(it)) }
        }
    }

    fun currentOf(state: CallAudioState): AudioRoute = when (state.route) {
        CallAudioState.ROUTE_SPEAKER -> fixed(AudioRouteType.Speaker, CallAudioState.ROUTE_SPEAKER)
        CallAudioState.ROUTE_WIRED_HEADSET -> fixed(AudioRouteType.Wired, CallAudioState.ROUTE_WIRED_HEADSET)
        CallAudioState.ROUTE_BLUETOOTH ->
            state.activeBluetoothDevice?.let(::device) ?: fixed(AudioRouteType.Bluetooth, CallAudioState.ROUTE_BLUETOOTH)
        else -> fixed(AudioRouteType.Earpiece, CallAudioState.ROUTE_EARPIECE)
    }

    @Suppress("DEPRECATION")
    fun request(service: InCallService, route: AudioRoute) {
        when (val target = route.native) {
            is BluetoothDevice -> service.requestBluetoothAudio(target)
            is Int -> service.setAudioRoute(target)
        }
    }

    private fun fixed(type: AudioRouteType, route: Int) = AudioRoute(type, "route-$route", type.name, route)

    @SuppressLint("MissingPermission") // Without the permission the name is simply left out.
    private fun device(device: BluetoothDevice): AudioRoute {
        val name = runCatching { device.name }.getOrNull() ?: BLUETOOTH
        return AudioRoute(AudioRouteType.Bluetooth, device.address, name, device)
    }
}
