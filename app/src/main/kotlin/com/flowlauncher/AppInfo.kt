package com.flowlauncher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    var icon: Drawable? = null,
    var screenTimeMinutes: Long = 0L,
    var isHidden: Boolean = false,
    var isFavorite: Boolean = false
)

enum class AppCategory(val displayName: String) {
    ALL("All"),
    MOST_USED("Most Used"),
    SOCIAL("Social"),
    PRODUCTIVITY("Productivity"),
    ENTERTAINMENT("Entertainment"),
    UTILITIES("Utilities"),
    OTHER("Other")
}

val SOCIAL_PACKAGES = setOf(
    "com.facebook.katana", "com.instagram.android", "com.twitter.android",
    "com.snapchat.android", "com.whatsapp", "org.telegram.messenger",
    "com.tiktok.android", "com.linkedin.android", "com.pinterest",
    "com.reddit.frontpage", "com.discord", "com.viber.voip",
    "com.skype.raider", "com.facebook.orca", "com.zhiliaoapp.musically"
)

val PRODUCTIVITY_PACKAGES = setOf(
    "com.google.android.gm", "com.microsoft.office.outlook",
    "com.google.android.calendar", "com.google.android.keep",
    "com.microsoft.office.word", "com.microsoft.office.excel",
    "com.microsoft.teams", "com.slack", "com.notion.id",
    "com.todoist", "md.obsidian", "com.evernote", "com.adobe.reader",
    "com.google.android.apps.docs"
)

val ENTERTAINMENT_PACKAGES = setOf(
    "com.netflix.mediaclient", "com.spotify.music", "com.google.android.youtube",
    "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus",
    "com.hulu.plus", "com.twitch.android.app", "tv.twitch.android.app",
    "com.google.android.apps.youtube.music", "com.apple.android.music",
    "com.soundcloud.android", "com.vanced.android.youtube"
)

fun AppInfo.getCategory(): AppCategory {
    return when (packageName) {
        in SOCIAL_PACKAGES -> AppCategory.SOCIAL
        in PRODUCTIVITY_PACKAGES -> AppCategory.PRODUCTIVITY
        in ENTERTAINMENT_PACKAGES -> AppCategory.ENTERTAINMENT
        else -> {
            val pkg = packageName.lowercase()
            when {
                pkg.contains("clock") || pkg.contains("calculator") ||
                pkg.contains("camera") || pkg.contains("gallery") ||
                pkg.contains("file") || pkg.contains("setting") ||
                pkg.contains("phone") || pkg.contains("contact") -> AppCategory.UTILITIES
                else -> AppCategory.OTHER
            }
        }
    }
}
