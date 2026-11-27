package moe.shizuku.manager.utils

import moe.shizuku.manager.ShizukuSettings
import java.util.LinkedHashMap
import java.util.Locale

/** Locale-indexed string map with region/language fallback resolution. */
class MultiLocaleEntity : LinkedHashMap<String, String>() {

    abstract class LocaleProvider {
        abstract fun get(): Locale
    }

    fun get(): String = get(sLocaleProvider.get()).orEmpty()

    fun get(locale: Locale): String? {
        if (size > 0) {
            val language = locale.language
            val region = locale.country

            val fullLocale = Locale(language, region)
            val fullTag = fullLocale.toString()
            for (l in keys) {
                if (fullTag == l.replace('-', '_')) {
                    return super.get(l)
                }
            }

            val langOnly = Locale(language).toString()
            for (l in keys) {
                if (langOnly == l) {
                    return super.get(l)
                }
            }

            for (l in keys) {
                if (langOnly.isNotEmpty() && l.replace('-', '_').startsWith("${langOnly}_")) {
                    return super.get(l)
                }
            }

            if (containsKey("en")) {
                return super.get("en")
            }

            if (containsKey("default")) {
                return super.get("default")
            }

            for (key in keys) {
                if ("overwrite_default" != key) {
                    return super.get(key)
                }
            }
        }
        return null
    }

    companion object {
        @JvmField
        val DEFAULT_LOCAL_PROVIDER: LocaleProvider = object : LocaleProvider() {
            override fun get(): Locale = ShizukuSettings.getLocale()
        }

        private var sLocaleProvider: LocaleProvider = DEFAULT_LOCAL_PROVIDER

        @JvmStatic
        fun setLocaleProvider(localeProvider: LocaleProvider) {
            sLocaleProvider = localeProvider
        }
    }
}
