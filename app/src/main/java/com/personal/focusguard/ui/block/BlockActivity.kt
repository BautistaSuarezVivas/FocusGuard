package com.personal.focusguard.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.focusguard.domain.tracking.BlockReason
import com.personal.focusguard.data.ThemeMode
import com.personal.focusguard.graph
import com.personal.focusguard.ui.ThemedContent
import com.personal.focusguard.util.Format

/**
 * Pantalla de bloqueo. Se abre en su propia tarea encima de la app bloqueada; tanto el botón como
 * "Atrás" llevan a la pantalla de inicio (nunca de vuelta a la app bloqueada).
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = reasonFrom(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        val mode = applicationContext.graph.settings.value?.themeMode ?: ThemeMode.SYSTEM
        setContent {
            ThemedContent(this, mode) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        reason?.let { Format.blockTitle(it) } ?: "Bloqueado por FocusGuard",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        reason?.let { Format.blockMessage(it) } ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { goHome() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Entendido, ir al inicio")
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_TIME = "time"
        private const val EXTRA_SECTION = "section"

        fun intent(context: Context, reason: BlockReason): Intent {
            val i = Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PKG, reason.packageName)
                .putExtra(EXTRA_LABEL, reason.appLabel)
            when (reason) {
                is BlockReason.UsageLimit -> i.putExtra(EXTRA_TYPE, "usage")
                    .putExtra(EXTRA_NUMBER, reason.limitMinutes).putExtra(EXTRA_TIME, reason.resetsAt)
                is BlockReason.OpenLimit -> i.putExtra(EXTRA_TYPE, "opens")
                    .putExtra(EXTRA_NUMBER, reason.maxOpens).putExtra(EXTRA_TIME, reason.nextAvailableAt ?: -1L)
                is BlockReason.ShortForm -> i.putExtra(EXTRA_TYPE, "short").putExtra(EXTRA_SECTION, reason.sectionName)
                is BlockReason.MorningRestricted -> i.putExtra(EXTRA_TYPE, "morning_restricted").putExtra(EXTRA_TIME, reason.endsAt)
                is BlockReason.MorningScreenLimit -> i.putExtra(EXTRA_TYPE, "morning_limit")
                    .putExtra(EXTRA_NUMBER, reason.limitMinutes).putExtra(EXTRA_TIME, reason.endsAt)
            }
            return i
        }

        fun reasonFrom(intent: Intent): BlockReason? {
            val pkg = intent.getStringExtra(EXTRA_PKG) ?: return null
            val label = intent.getStringExtra(EXTRA_LABEL) ?: pkg
            val number = intent.getIntExtra(EXTRA_NUMBER, 0)
            val time = intent.getLongExtra(EXTRA_TIME, -1L)
            return when (intent.getStringExtra(EXTRA_TYPE)) {
                "usage" -> BlockReason.UsageLimit(pkg, label, number, time)
                "opens" -> BlockReason.OpenLimit(pkg, label, number, time.takeIf { it > 0 })
                "short" -> BlockReason.ShortForm(pkg, label, intent.getStringExtra(EXTRA_SECTION) ?: "")
                "morning_restricted" -> BlockReason.MorningRestricted(pkg, label, time)
                "morning_limit" -> BlockReason.MorningScreenLimit(pkg, label, number, time)
                else -> null
            }
        }
    }
}
