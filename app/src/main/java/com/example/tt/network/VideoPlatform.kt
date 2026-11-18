package com.example.tt.network

/**
 * Nen tang video ma app ho tro tai.
 *
 * `id` duoc dung lam tien to ten file (vd: `douyin_7345...mp4`) va se duoc luu
 * vao SQLite o phien ban CSDL v2 (Phase B).
 */
enum class VideoPlatform(val id: String) {
    TIKTOK("tiktok"),
    DOUYIN("douyin");

    companion object {
        /** Doc lai tu CSDL / Intent; gia tri la -> mac dinh TikTok. */
        fun fromId(value: String?): VideoPlatform =
            values().firstOrNull { it.id.equals(value, ignoreCase = true) } ?: TIKTOK
    }
}
