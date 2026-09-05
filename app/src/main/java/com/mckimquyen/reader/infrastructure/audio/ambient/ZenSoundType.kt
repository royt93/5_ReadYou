package com.mckimquyen.reader.infrastructure.audio.ambient

import androidx.annotation.StringRes
import com.mckimquyen.reader.R

enum class ZenSoundType(@StringRes val titleRes: Int) {
    GENTLE_RAIN(R.string.zen_sound_rain),
    OCEAN_WAVES(R.string.zen_sound_ocean),
    PINK_NOISE(R.string.zen_sound_pink_noise),
    BINAURAL_40HZ(R.string.zen_sound_binaural),
    TIBETAN_BOWL(R.string.zen_sound_bowl)
}
