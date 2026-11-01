package com.calendarietto.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calendarietto.app.data.Mascot

@Composable
fun OnboardingScreen(
    name: String,
    mascot: Mascot,
    perms: PermStatus,
    onName: (String) -> Unit,
    onMascot: (Mascot) -> Unit,
    onRequestRuntime: () -> Unit,
    onDone: () -> Unit,
) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BouncyMascot(mascot, Modifier.size(150.dp))
        Spacer(Modifier.height(8.dp))
        Text("Ciao! Sono Calendarietto 🎀", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Leggo il vostro Google Calendar condiviso, vi sveglio prima degli impegni e li segno quando me li dite a voce.",
            color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )

        Column(Modifier.fillMaxWidth()) {
            SectionTitle("Come ti chiami?")
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                placeholder = { Text("Il tuo nome") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("Scegli la tua mascotte")
            MascotPicker(mascot, onMascot)
            SectionTitle("Qualche permesso")
            PermissionsCard(perms, onRequestRuntime)
        }

        Spacer(Modifier.height(20.dp))
        PillButton(
            if (perms.calendar) "Iniziamo! ✨" else "Prima attiva il Calendario 📅",
            Modifier.fillMaxWidth(),
            filled = true,
            enabled = perms.calendar,
            onClick = onDone,
        )
        Spacer(Modifier.height(30.dp))
    }
}
