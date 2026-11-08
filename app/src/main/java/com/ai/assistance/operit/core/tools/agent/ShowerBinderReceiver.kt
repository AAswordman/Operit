package com.ai.assistance.operit.core.tools.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.shower.IShowerService
import com.ai.assistance.shower.ShowerBinderContainer

class ShowerBinderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOWER_BINDER_READY) {
            return
        }
        val container = intent.getParcelableExtra<ShowerBinderContainer>(EXTRA_BINDER_CONTAINER)
        val binder = container?.binder
        val service = binder?.let { IShowerService.Stub.asInterface(it) }
        val alive = service?.asBinder()?.isBinderAlive == true
        AppLogger.d(TAG, "onReceive: service=$service alive=$alive")
        ShowerBinderRegistry.setService(service)
    }

    companion object {
        private const val TAG = "ShowerBinderReceiver"
        const val ACTION_SHOWER_BINDER_READY = "com.ai.assistance.operit.action.SHOWER_BINDER_READY"
        const val EXTRA_BINDER_CONTAINER = "binder_container"
    }
}
