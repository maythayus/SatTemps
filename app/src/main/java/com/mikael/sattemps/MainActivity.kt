package com.mikael.sattemps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikael.sattemps.ui.MainScreen
import com.mikael.sattemps.ui.SatTempsTheme

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.READ_PHONE_STATE
)

class MainActivity : ComponentActivity() {

    private var viewModel: MainViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel?.onPermissionsGranted()
        }
        granted = hasLocationPermission()
    }

    private var granted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        granted = hasLocationPermission()

        setContent {
            SatTempsTheme {
                val model: MainViewModel = viewModel()
                viewModel = model
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (granted) {
                        model.onPermissionsGranted()
                        MainScreen(model)
                    } else {
                        PermissionRequest { permissionLauncher.launch(REQUIRED_PERMISSIONS) }
                    }
                }
            }
        }

        if (!granted) permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    override fun onResume() {
        super.onResume()
        viewModel?.start()
    }

    override fun onPause() {
        viewModel?.stop()
        super.onPause()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}

@androidx.compose.runtime.Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "SatTemps a besoin de la localisation precise (GPS) et de l'etat du telephone " +
                "pour lire les satellites, l'heure GNSS et les cellules 5G / LTE."
        )
        Button(onClick = onRequest) { Text("Autoriser") }
    }
}
