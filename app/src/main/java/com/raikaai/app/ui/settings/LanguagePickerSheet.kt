package com.raikaai.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raikaai.app.core.translate.TranslatePrefs

/** Searchable list of every language for the translate button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    selectedTag: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val all = TranslatePrefs.languages
    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all else all.filter { it.englishName.lowercase().contains(q) || it.nativeName.lowercase().contains(q) || it.tag == q }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Translate to", style = MaterialTheme.typography.titleLarge)
            Text(
                "The translate button on replies and greetings uses the engine chosen in Settings. Only what you see changes; the chat stays in its original language.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search languages") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 8.dp, bottom = 24.dp)) {
            if (query.isBlank()) {
                item(key = TranslatePrefs.PHONE) {
                    LanguageRow(
                        title = "Phone language",
                        subtitle = TranslatePrefs.languageFor(TranslatePrefs.resolvedTag(TranslatePrefs.PHONE)).englishName,
                        selected = selectedTag == TranslatePrefs.PHONE,
                        phone = true,
                    ) { onSelect(TranslatePrefs.PHONE) }
                }
            }
            items(filtered, key = { it.tag }) { lang ->
                LanguageRow(
                    title = lang.nativeName,
                    subtitle = lang.englishName.takeIf { !it.equals(lang.nativeName, ignoreCase = true) },
                    selected = selectedTag == lang.tag,
                ) { onSelect(lang.tag) }
            }
        }
    }
}

@Composable
private fun LanguageRow(title: String, subtitle: String?, selected: Boolean, phone: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (phone) {
            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.padding(end = 12.dp).size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}
