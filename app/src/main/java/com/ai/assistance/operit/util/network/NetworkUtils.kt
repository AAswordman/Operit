package com.ai.assistance.operit.util.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ai.assistance.operit.R

object NetworkUtils {
    /**
     * 检查设备是否连接到网络
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }
    
    /**
     * 获取网络连接类型
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return context.getString(R.string.not_connected)
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return context.getString(R.string.not_connected)
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> context.getString(R.string.mobile_data)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> context.getString(R.string.ethernet)
            else -> context.getString(R.string.other_network)
        }
    }
} 