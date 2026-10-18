package com.nerdfm.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------------------------------------
// Flags
// ---------------------------------------------------------------------------------------------------------------------

/** A nation's flag, 3:2, [height] tall. */
@Composable
fun FlagIcon(nation: Int, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    val layers = Flags.design(nation)
    Canvas(modifier.size(width = height * 3 / 2, height = height).clip(RoundedCornerShape(2.dp))) {
        val w = size.width
        val h = size.height
        for (l in layers) {
            val c = Color(l.color)
            when (l.kind) {
                FlagLayer.RECT -> drawRect(c, Offset(w * l.a, h * l.b), Size(w * l.c, h * l.d))
                FlagLayer.CIRCLE -> drawCircle(c, radius = h * l.c, center = Offset(w * l.a, h * l.b))
                FlagLayer.DIAMOND -> {
                    val p = Path()
                    p.moveTo(w * l.a, h * (l.b - l.d)); p.lineTo(w * (l.a + l.c), h * l.b)
                    p.lineTo(w * l.a, h * (l.b + l.d)); p.lineTo(w * (l.a - l.c), h * l.b); p.close()
                    drawPath(p, c)
                }
                FlagLayer.SALTIRE -> {
                    drawLine(c, Offset(0f, 0f), Offset(w, h), strokeWidth = h * l.a)
                    drawLine(c, Offset(0f, h), Offset(w, 0f), strokeWidth = h * l.a)
                }
            }
        }
        // hairline so pale flags stay visible on a dark screen
        drawRect(Color(0x44FFFFFF), style = Stroke(width = 1f))
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Faces
// ---------------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawFace(sp: AvatarSpec, s: Float, shirt: Long, collar: Long) {
    val skin = Color(sp.skin)
    val shade = Color(sp.skinShade)
    val hair = Color(sp.hair)
    val hairDark = Color(sp.hair).copy(alpha = 0.95f)
    drawRect(Color(0xFF1F2A22))
    // shoulders and collar
    drawOval(Color(shirt), Offset(s * 0.06f, s * 0.74f), Size(s * 0.88f, s * 0.62f))
    drawOval(Color(collar), Offset(s * 0.40f, s * 0.72f), Size(s * 0.20f, s * 0.14f))
    // neck
    drawRect(shade, Offset(s * 0.425f, s * 0.58f), Size(s * 0.15f, s * 0.2f))

    val hw = s * 0.50f * sp.faceW
    val hh = s * 0.58f * sp.faceH
    val hx = s * 0.5f - hw / 2f
    val hy = s * 0.13f
    val cx = s * 0.5f
    val style = sp.hairStyle

    // hair behind the head
    when (style) {
        HairStyle.LONG -> drawRoundRect(hair, Offset(hx - hw * 0.10f, hy + hh * 0.05f), Size(hw * 1.20f, hh * 1.15f), androidx.compose.ui.geometry.CornerRadius(hw * 0.4f))
        HairStyle.BOB -> drawRoundRect(hair, Offset(hx - hw * 0.08f, hy + hh * 0.05f), Size(hw * 1.16f, hh * 0.85f), androidx.compose.ui.geometry.CornerRadius(hw * 0.35f))
        HairStyle.AFRO -> drawCircle(hair, radius = hw * 0.78f, center = Offset(cx, hy + hh * 0.30f))
        HairStyle.BUN -> drawCircle(hair, radius = hw * 0.17f, center = Offset(cx, hy - hh * 0.04f))
    }
    // ears
    val ew = hw * 0.12f * sp.ear
    val eh = hh * 0.17f * sp.ear
    drawOval(shade, Offset(hx - ew * 0.6f, hy + hh * 0.42f), Size(ew, eh))
    drawOval(shade, Offset(hx + hw - ew * 0.4f, hy + hh * 0.42f), Size(ew, eh))
    // head
    drawOval(skin, Offset(hx, hy), Size(hw, hh))

    // hair in front: a cap over the top of the head, shaped by style
    val cap = when (style) {
        HairStyle.BALD -> 0f
        HairStyle.BUZZ -> 0.20f
        HairStyle.SHORT -> 0.28f
        HairStyle.PART -> 0.31f
        HairStyle.CURLY -> 0.28f
        HairStyle.AFRO -> 0.24f
        HairStyle.LONG -> 0.30f
        HairStyle.MOHAWK -> 0f
        HairStyle.BUN -> 0.24f
        HairStyle.RECEDING -> 0.17f
        else -> 0.30f
    }
    if (cap > 0f) {
        drawArc(hair, 180f, 180f, true, Offset(hx - hw * 0.02f, hy - hh * 0.02f), Size(hw * 1.04f, hh * cap * 2f))
        if (style != HairStyle.BUZZ && style != HairStyle.AFRO) {
            // a curved hairline cut into the cap
            drawOval(skin, Offset(hx + hw * 0.16f, hy + hh * cap * 0.80f), Size(hw * 0.68f, hh * cap * 0.65f))
        }
    }
    if (style == HairStyle.CURLY) {
        for (i in 0..5) drawCircle(hair, hw * 0.075f, Offset(hx + hw * (0.12f + 0.15f * i), hy + hh * 0.07f - (if (i % 2 == 0) hh * 0.02f else 0f)))
    }
    if (style == HairStyle.MOHAWK) drawRoundRect(hair, Offset(cx - hw * 0.07f, hy - hh * 0.06f), Size(hw * 0.14f, hh * 0.30f), androidx.compose.ui.geometry.CornerRadius(hw * 0.05f))
    if (style == HairStyle.PART) drawLine(shade.copy(alpha = 0.6f), Offset(hx + hw * 0.34f, hy + hh * 0.02f), Offset(hx + hw * 0.30f, hy + hh * 0.17f), strokeWidth = s * 0.008f)
    if (style == HairStyle.RECEDING) {
        drawCircle(skin, hw * 0.09f, Offset(hx + hw * 0.16f, hy + hh * 0.13f))
        drawCircle(skin, hw * 0.09f, Offset(hx + hw * 0.84f, hy + hh * 0.13f))
    }

    // eyes and brows
    val ey = hy + hh * 0.46f
    val dx = hw * 0.20f * sp.eyeSpacing
    for (side in intArrayOf(-1, 1)) {
        val ex = cx + side * dx
        drawOval(Color(0xFFF2F2F2), Offset(ex - hw * 0.085f, ey - hh * 0.032f), Size(hw * 0.17f, hh * 0.064f))
        drawCircle(Color(sp.eye), hw * 0.036f, Offset(ex, ey))
        drawCircle(Color(0xFF111111), hw * 0.017f, Offset(ex, ey))
        drawLine(hairDark, Offset(ex - hw * 0.095f, ey - hh * 0.075f), Offset(ex + hw * 0.095f, ey - hh * 0.085f - hh * 0.01f * side), strokeWidth = s * 0.016f * sp.brow)
        if (sp.lines) drawArc(shade.copy(alpha = 0.55f), 20f, 140f, false, Offset(ex - hw * 0.075f, ey + hh * 0.025f), Size(hw * 0.15f, hh * 0.05f), style = Stroke(width = s * 0.005f))
    }
    if (sp.lines) {
        drawArc(shade.copy(alpha = 0.4f), 200f, 140f, false, Offset(cx - hw * 0.22f, hy + hh * 0.22f), Size(hw * 0.44f, hh * 0.08f), style = Stroke(width = s * 0.005f))
    }
    if (sp.glasses) {
        val r = hw * 0.12f
        drawCircle(Color(0xFF222222), r, Offset(cx - dx, ey), style = Stroke(width = s * 0.009f))
        drawCircle(Color(0xFF222222), r, Offset(cx + dx, ey), style = Stroke(width = s * 0.009f))
        drawLine(Color(0xFF222222), Offset(cx - dx + r, ey), Offset(cx + dx - r, ey), strokeWidth = s * 0.009f)
    }
    // nose
    val nw = hw * 0.15f * sp.nose
    drawOval(shade.copy(alpha = 0.75f), Offset(cx - nw / 2f, hy + hh * 0.60f), Size(nw, hh * 0.055f))
    drawLine(shade.copy(alpha = 0.4f), Offset(cx, ey + hh * 0.02f), Offset(cx + hw * 0.02f, hy + hh * 0.60f), strokeWidth = s * 0.008f)

    // beard (men) drawn under the mouth, then mouth on top
    val mw = hw * 0.30f * sp.mouthW
    val my = hy + hh * 0.72f
    when (sp.beard) {
        BeardStyle.STUBBLE -> drawArc(hair.copy(alpha = 0.30f), 0f, 180f, true, Offset(hx + hw * 0.03f, hy + hh * 0.40f), Size(hw * 0.94f, hh * 0.62f))
        BeardStyle.FULL -> drawArc(hair.copy(alpha = 0.95f), 0f, 180f, true, Offset(hx + hw * 0.02f, hy + hh * 0.38f), Size(hw * 0.96f, hh * 0.66f))
        BeardStyle.GOATEE -> drawOval(hair, Offset(cx - hw * 0.10f, my + hh * 0.045f), Size(hw * 0.20f, hh * 0.15f))
    }
    if (sp.beard == BeardStyle.FULL) {
        // keep a clear mouth area inside a full beard
        drawOval(Color(sp.lip).copy(alpha = 0.9f), Offset(cx - mw / 2f, my - hh * 0.005f), Size(mw, hh * 0.05f))
    } else {
        drawArc(Color(sp.lip), 15f, 150f, false, Offset(cx - mw / 2f, my - hh * 0.035f), Size(mw, hh * 0.09f), style = Stroke(width = s * 0.014f))
    }
    if (sp.beard == BeardStyle.MOUSTACHE || sp.beard == BeardStyle.GOATEE || sp.beard == BeardStyle.FULL) {
        drawOval(hair, Offset(cx - mw * 0.55f, my - hh * 0.065f), Size(mw * 1.1f, hh * 0.045f))
    }
}

/** A generated face. Pass the club's colours for the shirt (or leave the defaults for a neutral top). */
@Composable
fun FaceImage(spec: AvatarSpec, size: Dp, shirt: Long = 0xFF3A4A3AL, collar: Long = 0xFFB8C4B8L, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clip(RoundedCornerShape(size / 6))) { drawFace(spec, this.size.minDimension, shirt, collar) }
}

