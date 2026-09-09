package com.allen.pokemon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.allen.pokemon.ui.navigation.PokemonNavHost
import com.allen.pokemon.ui.theme.PokemonTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var hingeBounds by mutableStateOf<android.graphics.Rect?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collectLatest { info ->
                hingeBounds = (info.displayFeatures.filterIsInstance<FoldingFeature>()
                    .firstOrNull { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL })?.bounds
            }
        }
        setContent {
            PokemonTheme(darkTheme = false, dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                    ) {
                        PokemonNavHost(hingeBounds = hingeBounds)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PokemonTheme {
        PokemonNavHost()
    }
}
