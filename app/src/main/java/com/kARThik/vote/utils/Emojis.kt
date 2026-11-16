package com.kARThik.vote.utils

import android.graphics.Paint

/** Standard emoji sets for candidate/party avatars. Lists are filtered to what this device can draw. */
object Emojis {
    private fun range(a: Int, b: Int) = (a..b).map { String(Character.toChars(it)) }
    private fun words(s: String) = s.split(" ").filter { it.isNotBlank() }

    private val raw: List<Pair<String, List<String>>> = listOf(
        "😀" to (range(0x1F600, 0x1F64F) + words("🥰 🥳 🤩 🥺 🤗 🤔 🤫 🥱 🤠 🥸 🤓 😎 🤯 😇")),
        "👍" to words("👍 👎 👏 🙌 🙏 💪 ✊ ✌️ 🤝 👋 🤞 👌 🤟 🤘 👆 👇 👈 👉 ☝️ ✋ 🖐️ 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 👮 👷 💂 🕵️ 👨‍🎓 👩‍🎓 👨‍🏫 👩‍🏫 👨‍⚖️ 👩‍🌾 👨‍🍳 🧑‍💻 🦸 🦹 🧙 🧑‍🚀 🧑‍🔧 🧑‍⚕️ 🧑‍🌾 🧑‍🏫 🧑‍🎓 👑 🎩 🧢 👓 🕶️"),
        "🦁" to (words("🐶 🐱 🦁 🐯 🐘 🦚 🦅 🐎 🐂 🦌 🐻 🐼 🐨 🐸 🐵 🦊 🐺 🦉 🕊️ 🦋 🐝 🐢 🐬 🐳 🦈 🐙") + range(0x1F400, 0x1F43E) + range(0x1F980, 0x1F9AE)),
        "🌳" to words("🌱 🌳 🌲 🌴 🌵 🌾 🌷 🌸 🌹 🌺 🌻 🌼 🍀 🍁 🍂 🍃 🌞 🌝 🌙 ⭐ 🌟 ⚡ 🔥 💧 🌈 ☀️ ☁️ ⛅ ❄️ 🌊 🌍 🌎 🌏 🏔️ 🌋"),
        "🍎" to (range(0x1F345, 0x1F37C) + range(0x1F950, 0x1F96B) + words("☕ 🍵 🥛")),
        "⚽" to words("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🏏 🏑 🏒 🏓 🏸 🥊 🥋 🎯 🎳 ♟️ 🎮 🎲 🏆 🥇 🥈 🥉 🏅 🎖️ 🎭 🎨 🎤 🎧 🎸 🎹 🥁 🎬 🎪 🎻 🎺") + range(0x1F3A0, 0x1F3AF),
        "🚗" to (words("🚗 🚕 🚌 🚲 🛵 ✈️ 🚀 🚁 ⛵ 🏠 🏫 🏛️ 🏢 🏥 🏭 🏰 🗼 🗽 ⛪ 🕌 🛕") + range(0x1F680, 0x1F6A4)),
        "💡" to words("📚 📖 📒 📝 ✏️ 🖊️ 🖋️ 📢 📣 🔔 💡 🔑 🗳️ 📊 📈 📉 💰 💼 🎓 🧭 ⚖️ 🛡️ 🔧 🔨 ⚙️ 💻 🖥️ 📱 📷 📺 ⏰ 🎁 🧪 🔬 🔭 📡 🧲 🪙 🏦 📜 📰 🗞️ 🏷️ 📌 📎"),
        "❤️" to words("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💖 💗 💓 💯 ✅ ❌ ❓ ❗ ✨ ♻️ ☮️ ☯️ ✝️ ☪️ 🕉️ ☸️ ✡️ ⚛️ 🔱 ⚜️ ➕ ➖ ✔️ ▶️ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟥 🟧 🟨 🟩 🟦 🟪 ⬛ ⬜ 🔺 🔷 ⭕ ♾️ ☑️"),
        "🚩" to words("🏁 🚩 🎌 🏴 🏳️ 🏳️‍🌈 🇮🇳 🇺🇸 🇬🇧 🇨🇦 🇦🇺 🇯🇵 🇫🇷 🇩🇪 🇧🇷 🇿🇦 🇨🇳 🇷🇺 🇮🇹 🇪🇸 🇲🇽 🇰🇷 🇳🇵 🇧🇩 🇱🇰")
    )

    /** Categories (tab icon, emojis) containing only emojis the device can render. */
    val categories: List<Pair<String, List<String>>> by lazy {
        val p = Paint()
        raw.map { (icon, list) -> icon to list.distinct().filter { runCatching { p.hasGlyph(it) }.getOrDefault(true) } }
            .filter { it.second.isNotEmpty() }
    }

    /** Keep only the first emoji-like grapheme of whatever was typed or pasted. */
    fun clean(input: String): String? {
        val t = input.trim()
        if (t.isEmpty()) return null
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(t)
        // Take up to the first whitespace-free run of at most 16 UTF-16 units (covers ZWJ sequences and flags).
        return t.take(16).takeIf { s -> s.any { ch -> !ch.isLetterOrDigit() || ch.code > 0x2000 } }
    }
}
