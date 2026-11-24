package com.moneysmart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneysmart.app.data.local.entity.CategoryEntity
import com.moneysmart.app.data.local.entity.CategoryType
import com.moneysmart.app.ui.components.IconCatalog
import com.moneysmart.app.ui.components.ResponsiveContentBox
import com.moneysmart.app.ui.components.SoftElevatedCard
import com.moneysmart.app.ui.components.SquircleIconBadge
import com.moneysmart.app.ui.components.TopBarAddButton
import com.moneysmart.app.ui.components.WarmCurvedHeroHeader
import com.moneysmart.app.ui.components.WarmSegmentedPillBar
import com.moneysmart.app.ui.theme.CoralAccent
import com.moneysmart.app.ui.theme.PeachSoft
import com.moneysmart.app.ui.viewmodel.MoneySmartViewModel

@Composable
fun CategoriesScreen(
    viewModel: MoneySmartViewModel,
    onNavigateBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(CategoryType.EXPENSE) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var presetParentId by remember { mutableStateOf<Long?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val filtered = remember(categories, selectedType) {
        categories.filter { it.type == selectedType }
    }
    val parents = remember(filtered) { filtered.filter { it.parentId == null } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WarmCurvedHeroHeader(
            title = "Categories",
            subtitle = "Organize spending & income labels",
            heroColor = PeachSoft,
            maxWidth = 900.dp,
            onNavigateBack = onNavigateBack,
            actions = {
                TopBarAddButton(
                    label = "Add",
                    onClick = {
                        editingCategory = null
                        presetParentId = null
                        showDialog = true
                    }
                )
            }
        )

        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            maxWidth = 900.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                WarmSegmentedPillBar(
                    options = CategoryType.entries.map { it.displayName },
                    selectedIndex = selectedType.ordinal,
                    onSelect = { selectedType = CategoryType.entries[it] },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(parents, key = { it.id }) { parent ->
                        val children = filtered.filter { it.parentId == parent.id }
                        val accent = Color(parent.colorHex)
                        SoftElevatedCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editingCategory = parent
                                            presetParentId = null
                                            showDialog = true
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SquircleIconBadge(
                                        icon = IconCatalog.resolve(parent.iconName),
                                        tint = accent,
                                        containerColor = accent.copy(alpha = 0.14f),
                                        size = 38.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = parent.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            editingCategory = null
                                            presetParentId = parent.id
                                            showDialog = true
                                        }
                                    ) {
                                        Text("+ Sub", color = CoralAccent, fontWeight = FontWeight.Bold)
                                    }
                                }

                                children.forEach { child ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                editingCategory = child
                                                presetParentId = child.parentId
                                                showDialog = true
                                            }
                                            .padding(start = 52.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "↳ ${child.name}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        CategoryEditorDialog(
            initial = editingCategory,
            type = selectedType,
            parentId = editingCategory?.parentId ?: presetParentId,
            onDismiss = { showDialog = false },
            onSave = { cat ->
                viewModel.saveCategory(cat)
                showDialog = false
            },
            onDelete = { id ->
                viewModel.deleteCategory(id)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    initial: CategoryEntity?,
    type: CategoryType,
    parentId: Long?,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var iconName by remember { mutableStateOf(initial?.iconName ?: "Category") }
    var colorHex by remember { mutableLongStateOf(initial?.colorHex ?: IconCatalog.presetColorHexes.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial != null -> "Edit Category"
                    parentId != null -> "Add Subcategory"
                    else -> "Add ${type.displayName} Category"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconCatalog.presetColorHexes.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (colorHex == hex) 2.5.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconCatalog.availableIcons.take(15).forEach { (iName, iconVec) ->
                        val selected = iconName == iName
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color(colorHex).copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { iconName = iName },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVec,
                                contentDescription = iName,
                                tint = if (selected) Color(colorHex) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        CategoryEntity(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            type = initial?.type ?: type,
                            parentId = parentId,
                            iconName = iconName,
                            colorHex = colorHex,
                            sortOrder = initial?.sortOrder ?: 100
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { onDelete(initial.id) }
                    ) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
