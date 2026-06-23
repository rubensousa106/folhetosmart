package com.folhetosmart.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Campos "Distrito" e "Cidade" como dropdowns dependentes (campos fechados): ao
 * escolher o distrito, a cidade passa a listar os concelhos desse distrito
 * ([CONCELHOS_POR_DISTRITO]). Partilhado pelo registo (Passo 2) e pelo perfil.
 *
 * O chamador deve, ao mudar o distrito, limpar a cidade (ela deixa de pertencer
 * ao novo distrito) — ver `onDistritoChange`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistritoCidadeFields(
    distrito: String?,
    cidade: String,
    onDistritoChange: (String) -> Unit,
    onCidadeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var distExpanded by remember { mutableStateOf(false) }
    var cidExpanded by remember { mutableStateOf(false) }
    val cidades = distrito?.let { CONCELHOS_POR_DISTRITO[it] }.orEmpty()

    Column(modifier) {
        // Distrito
        ExposedDropdownMenuBox(
            expanded = distExpanded,
            onExpandedChange = { distExpanded = it }
        ) {
            OutlinedTextField(
                value = distrito ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Distrito") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(distExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = distExpanded,
                onDismissRequest = { distExpanded = false }
            ) {
                DISTRITOS_PT.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onDistritoChange(option); distExpanded = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Cidade (concelhos do distrito escolhido)
        ExposedDropdownMenuBox(
            expanded = cidExpanded,
            onExpandedChange = { if (cidades.isNotEmpty()) cidExpanded = it }
        ) {
            OutlinedTextField(
                value = cidade,
                onValueChange = {},
                readOnly = true,
                enabled = cidades.isNotEmpty(),
                label = { Text("Cidade") },
                placeholder = {
                    Text(if (distrito == null) "Escolhe primeiro o distrito" else "Escolhe a cidade")
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cidExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = cidExpanded,
                onDismissRequest = { cidExpanded = false }
            ) {
                cidades.forEach { nome ->
                    DropdownMenuItem(
                        text = { Text(nome) },
                        onClick = { onCidadeChange(nome); cidExpanded = false }
                    )
                }
            }
        }
    }
}
