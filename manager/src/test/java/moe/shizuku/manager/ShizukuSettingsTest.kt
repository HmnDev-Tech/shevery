package moe.shizuku.manager

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShizukuSettingsTest {

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)
        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = REMOVE_SENTINEL
            }

            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) data.clear()
                for ((k, v) in pending) {
                    if (v === REMOVE_SENTINEL) data.remove(k) else data[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        companion object {
            private val REMOVE_SENTINEL = Any()
        }
    }

    @After
    fun tearDown() {
        ShizukuSettings.setPreferencesForTesting(null, null)
    }

    @Test
    fun settingsValidatePortsClampBlurAndPersistLaunchMode() {
        val prefs = InMemorySharedPreferences()
        val auth = InMemorySharedPreferences()
        ShizukuSettings.setPreferencesForTesting(prefs, auth)

        assertEquals(ShizukuSettings.LaunchMethod.UNKNOWN, ShizukuSettings.getLastLaunchMode())
        ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.ADB)
        assertEquals(ShizukuSettings.LaunchMethod.ADB, ShizukuSettings.getLastLaunchMode())

        assertEquals(ShizukuSettings.DEFAULT_TCP_MODE_PORT, ShizukuSettings.getTcpModePort())
        ShizukuSettings.setTcpModePort(0)
        assertEquals(ShizukuSettings.DEFAULT_TCP_MODE_PORT, ShizukuSettings.getTcpModePort())
        ShizukuSettings.setTcpModePort(70000)
        assertEquals(ShizukuSettings.DEFAULT_TCP_MODE_PORT, ShizukuSettings.getTcpModePort())
        ShizukuSettings.setTcpModePort(5556)
        assertEquals(5556, ShizukuSettings.getTcpModePort())

        ShizukuSettings.setMotionBlurIntensity(-10)
        assertEquals(0, ShizukuSettings.getMotionBlurIntensity())
        ShizukuSettings.setMotionBlurIntensity(150)
        assertEquals(100, ShizukuSettings.getMotionBlurIntensity())

        ShizukuSettings.setMotionBlurStrength(-5)
        assertEquals(0, ShizukuSettings.getMotionBlurStrength())
        ShizukuSettings.setMotionBlurStrength(200)
        assertEquals(100, ShizukuSettings.getMotionBlurStrength())
    }

    @Test
    fun authTokenMigratesFromLegacyPrefsAndRegeneratesCleanly() {
        val prefs = InMemorySharedPreferences()
        val auth = InMemorySharedPreferences()
        prefs.edit().putString(ShizukuSettings.AUTH_TOKEN, "legacytoken1234567890abcd").apply()
        ShizukuSettings.setPreferencesForTesting(prefs, auth)

        assertEquals("legacytoken1234567890abcd", ShizukuSettings.getAuthToken())
        assertNull(prefs.getString(ShizukuSettings.AUTH_TOKEN, null))
        assertEquals("legacytoken1234567890abcd", auth.getString(ShizukuSettings.AUTH_TOKEN, null))

        val regenerated = ShizukuSettings.generateAuthToken()
        assertEquals(24, regenerated.length)
        assertNotEquals("legacytoken1234567890abcd", regenerated)
        assertEquals(regenerated, ShizukuSettings.getAuthToken())
    }

    @Test
    fun managerProviderBinderSenderUidValidationRejectsUntrustedUids() {
        val managerUid = 10199
        assertTrue(ShizukuManagerProvider.isTrustedBinderSenderUid(0, managerUid))
        assertTrue(ShizukuManagerProvider.isTrustedBinderSenderUid(2000, managerUid))
        assertTrue(ShizukuManagerProvider.isTrustedBinderSenderUid(managerUid, managerUid))
        ShizukuManagerProvider.enforceTrustedBinderSenderUid(0, managerUid)
        ShizukuManagerProvider.enforceTrustedBinderSenderUid(2000, managerUid)
        ShizukuManagerProvider.enforceTrustedBinderSenderUid(managerUid, managerUid)

        assertFalse(ShizukuManagerProvider.isTrustedBinderSenderUid(10250, managerUid))
        try {
            ShizukuManagerProvider.enforceTrustedBinderSenderUid(10250, managerUid)
            fail("Expected SecurityException for uid=10250")
        } catch (e: SecurityException) {
            assertEquals("Unauthorized binder delivery from uid=10250", e.message)
        }
    }
}
