package de.hxnk.iptv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.hxnk.iptv.ui.Bg
import de.hxnk.iptv.ui.BrowseScreen
import de.hxnk.iptv.ui.HxTheme
import de.hxnk.iptv.ui.MainViewModel
import de.hxnk.iptv.ui.PlayerScreen
import de.hxnk.iptv.ui.SetupScreen
import de.hxnk.iptv.ui.TextMain

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            HxTheme {
                val vm: MainViewModel = viewModel()
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val configured by vm.configured.collectAsStateWithLifecycle()
    val showSettings by vm.showSettings.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = Bg, contentColor = TextMain) {
        Box(Modifier.fillMaxSize()) {
            if (!configured || showSettings) {
                SetupScreen(vm)
            } else {
                BrowseScreen(vm)
                val p = playing
                if (p != null) PlayerScreen(vm, p)
            }
        }
    }
}
