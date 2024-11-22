package it.enrichman.grapesss

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import it.enrichman.grapesss.ui.theme.GrapesssTheme
import org.apache.commons.io.IOUtils
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL


const val TAG = "MyActivity"
const val REQUEST_ENABLE_BT = 1


private var scanning = false
private val handler = Handler(Looper.getMainLooper())

// Stops scanning after 10 seconds.
private const val SCAN_PERIOD: Long = 10000


class MainActivity : ComponentActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    private val devices = mutableStateListOf<BluetoothDeviceInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GrapesssTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Button(onClick = { onConfigureBtnClick(this@MainActivity) }) {
                        Text("Configure")
                    }

                    BluetoothScanButton(
                        onClick = { onBluetoothScanBtnClick(this@MainActivity, devices) },
                    )

                    BluetoothDeviceList(devices = devices)
                }

            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "onRequestPermissionsResult $permissions")

        if (requestCode == 122) {
            configure(this)
        } else if (requestCode == 123) {
            bluetoothScan(this, devices)
        }
    }
}

fun onConfigureBtnClick(activity: Activity) {
    // check permissions
    val permissions = arrayOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.CHANGE_NETWORK_STATE,
    )

    if (PermissionUtilities.checkPermissionsGranted(activity, permissions)) {
        configure(activity)
    } else {
        PermissionUtilities.checkPermissions(activity, permissions, 122)
    }
}

fun configure(activity: Activity) {
    Log.i(TAG, "configuring")

    // https://developer.android.com/develop/connectivity/wifi/wifi-bootstrap
    val specifier = WifiNetworkSpecifier.Builder()
        .setSsidPattern(PatternMatcher("ESP", PatternMatcher.PATTERN_PREFIX))
        .setWpa2Passphrase("password")
        // 64:e8:33 EspressIF - 64:e8:33:8a:d5:aa
        //.setBssidPattern(MacAddress.fromString("64:e8:33:00:00:00"), MacAddress.fromString("ff:ff:ff:00:00:00"))
        .build()

    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .setNetworkSpecifier(specifier)
        .build()

    val connectivityManager =
        activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    connectivityManager.requestNetwork(request, networkCallback)

    // Release the request when done.
    // connectivityManager.unregisterNetworkCallback(networkCallback)
}

val networkCallback = object : ConnectivityManager.NetworkCallback() {

    override fun onAvailable(network: Network) {
        // do success processing here..
        Log.i(TAG, "onAvailable: $network")

        val url = URL("http://192.168.4.1/api/sysinfo")
        val urlConnection = network.openConnection(url) as HttpURLConnection
        try {
            val inputStream: InputStream = BufferedInputStream(urlConnection.inputStream)
            val total: String = IOUtils.toString(inputStream)
            Log.i(TAG, "response: $total")

        } finally {
            urlConnection.disconnect()
        }
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        println(networkCapabilities.toString())
    }


    override fun onUnavailable() {
        // do failure processing here..
        Log.e(TAG, "error on unavailable")
    }


}


fun onBluetoothScanBtnClick(activity: Activity, devices: MutableList<BluetoothDeviceInfo>) {
    val permissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    )

    if (!PermissionUtilities.checkPermissionsGranted(activity, permissions)) {
        PermissionUtilities.checkPermissions(activity, permissions, 123)
        return
    }

    bluetoothScan(activity, devices)
}


fun bluetoothScan(context: Context, devices: MutableList<BluetoothDeviceInfo>) {
    val bluetoothManager: BluetoothManager = context.getSystemService(BluetoothManager::class.java)
    val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter ?: return
    val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

    devices.clear()
    val scanCallback = BluetoothScanCallback(devices)

    try {
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            Log.i(TAG, "start scanning")

            handler.postDelayed({
                scanning = false
                Log.i(TAG, "stopped scanning")
                bluetoothLeScanner.stopScan(scanCallback)
            }, SCAN_PERIOD)

            scanning = true
            bluetoothLeScanner.startScan(scanCallback)
        } else {
            scanning = false
            Log.i(TAG, "stopped scanning 2")
            // bluetoothLeScanner.stopScan(leScanCallback)
        }
    } catch (e: SecurityException) {
        println("SecurityException should not happen")
    }
}

data class BluetoothDeviceInfo(val name: String, val macAddress: String)

@Composable
fun BluetoothDeviceList(devices: List<BluetoothDeviceInfo>) {
    LazyColumn {
        items(devices.size) { i ->
            BluetoothDeviceItem(devices[i])
        }
    }
}

@Composable
fun BluetoothDeviceItem(device: BluetoothDeviceInfo) {
    Card(modifier = Modifier.padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Device Name: ${device.name}")
            Text(text = "MAC Address: ${device.macAddress}")
        }
    }
}

class BluetoothScanCallback(
    private val devices: MutableList<BluetoothDeviceInfo>
) : ScanCallback() {

    private val seenMacAddresses = mutableSetOf<String>()

    // This method is called for each found device during the scan
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        var deviceName = "<unknown>"
        try {
            deviceName = result.device.name ?: "<unknown>"
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot get name from device")
        }
        val macAddress = result.device.address

        if (!macAddress.startsWith("64:e8:33", ignoreCase = true)) {
            return
        }

        if (seenMacAddresses.contains(macAddress)) {
            return
        }

        devices.add(BluetoothDeviceInfo(deviceName, macAddress))
        seenMacAddresses.add(macAddress)
    }

    // Optionally, you can implement this method if you want to handle batch scan results
    override fun onBatchScanResults(results: List<ScanResult>) {
        super.onBatchScanResults(results)

        for (result in results) {
            var deviceName = "<unknown>"
            try {
                deviceName = result.device.name ?: "<unknown>"
            } catch (e: SecurityException) {
                Log.w(TAG, "cannot get name from device")
            }
            val macAddress = result.device.address

            if (!seenMacAddresses.contains(macAddress)) {
                devices.add(BluetoothDeviceInfo(deviceName, macAddress))
                seenMacAddresses.add(macAddress)
            }
        }
    }

    // This method is called when the scan is completed
    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        Log.e(TAG, "onScanFailed error: $errorCode")
    }
}


@Composable
fun BluetoothScanButton(onClick: (Context) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    Button(
        onClick = { onClick(ctx) },
        modifier = modifier
    ) {
        Text("Scan")
    }
}

@Preview(showBackground = true)
@Composable
fun BluetoothScanButtonPreview() {
    BluetoothScanButton(onClick = {})
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Surface() {
        Text(
            text = "Hello, my name is $name!",
            modifier = modifier.padding(24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GrapesssTheme {
        Greeting("Enrico")
    }
}

