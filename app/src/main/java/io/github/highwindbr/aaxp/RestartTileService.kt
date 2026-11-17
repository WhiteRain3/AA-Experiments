package io.github.highwindbr.aaxp

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import android.widget.Toast
import io.github.highwindbr.aaxp.service.ServiceBridge

/** Quick settings tile that asks Android Auto to restart, the same as the button in the app. */
class RestartTileService : TileService() {
    override fun onClick() {
        if (send()) return done(true)
        // The app may have just been started for this tap; the Xposed service connects a moment later.
        Handler(Looper.getMainLooper()).postDelayed({ done(send()) }, 1_500L)
    }

    private fun send(): Boolean = runCatching { ServiceBridge.sendRestartRequest() }.getOrDefault(false)

    private fun done(sent: Boolean) {
        Toast.makeText(applicationContext,
            if (sent) "Asked Android Auto to restart" else "AA Experiments: LSPosed is not connected",
            Toast.LENGTH_SHORT).show()
    }
}
