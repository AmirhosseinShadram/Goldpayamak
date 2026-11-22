package ir.goldpayamak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
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
            MaterialTheme(colorScheme = lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF8A6A00),
                secondary = androidx.compose.ui.graphics.Color(0xFF5C5C00),
            )) {
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
