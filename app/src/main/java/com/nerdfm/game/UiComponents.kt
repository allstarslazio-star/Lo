package com.nerdfm.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Guarantees the Material minimum touch target without changing how small content is drawn inside it. */
fun Modifier.minTouch(): Modifier = this.heightIn(min = Accessibility.MIN_TOUCH_DP.dp)

/** Gives a composable a single spoken description (TalkBack) that replaces its visual children. */
fun Modifier.describe(text: String): Modifier = this.semantics(mergeDescendants = true) { contentDescription = text }

/** A clickable, labelled button-like row: >= 48 dp tall, announced as a button. */
fun Modifier.tappable(label: String, onClick: () -> Unit): Modifier =
    this.minTouch().semantics { role = Role.Button }.clickable(onClickLabel = label, onClick = onClick)

/** Shown instead of a blank list. The optional action lets the user recover in one tap (e.g. "Clear filters"). */
@Composable
fun EmptyState(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(16.dp).describe(message),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        T(message, 13, C.muted, lines = 3)
        if (actionLabel != null && onAction != null) Btn(actionLabel, modifier = Modifier.minTouch()) { onAction() }
    }
}

/** A recoverable problem, surfaced in the UI instead of crashing or silently discarding the user's data. */
@Composable
fun ErrorState(title: String, detail: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(C.card).padding(12.dp).describe("$title. $detail"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        T(title, 14, C.red, true)
        T(detail, 12, C.text, lines = 4)
        if (actionLabel != null && onAction != null) Btn(actionLabel, modifier = Modifier.minTouch()) { onAction() }
    }
}

@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(12.dp).describe(label), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp))
        T(label, 13, C.muted)
    }
}

/** A selectable chip with a 48 dp target and a state-aware description ("GK, selected"). */
@Composable
fun ToggleChip(label: String, selected: Boolean, description: String = label, onToggle: () -> Unit) {
    Box(
        Modifier.minTouch().clip(RoundedCornerShape(2.dp)).background(if (selected) C.accent else C.card)
            .semantics { role = Role.Checkbox; contentDescription = description + if (selected) ", selected" else ", not selected" }
            .clickable(onClickLabel = "Toggle $label", onClick = onToggle).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) { T(label, 12, if (selected) Color.Black else C.text, selected) }
}

/** A labelled segmented control for filters. */
@Composable
fun LabeledSeg(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(end = 10.dp)) {
        T(label, 10, C.muted)
        Seg(options, selected, onSelect, Modifier.describe("$label filter, ${options.getOrElse(selected) { "" }} selected"))
    }
}

/** Severity colour for notices. */
fun noticeColor(level: NoticeLevel): Color = when (level) {
    NoticeLevel.URGENT -> C.red
    NoticeLevel.ATTENTION -> C.amber
    NoticeLevel.INFO -> C.muted
}