/** A player's face in his club's colours (neutral shirt for a free agent). */
@Composable
fun PlayerFace(w: World, p: Player, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val club = if (p.clubId >= 0 && p.clubId < w.clubs.size) w.clubs[p.clubId] else null
    FaceImage(AvatarSpec.of(p.id, p.nation, p.age, 0), size, club?.colorA ?: 0xFF3A4A3AL, club?.colorB ?: 0xFFB8C4B8L, modifier)
}

/** Staff, board members and other people: [kind] 1 staff, 2 board, 3 referee. */
@Composable
fun PersonFace(id: Int, nation: Int, age: Int, kind: Int, size: Dp = 28.dp, female: Boolean = false, modifier: Modifier = Modifier) {
    FaceImage(AvatarSpec.of(id, nation, age, kind, female), size, 0xFF2B3A4AL, 0xFFB8C4B8L, modifier)
}

/** Face, flag and name on one line, as in the squad lists: the name takes the remaining width. */
@Composable
fun PlayerTag(w: World, p: Player, width: Dp, color: Color = C.text, bold: Boolean = false, face: Dp = 26.dp) {
    Row(Modifier.width(width), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PlayerFace(w, p, face)
        FlagIcon(p.nation, 11.dp)
        T(p.name, 12, color, bold, Modifier.weight(1f), lines = 1)
    }
}

/** The large portrait used on a player's profile: face with the flag tucked into its corner. */
@Composable
fun PlayerPortrait(w: World, p: Player, size: Dp = 84.dp) {
    Box(Modifier.size(size)) {
        PlayerFace(w, p, size)
        FlagIcon(p.nation, 16.dp, Modifier.align(Alignment.BottomEnd).padding(3.dp))
    }
}

/** Staff member: face, flag and name. */
@Composable
fun StaffTag(m: StaffMember, width: Dp) {
    Row(Modifier.width(width), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PersonFace(m.id, m.nation, m.age, 1, 22.dp)
        FlagIcon(m.nation, 10.dp)
        T(m.name, 12, C.text, false, Modifier.weight(1f), lines = 1)
    }
}

/** Board members carry no nation or age of their own, so a stable face and flag are derived from the name. */
@Composable
fun BoardFace(name: String, size: Dp = 44.dp) {
    val h = name.hashCode()
    val nation = Math.floorMod(h, Names.nations.size)
    Box(Modifier.size(size)) {
        PersonFace(h, nation, 45 + Math.floorMod(h / 7, 25), 2, size)
        FlagIcon(nation, 11.dp, Modifier.align(Alignment.BottomEnd).padding(2.dp))
    }
}
