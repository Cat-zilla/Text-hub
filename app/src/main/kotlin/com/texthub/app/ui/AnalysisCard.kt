package com.texthub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.texthub.app.R
import com.texthub.app.ui.components.SecondaryAction
import com.texthub.app.ui.components.SectionCard
import com.texthub.app.ui.components.SectionTitle
import com.texthub.app.ui.theme.HubCorners
import com.texthub.app.ui.theme.Spacing
import com.texthub.app.ui.theme.LocalUiSettings
import androidx.compose.ui.text.font.FontFamily
import com.texthub.app.ui.theme.mutedTextColor
import com.texthub.core.ToolRegistry
import com.texthub.core.detector.Candidate
import com.texthub.core.detector.Confidence
import com.texthub.core.detector.Diagnosis
import com.texthub.core.detector.SecretKind

/**
 * The card the Universal Decoder shows above its result: what was recognised, at what confidence,
 * why, which layers were unwrapped, what else could fit, and which secret (if any) is still needed.
 *
 * It repeats nothing the tool did not actually find. A format that is only guessed is listed as a
 * candidate and never decoded, a digest is never presented as something that can be opened, and an
 * encrypted payload is described as "needs a password", never as "decrypted".
 *
 * The override is a single row that opens the ordinary tool picker ("Detected automatically: Base64
 * ▾"), and "Analyse result again" appears only once there is a result to analyse. Nothing on this
 * card is stored: it is derived from the input that is in memory.
 */
@Composable
fun AnalysisCard(
    diagnosis: Diagnosis,
    forcedToolId: String?,
    canAnalyseAgain: Boolean,
    onOverride: () -> Unit,
    onClearOverride: () -> Unit,
    onUseCandidate: (String) -> Unit,
    onAnalyseAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(
                text = stringResource(R.string.analysis_title),
                modifier = Modifier.weight(1f),
            )
            diagnosis.confidence()?.let { confidence ->
                Badge(text = stringResource(confidence.labelRes()), colour = confidence.colour())
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        val headline = diagnosis.headline()
        Text(
            text = headline.text,
            style = MaterialTheme.typography.bodyLarge,
        )
        headline.detail?.let { detail ->
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = mutedTextColor,
            )
        }

        // ------------------------------------------------------------ the chain of layers
        if (diagnosis.steps.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            SectionTitle(text = stringResource(R.string.analysis_chain))
            diagnosis.steps.forEachIndexed { index, step ->
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.labelMedium,
                        color = mutedTextColor,
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = step.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = step.note ?: ToolRegistry.metaOf(step.toolId).name,
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedTextColor,
                        )
                    }
                }
            }
        }

        // ------------------------------------------------------------- other candidates
        // Tapping one is the same override the row below offers, so the user can act on what was
        // found instead of retyping it into a picker.
        val others = diagnosis.otherCandidates()
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            SectionTitle(text = stringResource(R.string.analysis_candidates))
            others.forEach { candidate ->
                Spacer(Modifier.height(Spacing.xs))
                CandidateRow(candidate = candidate, onClick = { onUseCandidate(candidate.toolId) })
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // ----------------------------------------------------------------- the override
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HubCorners.row)
                .clickable { onOverride() }
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = forcedToolId?.let { id -> ToolRegistry.metaOf(id).name }
            Text(
                text = when {
                    label != null -> stringResource(R.string.analysis_forced, label)
                    diagnosis.detectedLabel() != null ->
                        stringResource(R.string.analysis_detected_automatically, diagnosis.detectedLabel()!!)
                    else -> stringResource(R.string.analysis_detected_nothing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (forcedToolId != null) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.analysis_clear_override),
                    tint = mutedTextColor,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onClearOverride() },
                )
                Spacer(Modifier.size(Spacing.xs))
            }
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = stringResource(R.string.cd_analysis_override),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        if (canAnalyseAgain) {
            Spacer(Modifier.height(Spacing.sm))
            SecondaryAction(
                text = stringResource(R.string.analysis_analyse_again),
                onClick = onAnalyseAgain,
                icon = {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}

/** One candidate that fits: the format, why it fits, and how sure the detection is. */
@Composable
private fun CandidateRow(candidate: Candidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HubCorners.row)
            .clickable { onClick() }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = candidate.reason,
                style = MaterialTheme.typography.labelSmall,
                color = mutedTextColor,
            )
            if (LocalUiSettings.current.showDetectionDetails) {
                // Advanced > Show detection details: what the detector already decided, stated
                // plainly - tool id, confidence level, whether it can run here and which parameters
                // it read out of the payload (names only; never the payload or a secret). The
                // detection itself is unchanged.
                val details = buildString {
                    append(candidate.toolId)
                    append(" \u00b7 ").append(candidate.confidence.name.lowercase())
                    append(" \u00b7 ").append(candidate.direction.name.lowercase())
                    if (!candidate.actionable) append(" \u00b7 ").append(stringResource(R.string.analysis_detail_not_runnable))
                    candidate.hashAlgorithm?.let { append(" \u00b7 ").append(it) }
                    if (candidate.suggestedParams.isNotEmpty()) {
                        append(" \u00b7 ").append(stringResource(R.string.analysis_detail_params, candidate.suggestedParams.keys.sorted().joinToString(", ")))
                    }
                }
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = mutedTextColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Badge(
            text = stringResource(candidate.confidence.labelRes()),
            colour = candidate.confidence.colour(),
        )
    }
}

