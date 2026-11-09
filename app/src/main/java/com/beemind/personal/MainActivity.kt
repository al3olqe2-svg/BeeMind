package com.beemind.personal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.beemind.personal.ui.BeeMindApp
import com.beemind.personal.ui.theme.BeeMindTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BeeMindTheme { BeeMindApp() } }
    }
}
