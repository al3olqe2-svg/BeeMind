package com.beemind.personal.ui.notes

/** Explicit FNV-1a over UTF-16 code units: stable across processes, devices and list order. */
enum class HomeCardSize(val heightDp: Int, val previewLines: Int) {
    Compact(140, 3), Medium(185, 5), Tall(234, 7);
}

fun homeCardSize(id: String): HomeCardSize {
    var hash = 0x811c9dc5u
    for (character in id) hash = (hash xor character.code.toUInt()) * 0x01000193u
    return HomeCardSize.entries[(hash % 3u).toInt()]
}
