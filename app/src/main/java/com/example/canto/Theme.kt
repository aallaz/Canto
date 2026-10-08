package com.example.canto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Jeu de couleurs complet d'un mode (clair ou sombre). Le fond reste toujours sombre.
 *
 * Les rôles d'accent servent aux boutons du lecteur et des réglages : le mode sombre, uni,
 * leur donne la même couleur.
 */
data class CantoPalette(
    val name: String,
    val background: Color,
    val surface: Color,
    /** Bordures des cadres et des tuiles. */
    val frame: Color,
    /** Boutons secondaires, fond des pochettes absentes, piste des curseurs. */
    val secondary: Color,
    val shadow: Color,
    val text: Color,
    /** Texte et icônes posés sur un bouton coloré. */
    val onAccent: Color,
    val accent: Color,
    val accent2: Color,
    val accent3: Color,
    val accent4: Color,
    /** Erreurs et alertes (batterie faible, coupure du son…). */
    val warning: Color,
    /** Pochettes en bichromie (ombres, lumières), ou null pour les couleurs d'origine. */
    val coverDuotone: Pair<Color, Color>? = null
)

object Palettes {
    /** Mode clair, multicolore (style d'origine). */
    val Couleurs = CantoPalette(
        name = "couleurs",
        background = Color(0xFF121116),
        surface = Color(0xFF1D1B23),
        frame = Color(0xFF3B3747),
        secondary = Color(0xFF3B3747),
        shadow = Color(0xFF000000),
        text = Color(0xFFE6DFCB),
        onAccent = Color(0xFF121116),
        accent = Color(0xFFC9972F),
        accent2 = Color(0xFF2E8F8B),
        accent3 = Color(0xFF7A9A3C),
        accent4 = Color(0xFFC2632A),
        warning = Color(0xFFC2632A)
    )

    /** Mode sombre : uni bleu-vert foncé, très peu lumineux. */
    val Sombre = CantoPalette(
        name = "sombre",
        background = Color(0xFF001212),
        surface = Color(0xFF012323),
        frame = Color(0xFF03685A),
        secondary = Color(0xFF023B3B),
        shadow = Color(0xFF000000),
        text = Color(0xFF04725F),
        onAccent = Color(0xFF012A26),
        accent = Color(0xFF03665A),
        accent2 = Color(0xFF03665A),
        accent3 = Color(0xFF03665A),
        accent4 = Color(0xFF03665A),
        warning = Color(0xFF8C4A2A),
        coverDuotone = Color(0xFF06223A) to Color(0xFFB9D3DC)
    )

}

/**
 * Couleurs courantes. Les valeurs sont lues dans un état Compose : changer [palette]
 * redessine immédiatement toute l'interface.
 */
object CantoColors {
    var palette by mutableStateOf(Palettes.Couleurs)

    val Background get() = palette.background
    val Surface get() = palette.surface
    val Frame get() = palette.frame
    val Secondary get() = palette.secondary
    val Shadow get() = palette.shadow
    val Text get() = palette.text
    val OnAccent get() = palette.onAccent
    val Amber get() = palette.accent
    val Teal get() = palette.accent2
    val Moss get() = palette.accent3
    val Ember get() = palette.accent4
    val Warning get() = palette.warning
    val CoverDuotone get() = palette.coverDuotone
}
