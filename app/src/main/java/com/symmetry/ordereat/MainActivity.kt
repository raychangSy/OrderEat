package com.symmetry.ordereat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.symmetry.ordereat.presentation.navigation.OrderEatNavDisplay
import com.symmetry.ordereat.ui.theme.OrderEatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrderEatTheme {
                OrderEatNavDisplay()
            }
        }
    }
}
