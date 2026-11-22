package com.titradeck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.titradeck.app.ui.StartupAnimation
import com.titradeck.app.ui.TitraDeckApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deleteSharedPreferences("pubchem_compounds")
        enableEdgeToEdge()
        setContent {
            StartupAnimation(showOnLaunch = savedInstanceState == null) { TitraDeckApp() }
        }
    }
}
