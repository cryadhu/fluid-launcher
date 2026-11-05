package com.example.launcher.fluid

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.launcher.ui.Glyph
import kotlinx.coroutines.delay

/** Shown over the lock screen while an alarm or timer rings. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val key = intent.getStringExtra(ClockAlarms.EXTRA_KEY) ?: return finish()
        val store = ClockStore.get(this)
        setContent {
            val ringing by store.ringing.collectAsStateWithLifecycle()
            val data by store.state.collectAsStateWithLifecycle()
            LaunchedEffect(key in ringing) { if (key !in ringing) finish() }
            LaunchedEffect(Unit) { delay(10 * 60_000L); store.silence(key) }
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
            val timer = key == ClockStore.TIMER_KEY
            val title = if (timer) "Time's up" else data.alarms.find { it.id == ClockStore.alarmId(key) }?.label?.ifBlank { null } ?: "Alarm"
            RingingScreen(clockTime(this, now), title, if (timer) "+1 min" else "Snooze ${SNOOZE_LENGTH / 60_000} min",
                snooze = { store.snooze(key); finish() }, stop = { store.dismiss(key); unlockThenFinish() })
        }
    }

    private fun unlockThenFinish() {
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        finish()
    }
}

@Composable private fun RingingScreen(time: String, title: String, snoozeLabel: String, snooze: () -> Unit, stop: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "Alarm pulse").animateFloat(1f, 1.18f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Pulse")
    val accent = Color(0xFF8051FF)
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF241A45), Color(0xFF120E22)))).systemBarsPadding().padding(28.dp).testTag("alarm-ringing"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).scale(pulse).clip(CircleShape).background(accent.copy(alpha = .18f)))
            Box(Modifier.size(104.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFB99BFF), accent))), contentAlignment = Alignment.Center) {
                Glyph("clock", Color.White, Modifier.size(46.dp))
            }
        }
        Text(time, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light, fontFamily = F.font, modifier = Modifier.padding(top = 36.dp))
        Text(title, color = Color(0xFFD9D1FF), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = F.font, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.weight(1.2f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RingButton(snoozeLabel, Modifier.weight(1f), Color.White.copy(alpha = .12f), snooze)
            RingButton("Stop", Modifier.weight(1f), accent, stop)
        }
    }
}

@Composable private fun RingButton(label: String, modifier: Modifier, color: Color, click: () -> Unit) {
    Box(modifier.height(64.dp).clip(CircleShape).background(color).clickable(role = Role.Button, onClick = click), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = F.font)
    }
}
