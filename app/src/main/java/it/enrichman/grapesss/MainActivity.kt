package it.enrichman.grapesss

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import it.enrichman.grapesss.ui.theme.GrapesssTheme

const val TAG = "MyActivity"
const val REQUEST_ENABLE_BT = 1


private var scanning = false
private val handler = Handler(Looper.getMainLooper())

// Stops scanning after 10 seconds.
private const val SCAN_PERIOD: Long = 10000


class MainActivity : ComponentActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GrapesssTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Greeting(
                            name = "Android",
                            modifier = Modifier.padding(innerPadding)
                        )

                        val devices = remember { mutableStateListOf<BluetoothDeviceInfo>() }
                        val ctx = LocalContext.current

                        BluetoothScanButton(
                            onClick = { onBluetoothScanBtnClick(ctx, devices) },
                            modifier = Modifier.padding(innerPadding)
                        )

                        BluetoothDeviceList(devices = devices)
                    }
                }
            }
        }

        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter ?: return

        // Check to see if the Bluetooth classic feature is available.
        val bluetoothAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        Log.i(TAG, "bluetooth Available $bluetoothAvailable");

        // Check to see if the BLE feature is available.
        val bluetoothLEAvailable =
            packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        Log.i(TAG, "bluetooth LE Available $bluetoothLEAvailable");

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }


        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )

        PermissionUtilities.checkPermissions(this, permissions, 123)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, permissions, 123)
        } else {
            // Permission already granted, proceed with creating the notification
        }


    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "onRequestPermissionsResult $permissions")
    }

    // onRequestPermissionsResult(int, String[], int[]) method.
}

fun onBluetoothScanBtnClick(context: Context, devices: MutableList<BluetoothDeviceInfo>) {
    val permissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    )

    println("Bluetooth scan started...")

    if (!PermissionUtilities.checkPermissionsGranted(context, permissions)) {
        return
    }

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

        if (!seenMacAddresses.contains(macAddress)) {
            devices.add(BluetoothDeviceInfo(deviceName, macAddress))
            seenMacAddresses.add(macAddress)
        }
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

