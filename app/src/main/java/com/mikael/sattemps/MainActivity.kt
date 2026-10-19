package com.mikael.sattemps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Ecran unique de l'application : demande les permissions necessaires puis affiche
 * [MainScreen]. Tant que la localisation precise est refusee, seule l'explication
 * est visible car aucune mesure n'est possible.
 */
class MainActivity : ComponentActivity() {

    private var viewModel: MainViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel?.onPermissionsGranted()
        }
        granted = hasLocationPermission()
        permanentlyDenied = !granted &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private var granted by mutableStateOf(false)
    private var permanentlyDenied by mutableStateOf(false)

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
                        PermissionRequest(
                            permanentlyDenied = permanentlyDenied,
                            onRequest = { permissionLauncher.launch(REQUIRED_PERMISSIONS) },
                            onOpenSettings = ::openAppSettings
                        )
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

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}

@androidx.compose.runtime.Composable
private fun PermissionRequest(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.permission_rationale), textAlign = TextAlign.Center)
        if (permanentlyDenied) {
            Text(stringResource(R.string.permission_denied_twice), textAlign = TextAlign.Center)
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_open_settings))
            }
        } else {
            Button(onClick = onRequest) { Text(stringResource(R.string.permission_allow)) }
        }
    }
}
