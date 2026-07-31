package com.avgangsplaneraren.app.ui.planner

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value.toString())) }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            onValueChange(newValue.text.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    fieldValue = fieldValue.copy(
                        selection = TextRange(0, fieldValue.text.length)
                    )
                }
            }
    )
}
