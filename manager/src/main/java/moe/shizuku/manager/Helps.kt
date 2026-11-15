package moe.shizuku.manager

import moe.shizuku.manager.utils.MultiLocaleEntity

/** Localized documentation and help URLs used throughout the manager UI. */
object Helps {

    @JvmField
    val ADB = MultiLocaleEntity()

    @JvmField
    val ADB_ANDROID11 = MultiLocaleEntity()

    @JvmField
    val APPS = MultiLocaleEntity()

    @JvmField
    val HOME = MultiLocaleEntity()

    @JvmField
    val DOWNLOAD = MultiLocaleEntity()

    @JvmField
    val SUI = MultiLocaleEntity()

    @JvmField
    val RISH = MultiLocaleEntity()

    @JvmField
    val ADB_PERMISSION = MultiLocaleEntity()

    init {
        ADB["zh-CN"] = "https://shizuku.rikka.app/zh-hans/guide/setup/"
        ADB["zh-TW"] = "https://shizuku.rikka.app/zh-hant/guide/setup/"
        ADB["en"] = "https://shizuku.rikka.app/guide/setup/"

        ADB_ANDROID11["zh-CN"] = "https://shizuku.rikka.app/zh-hans/guide/setup/"
        ADB_ANDROID11["zh-TW"] = "https://shizuku.rikka.app/zh-hant/guide/setup/"
        ADB_ANDROID11["en"] = "https://shizuku.rikka.app/guide/setup/"

        APPS["zh-CN"] = "https://shizuku.rikka.app/zh-hans/apps/"
        APPS["zh-TW"] = "https://shizuku.rikka.app/zh-hant/apps/"
        APPS["en"] = "https://shizuku.rikka.app/apps/"

        HOME["zh-CN"] = "https://shizuku.rikka.app/zh-hans/"
        HOME["zh-TW"] = "https://shizuku.rikka.app/zh-hant/"
        HOME["en"] = "https://shizuku.rikka.app/"

        DOWNLOAD["zh-CN"] = "https://shizuku.rikka.app/zh-hans/download/"
        DOWNLOAD["zh-TW"] = "https://shizuku.rikka.app/zh-hant/download/"
        DOWNLOAD["en"] = "https://shizuku.rikka.app/download/"

        ADB_PERMISSION["zh-CN"] =
            "https://shizuku.rikka.app/zh-hans/guide/setup/#%E9%80%9A%E8%BF%87%E6%97%A0%E7%BA%BF%E8%B0%83%E8%AF%95%E5%90%AF%E5%8A%A8-%E9%80%9A%E8%BF%87%E8%BF%9E%E6%8E%A5%E7%94%B5%E8%84%91%E5%90%AF%E5%8A%A8-adb-%E6%9D%83%E9%99%90%E5%8F%97%E9%99%90"
        ADB_PERMISSION["zh-TW"] =
            "https://shizuku.rikka.app/zh-hant/guide/setup/#%E9%80%8F%E9%81%8E%E7%84%A1%E7%B7%9A%E9%99%A4%E9%8C%AF%E5%95%9F%E5%8B%95-%E9%80%8F%E9%81%8E%E9%80%A3%E7%B7%9A%E9%9B%BB%E8%85%A6%E5%95%9F%E5%8B%95-adb-%E6%AC%8A%E9%99%90%E5%8F%97%E9%99%90"
        ADB_PERMISSION["en"] =
            "https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging-start-by-connecting-to-a-computer-the-permission-of-adb-is-limited"

        SUI["en"] = "https://github.com/RikkaApps/Sui"

        RISH["en"] = "https://github.com/RikkaApps/Shizuku-API/tree/master/rish"
    }
}
