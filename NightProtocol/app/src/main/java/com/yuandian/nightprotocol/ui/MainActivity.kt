package com.yuandian.nightprotocol.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuandian.nightprotocol.admin.DeviceOwnerState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = DeviceOwnerState.read(this)
        setContent {
            Column(Modifier.padding(24.dp)) {
                Text("Device Owner: ${s.isDeviceOwner}")
                Text("Admin active: ${s.isAdminActive}")
            }
        }
    }
}
