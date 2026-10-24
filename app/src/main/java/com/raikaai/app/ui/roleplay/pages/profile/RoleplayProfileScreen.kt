package com.raikaai.app.ui.roleplay.pages.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun RoleplayProfileScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { RoleplayProfileStore(context.applicationContext) }
    val saved = remember { store.load() }
    var name by rememberSaveable { mutableStateOf(saved.name) }
    var description by rememberSaveable { mutableStateOf(saved.description) }
    var gender by rememberSaveable { mutableStateOf(saved.gender) }
    var genderExpanded by remember { mutableStateOf(false) }

    fun save() = store.save(RoleplayProfile(name, description, gender))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = "Character will remember your profile",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; save() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
        )
        androidx.compose.foundation.layout.Box {
            OutlinedButton(
                onClick = { genderExpanded = true },
                modifier = Modifier,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("Gender: $gender") }
            DropdownMenu(
                expanded = genderExpanded,
                onDismissRequest = { genderExpanded = false },
            ) {
                listOf("male", "female").forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            gender = option
                            genderExpanded = false
                            save()
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = description,
            onValueChange = { description = it; save() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("About you") },
            minLines = 5,
        )
    }
}
