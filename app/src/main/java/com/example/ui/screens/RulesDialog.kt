package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.ui.components.DuoCard
import com.example.ui.components.DuoGradientButton
import com.example.ui.theme.LocalDuoPalette

@Composable
fun RulesDialog(onDismiss: () -> Unit) {
    val palette = LocalDuoPalette.current
    val rules = remember {
        listOf(
            "Enter with a 1." to "Every token starts on the edge row and needs a 1 to step onto its start tile.",
            "Extra turn" to "earned for rolling a 6, capturing an opponent, or bringing a token home.",
            "Capture" to "by landing on a lone opponent token. Stars are safe tiles, and two tokens on one tile protect each other.",
            "Boost tiles" to "push your token three extra steps forward along the track.",
            "Gate:" to "after completing a lap, your token turns into its own home lane. You need the exact number to finish.",
            "Classic vs Drama Dice:" to "Classic Dice uses exact 1/6 uniform randomness (default). Drama Dice uses bounded adaptive weights symmetrically for both players — inspect live probabilities in Dice Insight."
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x661E243B))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        DuoCard(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 480.dp)
                .heightIn(max = 560.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            contentPadding = PaddingValues(24.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "How to play",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.ink
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rules.forEachIndexed { idx, (lead, body) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (idx % 2 == 0) palette.players[0].soft else palette.players[1].soft,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (idx % 2 == 0) palette.players[0].deep else palette.players[1].deep
                                    )
                                }
                            }
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.ink)) {
                                        append(lead)
                                        append(" ")
                                    }
                                    withStyle(SpanStyle(color = palette.muted)) {
                                        append(body)
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DuoGradientButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("rules_close_button")
                    ) {
                        Text("Got it")
                    }
                }
            }
        }
    }
}
