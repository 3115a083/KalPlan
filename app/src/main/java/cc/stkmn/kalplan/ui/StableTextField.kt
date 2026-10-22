package cc.stkmn.kalplan.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/** Editing state is synchronous and retains selection/IME composition. Persist only outside this control. */
@Composable
fun StableTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null, singleLine: Boolean = false,
    minLines: Int = 1, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    placeholder: @Composable (() -> Unit)? = null, suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null, leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null, enabled: Boolean = true, isError: Boolean = false
) {
    var edit by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Owners use local drafts. An external replacement preserves the existing selection, never resets to zero.
    SideEffect {
        if (value != edit.text) edit = edit.copy(text = value,
            selection = TextRange(edit.selection.start.coerceIn(0, value.length), edit.selection.end.coerceIn(0, value.length)), composition = null)
    }
    OutlinedTextField(edit, { next -> edit = next; onValueChange(next.text) }, modifier = modifier,
        label = label, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
        keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
        placeholder = placeholder, suffix = suffix, supportingText = supportingText,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, enabled = enabled, isError = isError)
}
