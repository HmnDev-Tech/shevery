package moe.shizuku.manager.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MultiLocaleEntityTest {

    @After
    fun restoreDefaultProvider() {
        MultiLocaleEntity.setLocaleProvider(MultiLocaleEntity.DEFAULT_LOCAL_PROVIDER)
    }

    @Test
    fun resolvesExactLocaleThenLanguagePrefixThenEnAndDefault() {
        val entity = MultiLocaleEntity().apply {
            put("en", "English")
            put("zh-CN", "Simplified Chinese")
            put("fr", "French")
            put("ru_RU", "Russian (Russia)")
            put("default", "Default Fallback")
        }

        assertEquals("Simplified Chinese", entity.get(Locale("zh", "CN")))
        assertEquals("Russian (Russia)", entity.get(Locale("ru", "RU")))
        assertEquals("French", entity.get(Locale("fr", "CA")))
        assertEquals("Simplified Chinese", entity.get(Locale("zh", "TW")))
        assertEquals("English", entity.get(Locale("de", "DE")))
    }

    @Test
    fun fallsBackToDefaultAndArbitraryNonOverwriteKey() {
        val withDefault = MultiLocaleEntity().apply {
            put("overwrite_default", "ignored")
            put("default", "Default Text")
        }
        assertEquals("Default Text", withDefault.get(Locale("ja", "JP")))

        val arbitraryOnly = MultiLocaleEntity().apply {
            put("overwrite_default", "ignored")
            put("es", "Spanish")
        }
        assertEquals("Spanish", arbitraryOnly.get(Locale("ko", "KR")))

        val empty = MultiLocaleEntity()
        assertNull(empty.get(Locale.US))
    }

    @Test
    fun customLocaleProviderDrivesZeroArgGetSafely() {
        val entity = MultiLocaleEntity().apply {
            put("en", "Hello")
            put("ru", "Privet")
        }
        MultiLocaleEntity.setLocaleProvider(object : MultiLocaleEntity.LocaleProvider() {
            override fun get(): Locale = Locale("ru", "RU")
        })
        assertEquals("Privet", entity.get())

        val empty = MultiLocaleEntity()
        assertEquals("", empty.get())
    }
}
