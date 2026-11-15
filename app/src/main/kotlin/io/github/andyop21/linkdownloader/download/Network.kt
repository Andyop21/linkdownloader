package io.github.andyop21.linkdownloader.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** True when the active network offers internet access (or connectivity cannot be read). */
internal fun Context.isOnline(): Boolean {
    val connectivity = getSystemService(ConnectivityManager::class.java) ?: return true
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
