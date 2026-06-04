package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

data class WifiNetworkData(
    val ssid: String,
    val signalStrength: Int
)

data class NetworkData(
    val cellId: String,
    val operatorName: String,
    val lac: String,
    val signalStrength: String,
    val wifiNetworks: List<WifiNetworkData> = emptyList()
)

sealed class LocationState {
    object Idle : LocationState()
    object Loading : LocationState()
    data class Success(val location: Location?) : LocationState()
    data class Error(val message: String) : LocationState()
}

@SuppressLint("MissingPermission")
@Composable
fun LocationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var locationState by remember { mutableStateOf<LocationState>(LocationState.Idle) }
    var address by remember { mutableStateOf<String?>(null) }
    var networkData by remember { mutableStateOf<NetworkData?>(null) }
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val telephonyManager = remember { context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager }

    var isWifiEnabled by remember { mutableStateOf(true) }
    var isCellTowerEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    var cellId = "N/A"
                    var lac = "N/A"
                    var signalStrength = "N/A"
                    var operatorName = "Unknown"
                    
                    if (isCellTowerEnabled) {
                        val cellInfoList = try { telephonyManager.allCellInfo } catch (e: SecurityException) { null }
                        if (!cellInfoList.isNullOrEmpty()) {
                            val cellInfo = cellInfoList[0]
                            when (cellInfo) {
                                is android.telephony.CellInfoGsm -> {
                                    val identity = cellInfo.cellIdentity
                                    val signal = cellInfo.cellSignalStrength
                                    cellId = identity.cid.toString()
                                    lac = "0x" + identity.lac.toString(16).uppercase()
                                    signalStrength = "${signal.dbm} dBm"
                                }
                                is android.telephony.CellInfoLte -> {
                                    val identity = cellInfo.cellIdentity
                                    val signal = cellInfo.cellSignalStrength
                                    cellId = identity.ci.toString()
                                    lac = "0x" + identity.tac.toString(16).uppercase()
                                    signalStrength = "${signal.dbm} dBm"
                                }
                                is android.telephony.CellInfoWcdma -> {
                                    val identity = cellInfo.cellIdentity
                                    val signal = cellInfo.cellSignalStrength
                                    cellId = identity.cid.toString()
                                    lac = "0x" + identity.lac.toString(16).uppercase()
                                    signalStrength = "${signal.dbm} dBm"
                                }
                            }
                            operatorName = telephonyManager.networkOperatorName.takeIf { !it.isNullOrBlank() } ?: "Unknown"
                        }
                    }
                    
                    var wifiNetworks = emptyList<WifiNetworkData>()
                    if (isWifiEnabled) {
                        try {
                            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                            val scanResults = wifiManager.scanResults
                            wifiNetworks = scanResults.mapNotNull {
                                val ssid = it.SSID ?: ""
                                if (ssid.isBlank()) null else WifiNetworkData(ssid, it.level)
                            }.distinctBy { it.ssid }.sortedByDescending { it.signalStrength }.take(5)
                        } catch (e: Exception) {
                            // Ignore wifi scan errors
                        }
                    }
                    
                    networkData = NetworkData(cellId, operatorName, lac, signalStrength, wifiNetworks)
                }
            }
            kotlinx.coroutines.delay(1000) // Poll every 1 second
        }
    }

    fun getLocation() {
        locationState = LocationState.Loading

        fun processLocation(location: Location?) {
            if (location != null) {
                locationState = LocationState.Success(location)
                coroutineScope.launch {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = withContext(Dispatchers.IO) {
                        try {
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (!addresses.isNullOrEmpty()) {
                        address = addresses[0].getAddressLine(0)
                    } else {
                        address = "Address not found."
                    }
                }
            } else {
                if (networkData != null) {
                    locationState = LocationState.Success(null)
                } else {
                    locationState = LocationState.Error("No cellular data found. (Note: Android OS requires the main 'Location' toggle to be ON just to scan cell towers).")
                }
            }
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                processLocation(location)
            } else {
                // Fallback to LocationManager NETWORK_PROVIDER last known
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                try {
                    @Suppress("MissingPermission")
                    val loc = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    if (loc != null) {
                        processLocation(loc)
                    } else {
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_LOW_POWER,
                            CancellationTokenSource().token
                        ).addOnSuccessListener { newLoc -> 
                            processLocation(newLoc) 
                        }.addOnFailureListener {
                            processLocation(null)
                        }
                    }
                } catch (e: Exception) {
                    processLocation(null)
                }
            }
        }.addOnFailureListener { e ->
            if (networkData != null) {
                locationState = LocationState.Success(null)
            } else {
                locationState = LocationState.Error("LBS scan failed: ${e.message}")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                getLocation()
            } else {
                locationState = LocationState.Error("Permission denied.")
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location Icon",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = "LBS Locator",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Network Triangulation (No GPS)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Section (Radar / Info)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.3f))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Background Pattern Simulation
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Transparent)
                )

                when (val state = locationState) {
                    is LocationState.Success -> {
                        if (state.location != null) {
                            MapViewWithRadius(
                                latitude = state.location.latitude,
                                longitude = state.location.longitude,
                                accuracy = state.location.accuracy
                            )
                            // Accuracy Badge overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.8f))
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "ACCURACY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "± ${state.location.accuracy.toInt()} m",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            // Fallback purely network radar no exact match
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Cell Tower Only",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            when (state) {
                                is LocationState.Idle, is LocationState.Error -> {
                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = "Idle",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }
                                }
                                is LocationState.Loading -> {
                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(48.dp).testTag("loading_indicator"),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 4.dp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Fetching location...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            // Control Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Wi-Fi",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Switch(
                        checked = isWifiEnabled,
                        onCheckedChange = { isWifiEnabled = it },
                        modifier = Modifier.testTag("wifi_switch")
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Cellular",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Switch(
                        checked = isCellTowerEnabled,
                        onCheckedChange = { isCellTowerEnabled = it },
                        modifier = Modifier.testTag("cellular_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Section (Controls & Results)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.7f))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(32.dp))
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val state = locationState) {
                        is LocationState.Idle -> {
                            Text(
                                text = "Find your location using Network (Wi-Fi/Cellular) without relying strictly on GPS.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    val isPermissionGranted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    
                                    if (isPermissionGranted) {
                                        getLocation()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("get_location_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Get My Location", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        is LocationState.Loading -> {
                            Text(
                                text = "Waiting for network triangulation...",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is LocationState.Success -> {
                            if (networkData != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "CELL ID",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            networkData?.cellId ?: "N/A",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "OPERATOR",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            networkData?.operatorName ?: "N/A",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "LAC",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            networkData?.lac ?: "N/A",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "STRENGTH",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            networkData?.signalStrength ?: "N/A",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                if (networkData?.wifiNetworks?.isNotEmpty() == true) {
                                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)).padding(16.dp)) {
                                        Text(
                                            "DETECTED WI-FI NETWORKS (RSSI)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        networkData?.wifiNetworks?.forEach { wifi ->
                                            val normalizedStrength = ((wifi.signalStrength + 100) / 70f).coerceIn(0f, 1f)
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = wifi.ssid,
                                                    modifier = Modifier.weight(0.35f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .weight(0.45f)
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(normalizedStrength)
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${wifi.signalStrength} dBm",
                                                    modifier = Modifier.weight(0.2f),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    textAlign = TextAlign.End,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "LATITUDE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        state.location?.latitude?.let { "%.4f".format(it) } ?: "N/A",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "LONGITUDE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        state.location?.longitude?.let { "%.4f".format(it) } ?: "N/A",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "ADDRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    address ?: if (state.location == null) "Address unavailable" else "Looking up address...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            if (state.location == null) {
                                val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager }
                                val isLocationEnabled = remember(locationState) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                        locationManager.isLocationEnabled
                                    } else {
                                        @Suppress("DEPRECATION")
                                        android.provider.Settings.Secure.getInt(context.contentResolver, android.provider.Settings.Secure.LOCATION_MODE, 0) != 0
                                    }
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        if (isLocationEnabled) "MISSING API KEY" else "MASTER LOCATION SWITCH OFF",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        if (isLocationEnabled) "We retrieved the cellular LBS data, but calculating exact Lat/Lon coordinates from Cell IDs requires a 3rd-party database API (like Google Geolocation API)." else "To translate raw Cell IDs into exact Lat/Lon map coordinates, the Android OS Location switch must be turned ON. Without an external database, the phone cannot map these signals to coordinates offline.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (state.location != null) {
                                    Button(
                                        onClick = {
                                            val uri = android.net.Uri.parse("geo:${state.location.latitude},${state.location.longitude}?q=${state.location.latitude},${state.location.longitude}(Your+Location)")
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "No map application installed.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(56.dp).testTag("show_map_button"),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "Map",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Map", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        locationState = LocationState.Idle
                                        address = null
                                        networkData = null
                                        
                                        val isPermissionGranted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        
                                        if (isPermissionGranted) {
                                            getLocation()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp).testTag("refresh_button"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Refresh", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        is LocationState.Error -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { locationState = LocationState.Idle },
                                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("try_again_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Try Again", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapViewWithRadius(latitude: Double, longitude: Double, accuracy: Float) {
    val htmlData = remember(latitude, longitude, accuracy) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body { padding: 0; margin: 0; background-color: #E8F0FE; } 
                #map { height: 100vh; width: 100vw; position: absolute; }
                .leaflet-control-attribution { display: none; }
                .leaflet-control-zoom { display: none; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var lat = $latitude;
                var lng = $longitude;
                var acc = $accuracy;
                var zoom = 16;
                if (acc > 500) zoom = 15;
                if (acc > 1000) zoom = 14;
                if (acc > 3000) zoom = 13;
                if (acc > 5000) zoom = 12;
                
                var map = L.map('map', { zoomControl: false }).setView([lat, lng], zoom);
                L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                    maxZoom: 19
                }).addTo(map);
                
                var circle = L.circle([lat, lng], {
                    color: '#2563EB',
                    fillColor: '#3B82F6',
                    fillOpacity: 0.25,
                    radius: acc,
                    weight: 2,
                    dashArray: '5, 5'
                }).addTo(map);
                
                var marker = L.circleMarker([lat, lng], {
                    radius: 8,
                    fillColor: "#2563EB",
                    color: "#ffffff",
                    weight: 3,
                    opacity: 1,
                    fillOpacity: 1
                }).addTo(map);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://example.com", htmlData, "text/html", "UTF-8", null)
            }
        },
        update = { },
        modifier = Modifier.fillMaxSize()
    )
}


