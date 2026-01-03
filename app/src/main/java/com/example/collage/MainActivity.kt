package com.example.collage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.collage.ui.theme.AutoCollageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoCollageTheme {
                val vm: CollageViewModel = viewModel()
                LaunchApp(vm)
            }
        }
    }
}
