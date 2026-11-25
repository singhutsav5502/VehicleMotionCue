package dev.cutlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// OLED-friendly true-black dark theme: lower power on AMOLED screens.
val Accent = Color(0xFF7CE0A3)
val Warn = Color(0xFFFFB86B)
val Over = Color(0xFFFF6B6B)
val Muted = Color(0xFF8A958E)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00210F),
    secondary = Color(0xFF9CCBB0),
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF151A17),
    surfaceContainer = Color(0xFF0E1210),
    surfaceContainerHigh = Color(0xFF151A17),
    surfaceContainerHighest = Color(0xFF1C221E),
    onSurface = Color(0xFFE6ECE8),
    onSurfaceVariant = Color(0xFFB4BEB8),
    outline = Color(0xFF3A443E),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = StoreHolder.get(applicationContext)
        setContent {
            MaterialTheme(colorScheme = Colors) { AppRoot(store) }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Today", Icons.Filled.Home),
    Tab("Week", Icons.Filled.DateRange),
    Tab("Weight", Icons.Filled.Person),
    Tab("Claude", Icons.Filled.Face),
    Tab("Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot(store: Store) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val chat = remember { ChatState() }
    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B0E0C)) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> TodayScreen(store)
                1 -> WeekScreen(store)
                2 -> WeightScreen(store)
                3 -> ClaudeScreen(store, chat)
                else -> SettingsScreen(store)
            }
        }
    }
}
