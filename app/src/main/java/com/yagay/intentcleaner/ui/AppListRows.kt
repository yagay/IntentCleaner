package com.yagay.intentcleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.Apps
import com.yagay.intentcleaner.domain.ComponentCandidate
import com.yagay.intentcleaner.domain.ComponentRule
    @Composable
    internal fun AppRow(group: AppGroup, selected: Set<com.yagay.intentcleaner.domain.ComponentRule>, expanded: Boolean, onExpand: () -> Unit, onSelect: (Boolean) -> Unit) {
        val selectedCount = group.components.count { it.rule in selected }
        val selectionState = when {
            selectedCount == 0 -> ToggleableState.Off
            selectedCount == group.components.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "折叠" else "展开", onClick = onExpand)
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TriStateCheckbox(
                state = selectionState,
                onClick = { onSelect(selectionState != ToggleableState.On) }
            )
            AppIcon(bitmap = group.appIcon, appLabel = group.appLabel)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    group.appLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "已选 $selectedCount/${group.components.size} · ${group.components.map { it.rule.kind.shortTitle }.distinct().joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onExpand) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "折叠" else "展开")
            }
        }
        HorizontalDivider()
    }

    @Composable
    internal fun ComponentRow(item: ComponentCandidate, checked: Boolean, onToggle: () -> Unit) {

            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = { onToggle() }
                    )
                    .heightIn(min = 48.dp)
                    .padding(start = 24.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ComponentSelectionMark(checked)
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.activityLabel,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.rule.kind.shortTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        item.rule.className,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.unavailable) Text("已配置 · 本次未扫描到", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                    else if (item.advanced) Text("高级候选 · 非普通打开入口保证", style = MaterialTheme.typography.labelSmall)
                    item.evidence.take(3).forEach { reason ->
                        Text(reason, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.evidence.size > 3) Text("另有 ${item.evidence.size - 3} 条匹配依据，见诊断包", style = MaterialTheme.typography.labelSmall)
                }
            }
        }


    @Composable
    private fun ComponentSelectionMark(checked: Boolean) {
        val colors = MaterialTheme.colorScheme
        // The parent row owns the checkbox semantics and the full touch target.
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(20.dp)
                    .background(if (checked) colors.primary else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (checked) colors.primary else colors.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onPrimary)
                }
            }
        }
    }

    @Composable
    internal fun AppIcon(bitmap: Bitmap?, appLabel: String) {
        if (bitmap == null) {
            Icon(Icons.Rounded.Apps, null, Modifier.size(40.dp))
            return
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "$appLabel 图标",
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)),
            contentScale = ContentScale.Fit
        )
    }
