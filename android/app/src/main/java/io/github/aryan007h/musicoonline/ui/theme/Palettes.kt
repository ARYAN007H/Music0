package io.github.aryan007h.musicoonline.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The five app palettes. Each is built on a color-theory relationship and
 * keeps the same STRUCTURE (three surface depths + ink ladder + one lead
 * accent + one warm counterpoint + one supporting hue), so swapping a
 * palette never changes layout or legibility:
 *
 *  - **Nebula** (default): cool indigo-black surfaces; violet lead with a
 *    mint counterpoint — split-complementary (violet → green-cyan), quiet
 *    warm amber as the tertiary pop. Calm, spacious, "night studio".
 *  - **Ocean**: deep navy surfaces; sky-blue lead with a golden-amber
 *    counterpoint — the classic blue/orange complementary pair; teal
 *    support. Cinematic, wide, "deep water".
 *  - **Ember**: warm charcoal surfaces; coral lead with gold support —
 *    analogous warm cluster (red-orange → yellow) held together by low
 *    saturation; a single cool slate counterpoint keeps it from going
 *    monochrome-muddy. Bold, close, "live show".
 *  - **Forest**: green-black surfaces; leaf-green lead with a soft peach
 *    counterpoint — green/red (via peach) near-complementary, muted to
 *    natural levels. Grounded, organic, "acoustic session".
 *  - **Mono**: neutral graphite surfaces; white lead — pure monochromatic
 *    scheme where hierarchy comes from luminance alone; the smallest warm
 *    tint on the "coral" slot keeps likes visible. Editorial, "print".
 *
 * All values are hand-tuned for WCAG-comfortable contrast on their own
 * surfaces (lead/ink ≥ 7:1, dim ink ≥ 4.5:1 against bg0/bg1).
 */
data class Palette(
    val id: String,
    val label: String,
    val blurb: String,
    // Surfaces (dark-first, three depths)
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    // Ink ladder
    val ink: Color,
    val inkDim: Color,
    val inkFaint: Color,
    // Lead accent (primary)
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // Counterpoint accent (the "mint" slot — secondary actions, playing EQ)
    val accent: Color,
    val onAccent: Color,
    // Warm pop (likes, errors)
    val pop: Color,
    // Supporting hue (aurora veils, gradients)
    val support: Color,
) {
    companion object {
        val NEBULA = Palette(
            id = "nebula",
            label = "Nebula",
            blurb = "Violet & mint — split-complementary calm",
            bg0 = Color(0xFF0A0A12),
            bg1 = Color(0xFF12121C),
            bg2 = Color(0xFF1C1C2A),
            ink = Color(0xFFF2F1F7),
            inkDim = Color(0xFFA8A6B8),
            inkFaint = Color(0xFF6E6C80),
            primary = Color(0xFF7C5CFF),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF3B2A80),
            onPrimaryContainer = Color(0xFFE6DEFF),
            accent = Color(0xFF53F2C4),
            onAccent = Color(0xFF05291D),
            pop = Color(0xFFFF6E7A),
            support = Color(0xFF2E86FF),
        )

        val OCEAN = Palette(
            id = "ocean",
            label = "Ocean",
            blurb = "Sky blue & gold — cinematic complementary",
            bg0 = Color(0xFF080D1A),
            bg1 = Color(0xFF0F1626),
            bg2 = Color(0xFF18223A),
            ink = Color(0xFFEDF2FB),
            inkDim = Color(0xFF9DABC4),
            inkFaint = Color(0xFF5F6E8C),
            primary = Color(0xFF4FA3FF),
            onPrimary = Color(0xFF00234D),
            primaryContainer = Color(0xFF1A3E6E),
            onPrimaryContainer = Color(0xFFD6E8FF),
            accent = Color(0xFFFFC24B),
            onAccent = Color(0xFF3D2A00),
            pop = Color(0xFFFF6E7A),
            support = Color(0xFF18C8B0),
        )

        val EMBER = Palette(
            id = "ember",
            label = "Ember",
            blurb = "Coral & gold — warm analogous heat",
            bg0 = Color(0xFF140E0C),
            bg1 = Color(0xFF1E1512),
            bg2 = Color(0xFF2C1F1A),
            ink = Color(0xFFFDF3EE),
            inkDim = Color(0xFFC4A9A0),
            inkFaint = Color(0xFF8A7068),
            primary = Color(0xFFFF6E5A),
            onPrimary = Color(0xFF4D1000),
            primaryContainer = Color(0xFF7C2A1E),
            onPrimaryContainer = Color(0xFFFFD9CF),
            accent = Color(0xFFFFC24B),
            onAccent = Color(0xFF3D2A00),
            pop = Color(0xFFFF6E7A),
            support = Color(0xFF5A7CA6),
        )

        val FOREST = Palette(
            id = "forest",
            label = "Forest",
            blurb = "Leaf & peach — grounded near-complementary",
            bg0 = Color(0xFF0A110D),
            bg1 = Color(0xFF111A14),
            bg2 = Color(0xFF1B2A20),
            ink = Color(0xFFEFF5F0),
            inkDim = Color(0xFFA6B8AC),
            inkFaint = Color(0xFF6B7E72),
            primary = Color(0xFF53D18A),
            onPrimary = Color(0xFF00391D),
            primaryContainer = Color(0xFF1E5236),
            onPrimaryContainer = Color(0xFFD2F5DF),
            accent = Color(0xFFFFA07A),
            onAccent = Color(0xFF4D1F00),
            pop = Color(0xFFFF6E7A),
            support = Color(0xFF6C8FFF),
        )

        val MONO = Palette(
            id = "mono",
            label = "Mono",
            blurb = "Pure graphite — luminance-led editorial",
            bg0 = Color(0xFF0C0C0D),
            bg1 = Color(0xFF151517),
            bg2 = Color(0xFF222225),
            ink = Color(0xFFF4F4F5),
            inkDim = Color(0xFFABABB0),
            inkFaint = Color(0xFF6C6C72),
            primary = Color(0xFFF4F4F5),
            onPrimary = Color(0xFF111114),
            primaryContainer = Color(0xFF3A3A40),
            onPrimaryContainer = Color(0xFFF4F4F5),
            accent = Color(0xFFB8B8C0),
            onAccent = Color(0xFF111114),
            pop = Color(0xFFF2A2A8),
            support = Color(0xFF8A8A92),
        )

        val ALL = listOf(NEBULA, OCEAN, EMBER, FOREST, MONO)

        fun fromId(id: String?): Palette = ALL.firstOrNull { it.id == id } ?: NEBULA
    }
}