/** A small rounded label: the confidence of a detection. */
@Composable
private fun Badge(text: String, colour: Color) {
    Box(
        modifier = Modifier
            .clip(HubCorners.chip)
            .background(colour.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = colour,
        )
    }
}

/** The one-line summary of a diagnosis, already resolved from string resources. */
private data class Headline(val text: String, val detail: String? = null)

@Composable
private fun Diagnosis.headline(): Headline = when (this) {
    is Diagnosis.Decoded -> {
        // `lastOrNull` rather than `last`: a decoded result always carries at least one step (the
        // core tests pin that), and the UI must not be the place where a broken invariant crashes.
        val layer = steps.lastOrNull()
        Headline(
            text = layer?.let {
                stringResource(R.string.analysis_result_decoded, ToolRegistry.metaOf(it.toolId).name)
            } ?: stringResource(R.string.analysis_result_decoded_plain),
            detail = layer?.note,
        )
    }
    is Diagnosis.NeedsSecret -> Headline(
        text = stringResource(R.string.analysis_result_needs_secret, stringResource(candidate.secretLabelRes())),
        detail = candidate.reason,
    )
    is Diagnosis.Choose -> Headline(stringResource(R.string.analysis_result_choose), note)
    is Diagnosis.HashOnly -> Headline(stringResource(R.string.analysis_result_hash), note)
    is Diagnosis.NotSupported -> Headline(stringResource(R.string.analysis_result_not_supported), note)
    is Diagnosis.Failed -> Headline(stringResource(R.string.analysis_result_failed), message)
    is Diagnosis.Unrecognized -> Headline(stringResource(R.string.analysis_result_unrecognized), note)
}

/** What to ask for: the payload's own header decides, never a guess. */
private fun Candidate.secretLabelRes(): Int = when (secretKind) {
    SecretKind.PASSWORD -> R.string.analysis_secret_password
    SecretKind.KEY -> R.string.analysis_secret_key
    SecretKind.PRIVATE_KEY -> R.string.analysis_secret_private_key
    SecretKind.PUBLIC_KEY -> R.string.analysis_secret_public_key
    null -> R.string.analysis_secret_any
}

/** The label of what was recognised, for the override row. */
private fun Diagnosis.detectedLabel(): String? = when (this) {
    is Diagnosis.Decoded -> steps.lastOrNull()?.label
    is Diagnosis.NeedsSecret -> candidate.label
    is Diagnosis.Failed -> candidate.label
    is Diagnosis.Choose -> candidates.firstOrNull()?.label
    is Diagnosis.HashOnly -> candidates.firstOrNull()?.label
    is Diagnosis.NotSupported -> candidates.firstOrNull()?.label
    is Diagnosis.Unrecognized -> null
}

/** The other formats that fit; never the one already in use. */
private fun Diagnosis.otherCandidates(): List<Candidate> = when (this) {
    is Diagnosis.Choose -> candidates
    is Diagnosis.HashOnly -> candidates.drop(1)
    is Diagnosis.NotSupported -> candidates.drop(1)
    is Diagnosis.Unrecognized -> candidates
    is Diagnosis.Decoded -> emptyList()
    is Diagnosis.NeedsSecret -> emptyList()
    is Diagnosis.Failed -> emptyList()
}

/** The confidence of what was found, if anything was. */
private fun Diagnosis.confidence(): Confidence? = when (this) {
    is Diagnosis.Decoded -> steps.lastOrNull()?.confidence
    is Diagnosis.NeedsSecret -> candidate.confidence
    is Diagnosis.Failed -> candidate.confidence
    is Diagnosis.Choose -> candidates.firstOrNull()?.confidence
    is Diagnosis.HashOnly -> candidates.firstOrNull()?.confidence
    is Diagnosis.NotSupported -> candidates.firstOrNull()?.confidence
    is Diagnosis.Unrecognized -> null
}

/** Confidence wording: only structural evidence is ever "high". */
private fun Confidence.labelRes(): Int = when (this) {
    Confidence.HIGH -> R.string.analysis_confidence_high
    Confidence.LIKELY -> R.string.analysis_confidence_likely
    Confidence.POSSIBLE -> R.string.analysis_confidence_possible
}

/** Colours for the three confidence levels, legible on the AMOLED background. */
private fun Confidence.colour(): Color = when (this) {
    Confidence.HIGH -> Color(0xFF57C79A)
    Confidence.LIKELY -> Color(0xFFE0B054)
    Confidence.POSSIBLE -> Color(0xFF8C9BB5)
}
