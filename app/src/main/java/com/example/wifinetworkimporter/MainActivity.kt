package com.example.wifinetworkimporter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.wifinetworkimporter.ui.theme.WiFiNetworkImporterTheme

class MainActivity : ComponentActivity() {

    private val wifiNetworkImporter by lazy { WiFiNetworkImporter(this) }
    private var networkSuggestions: List<WifiNetworkSuggestion> = emptyList()
    private var currentSuggestionIndex = 0

    private var selectedFileUri by mutableStateOf<Uri?>(null)
    private var errorLogs by mutableStateOf<List<String>>(emptyList())
    private var showErrorDialog by mutableStateOf(false)

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // Permission granted, you might want to trigger the action that required the permission
        } else {
            // Permission denied, show a toast
            Toast.makeText(this, "Location permission is required to suggest Wi-Fi networks.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WiFiNetworkImporterTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = { pickFileLauncher.launch("text/*") }) {
                            Text(selectedFileUri?.path ?: "Select CSV File")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { checkPermissionsAndImport(selectedFileUri) }) {
                            Text("Import with Network Suggestions")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { checkPermissionsAndAddNetworks(selectedFileUri) }) {
                            Text("Add Networks with Intent (5 at a time)")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showErrorDialog = true }, enabled = errorLogs.isNotEmpty()) {
                            Text("Show Error Logs")
                        }

                        if (showErrorDialog) {
                            AlertDialog(
                                onDismissRequest = { showErrorDialog = false },
                                title = { Text("Import Errors") },
                                text = {
                                    LazyColumn {
                                        items(errorLogs) { error ->
                                            Text(error, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showErrorDialog = false }) {
                                        Text("Close")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndImport(fileUri: Uri?) {
        if (fileUri == null) {
            Toast.makeText(this, "Please select a CSV file first.", Toast.LENGTH_LONG).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    importWifiNetworks(fileUri)
                }
                else -> {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }
        } else {
            Toast.makeText(this, "This feature requires Android 10 or higher.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissionsAndAddNetworks(fileUri: Uri?) {
        if (fileUri == null) {
            Toast.makeText(this, "Please select a CSV file first.", Toast.LENGTH_LONG).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    addNetworksWithIntentInBatches(fileUri)
                }
                else -> {
                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }
        } else {
            Toast.makeText(this, "This feature requires Android 11 or higher.", Toast.LENGTH_LONG).show()
        }
    }

    private fun importWifiNetworks(fileUri: Uri) {
        val result = wifiNetworkImporter.importNetworksFromCSV(fileUri)
        errorLogs = result.errors
        val message = "${result.successCount} networks suggested, ${result.failureCount} failed."
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (result.errors.isNotEmpty()) {
            val errorDetails = result.errors.joinToString("\n")
            Log.e("WiFiImporter", "Import errors:\n$errorDetails")
        }
    }

    private fun addNetworksWithIntentInBatches(fileUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Load suggestions if not already loaded
            if (networkSuggestions.isEmpty()) {
                val (suggestions, errors) = wifiNetworkImporter.getNetworkSuggestionsFromCsv(fileUri)
                networkSuggestions = suggestions
                errorLogs = errors
                if (networkSuggestions.isEmpty()) {
                    val message = if (errors.isNotEmpty()) {
                        "Found ${errors.size} errors. Click 'Show Error Logs' for details."
                    } else {
                        "No networks found in CSV."
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    return
                }
            }

            if (currentSuggestionIndex >= networkSuggestions.size) {
                Toast.makeText(this, "All networks have been processed.", Toast.LENGTH_LONG).show()
                // Reset to start over
                currentSuggestionIndex = 0
                networkSuggestions = emptyList()
                return
            }

            val toIndex = (currentSuggestionIndex + 5).coerceAtMost(networkSuggestions.size)
            val chunk = networkSuggestions.subList(currentSuggestionIndex, toIndex)

            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
            intent.putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, ArrayList(chunk))
            startActivity(intent)

            val networksShown = chunk.size
            currentSuggestionIndex = toIndex
            val remaining = networkSuggestions.size - currentSuggestionIndex
            Toast.makeText(this, "Showing $networksShown networks. $remaining remaining.", Toast.LENGTH_LONG).show()

        } else {
             Toast.makeText(this, "This feature requires Android 11 or higher.", Toast.LENGTH_LONG).show()
        }
    }
}
