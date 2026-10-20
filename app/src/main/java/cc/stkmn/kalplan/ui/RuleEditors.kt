package cc.stkmn.kalplan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.stkmn.kalplan.data.*

@Composable
fun SelectMenu(value: String, choices: List<String>, label: @Composable (String) -> String = { it }, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label(value)) }
        DropdownMenu(open, { open = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(label(choice)) }, onClick = { open = false; onSelect(choice) }) }
        }
    }
}

fun editableLabelGroups(rule: LabelPolicy?): List<LabelConditionGroup> {
    if (rule == null) return emptyList()
    if (rule.conditionGroups.isNotEmpty()) return rule.conditionGroups
    val sender = rule.senderContains.takeIf { it.isNotBlank() }?.let { LabelCondition("SENDER", it) }
    val terms = rule.keywords.filter { it.isNotBlank() }.map { LabelCondition(
        if (rule.searchIn == "SUBJECT_AND_BODY") "SUBJECT_OR_BODY" else rule.searchIn, it) }
    return when {
        terms.isEmpty() -> sender?.let { listOf(LabelConditionGroup(listOf(it))) }.orEmpty()
        rule.keywordMode == "ALL" -> listOf(LabelConditionGroup(listOfNotNull(sender) + terms))
        else -> terms.map { LabelConditionGroup(listOfNotNull(sender) + it) }
    }
}

@Composable
fun LabelGroupsEditor(groups: List<LabelConditionGroup>, onChange: (List<LabelConditionGroup>) -> Unit) {
    Text(tr("Eine passende Gruppe genügt (ODER). Innerhalb einer Gruppe müssen alle Bedingungen passen (UND).", "Any group may match (OR). All conditions inside a group must match (AND)."), style = MaterialTheme.typography.bodySmall)
    groups.forEachIndexed { gi, group ->
        if (gi > 0) Text(tr("ODER", "OR"), style = MaterialTheme.typography.labelLarge)
        OutlinedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.conditions.forEachIndexed { ci, condition ->
                    if (ci > 0) Text(tr("UND", "AND"), style = MaterialTheme.typography.labelMedium)
                    fun change(c: LabelCondition) = onChange(groups.mapIndexed { i, g -> if (i != gi) g else g.copy(conditions = g.conditions.mapIndexed { j, old -> if (j == ci) c else old }) })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SelectMenu(condition.field, listOf("SENDER", "SUBJECT", "BODY", "SUBJECT_OR_BODY"), label = { field -> when (field) {
                            "SENDER" -> tr("Absender", "Sender"); "SUBJECT" -> tr("Betreff", "Subject"); "BODY" -> tr("Mailtext", "Body"); else -> tr("Betreff oder Mailtext", "Subject or body")
                        } }) { change(condition.copy(field = it)) }
                        SelectMenu(condition.operator, listOf("CONTAINS", "EQUALS", "NOT_CONTAINS"), label = { when(it) {
                            "EQUALS" -> tr("ist genau", "equals"); "NOT_CONTAINS" -> tr("enthält nicht", "does not contain"); else -> tr("enthält", "contains")
                        } }) { change(condition.copy(operator = it)) }
                    }
                    StableTextField(condition.text, { change(condition.copy(text = it.take(320))) }, label = { Text(tr("Suchtext", "Match text")) }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        val next = group.conditions.filterIndexed { i, _ -> i != ci }
                        onChange(if (next.isEmpty()) groups.filterIndexed { i, _ -> i != gi } else groups.mapIndexed { i, g -> if (i == gi) g.copy(conditions = next) else g })
                    }) { Text(tr("Bedingung entfernen", "Remove condition")) }
                }
                TextButton(enabled = group.conditions.size < 20, onClick = { onChange(groups.mapIndexed { i, g -> if (i == gi) g.copy(conditions = g.conditions + LabelCondition(text = "")) else g }) }) { Text(tr("UND-Bedingung", "AND condition")) }
            }
        }
    }
    OutlinedButton(enabled = groups.size < 20, onClick = { onChange(groups + LabelConditionGroup(listOf(LabelCondition(text = "")))) }) { Text(tr("ODER-Gruppe hinzufügen", "Add OR group")) }
    if (groups.isEmpty()) Text(tr("Ohne Bedingungen wird dieses Label nur manuell vergeben.", "Without conditions this label is manual only."))
}

@Composable
fun PresetMultiChoice(selected: List<String>, presets: List<FlatFeePreset>, onSelect: (List<String>) -> Unit) {
    Text(tr("Pauschalen kombinieren", "Combine flat fees"), style = MaterialTheme.typography.titleSmall)
    if (presets.isEmpty()) Text(tr("Lege zuerst eine Pauschale in den Einstellungen an.", "Create a flat fee in settings first."))
    presets.forEach { preset ->
        ToggleRow(preset.name + " · " + when(preset.appliesTo) { "WORK" -> tr("Arbeitszeit", "Work"); "TRAVEL" -> tr("Fahrtzeit", "Travel"); else -> tr("Auftrag", "Order") }, preset.id in selected) {
            onSelect(if (it) (selected + preset.id).distinct() else selected - preset.id)
        }
    }
}
