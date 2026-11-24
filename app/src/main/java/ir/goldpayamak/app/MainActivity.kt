package ir.goldpayamak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.goldpayamak.app.ui.AuthScreen
import ir.goldpayamak.app.ui.MainShell

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF805F10),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFF6EACD),
                    onPrimaryContainer = Color(0xFF4A3409),
                    secondary = Color(0xFF645738),
                    secondaryContainer = Color(0xFFEFE5CF),
                    onSecondaryContainer = Color(0xFF3B2F16),
                    background = Color(0xFFFAF8F2),
                    onBackground = Color(0xFF29251E),
                    surface = Color(0xFFFAF8F2),
                    onSurface = Color(0xFF29251E),
                    surfaceVariant = Color(0xFFF1EBDD),
                    onSurfaceVariant = Color(0xFF514A3D),
                    surfaceContainer = Color(0xFFF3EEE3),
                    surfaceContainerHighest = Color(0xFFF1EBDD),
                    outline = Color(0xFF847964),
                    outlineVariant = Color(0xFFE0D7C5),
                ),
                shapes = Shapes(
                    extraSmall = RoundedCornerShape(10.dp),
                    small = RoundedCornerShape(12.dp),
                    medium = RoundedCornerShape(16.dp),
                    large = RoundedCornerShape(20.dp),
                ),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        if (!vm.isActivated) AuthScreen(vm) else MainShell(vm)
                        vm.errorMsg?.let { m ->
                            AlertDialog(
                                onDismissRequest = { vm.errorMsg = null },
                                title = { Text("خطا") },
                                text = { Text(m) },
                                confirmButton = { TextButton(onClick = { vm.errorMsg = null }) { Text("باشه") } },
                            )
                        }
                    }
                }
            }
        }
    }
}
