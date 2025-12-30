package com.example.wifinetworkimporter

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.opencsv.CSVReader
import java.io.InputStreamReader

/**
 * Result of WiFi network import process
 * @param successCount Number of networks successfully suggested
 * @param failureCount Number of networks that failed to be suggested
 * @param errors A list of error messages for failed imports
 */
data class ImportResult(
    val successCount: Int,
    val failureCount: Int,
    val errors: List<String>
)

class WiFiNetworkImporter(private val context: Context) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Import WiFi networks from a CSV file and suggest them to the system.
     * @param csvUri URI of the CSV file
     * @return ImportResult with details of the import process
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun importNetworksFromCSV(csvUri: Uri): ImportResult {
        val (suggestionList, errors) = getNetworkSuggestionsFromCsv(csvUri)
        val failureCount = errors.size

        if (suggestionList.isNotEmpty()) {
            val status = wifiManager.addNetworkSuggestions(suggestionList)
            if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Log.e("WiFiImporter", "Failed to add network suggestions. Status: $status")
                return ImportResult(0, suggestionList.size + failureCount, errors + "Failed to add network suggestions")
            }
        }

        return ImportResult(
            suggestionList.size,
            failureCount,
            errors
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getNetworkSuggestionsFromCsv(csvUri: Uri): Pair<List<WifiNetworkSuggestion>, List<String>> {
        val suggestions = mutableListOf<WifiNetworkSuggestion>()
        val errors = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(csvUri)?.use { inputStream ->
                CSVReader(InputStreamReader(inputStream)).use { csvReader ->
                    // Skip header row
                    csvReader.readNext()

                    var record: Array<String>?
                    var rowNum = 2 // Start from 2 because of header
                    while (csvReader.readNext().also { record = it } != null) {
                        if (record?.size == 3) {
                            val ssid = record!![0].trim()
                            val password = record!![1].trim()
                            val security = record!![2].trim().uppercase()

                            val result = createWifiNetworkSuggestion(ssid, password, security)
                            if (result.first != null) {
                                suggestions.add(result.first!!)
                            } else {
                                errors.add("Row $rowNum: ${result.second}")
                            }
                        } else {
                            errors.add("Row $rowNum: Incorrect number of columns")
                        }
                        rowNum++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WiFiImporter", "Critical error during network import", e)
            errors.add("Critical error: ${e.localizedMessage}")
        }
        return Pair(suggestions, errors)
    }

    /**
     * Create a single WiFi network suggestion.
     * @return A Pair containing the WifiNetworkSuggestion or null, and an error message.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createWifiNetworkSuggestion(ssid: String, password: String, securityType: String): Pair<WifiNetworkSuggestion?, String> {
        Log.d("WiFiImporter", "Processing network: SSID='$ssid', Security='$securityType'")
        if (ssid.isEmpty()) {
            return Pair(null, "Skipping network with empty SSID")
        }

        return when (securityType) {
            "WPA", "WPA2", "WPA-PSK", "WPA_WPA2_PERSONAL", "WPA_PERSONAL" -> {
                if (password.isEmpty()) {
                    return Pair(null, "Skipping WPA/WPA2 network '$ssid' with empty password")
                }
                Pair(WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build(), "")
            }
            "WPA3", "WPA3_PERSONAL" -> {
                if (password.isEmpty()) {
                    return Pair(null, "Skipping WPA3 network '$ssid' with empty password")
                }
                Pair(WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setWpa3Passphrase(password)
                    .build(), "")
            }
            "OPEN", "NONE", "UNKNOWN" -> Pair(WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .build(), "")
            else -> Pair(null, "Unsupported security type: '$securityType' for network '$ssid'")
        }
    }
}
