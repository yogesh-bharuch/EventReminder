package com.example.eventreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReminderManagerScreen(
    onBack: () -> Unit,
    onOpenDebug: () -> Unit          // NEW PARAM
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Reminder Manager")

        Spacer(Modifier.height(20.dp))

        // Existing button behavior remains the same
        Button(onClick = onBack) {
            Text("Development in progress")
        }

        Spacer(Modifier.height(20.dp))

        // NEW — Navigate to Scheduling Debug UI
        Button(onClick = onOpenDebug) {
            Text("Open Scheduling Debug")
        }
    }
}




/*

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReminderManagerScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Reminder Manager")
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack) {
            Text("Development in progress")
        }
    }
}
*/
