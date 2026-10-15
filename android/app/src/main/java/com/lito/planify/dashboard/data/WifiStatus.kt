package com.lito.planify.dashboard.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.io.File
import java.net.Inet4Address

/** Red Wi-Fi actual: nombre, señal en dBm (null si no se sabe) e IP local. */
data class WifiStatus(val ssid: String?, val rssi: Int?, val ip: String?)

/**
 * Lee la conexión Wi-Fi sin permisos de localización (están apagados en el marco).
 * El driver del marco (Realtek 8189fs) no le pasa la señal a Android (RSSI -127) y, sin localización,
 * Android oculta el nombre de la red; los dos se leen entonces de /proc/net/rtl8189fs/wlan0.
 * Devuelve null si no hay Wi-Fi conectado. Hace E/S: llamar fuera del hilo principal.
 */
fun readWifiStatus(context: Context): WifiStatus? {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity.allNetworks.firstOrNull {
        connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } ?: return null
    val ip = connectivity.getLinkProperties(network)?.linkAddresses
        ?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress

    @Suppress("DEPRECATION")
    val info = context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo
    val ssid = info?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
        ?: driverValue("ap_info", "SSID=")
    val rssi = info?.rssi?.takeIf { it in -110..-1 }
        ?: driverValue("rx_signal", "rssi:")?.toIntOrNull()?.takeIf { it in -110..-1 }
    return WifiStatus(ssid, rssi, ip)
}

private fun driverValue(file: String, prefix: String): String? = runCatching {
    File("/proc/net/rtl8189fs/wlan0/$file").readLines().firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
}.getOrNull()?.takeIf { it.isNotEmpty() }

/** WifiManager.UNKNOWN_SSID (la constante pública es de la API 30; el valor es el mismo en Android 10). */
private const val UNKNOWN_SSID = "<unknown ssid>"
