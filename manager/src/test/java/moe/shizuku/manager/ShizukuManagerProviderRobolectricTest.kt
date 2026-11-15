package moe.shizuku.manager

import android.app.Application
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import com.hamondev.shevery.api.SheveryApiConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder
import org.robolectric.shadows.ShadowProcess

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class ShizukuManagerProviderRobolectricTest {

    private lateinit var provider: ShizukuManagerProvider

    @Before
    fun setUp() {
        ShadowProcess.setUid(MY_UID)
        val info = ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            multiprocess = false
        }
        provider = Robolectric.buildContentProvider(ShizukuManagerProvider::class.java)
            .create(info)
            .get()
    }

    @After
    fun tearDown() {
        ShadowBinder.reset()
        ShadowProcess.reset()
    }

    @Test
    fun sendBinderAllowsTrustedUidsAndRejectsUntrustedUid() {
        for (uid in intArrayOf(0, 2000, MY_UID)) {
            ShadowBinder.setCallingUid(uid)
            val result = provider.call(SheveryApiConstants.METHOD_SEND_BINDER, null, Bundle())
            assertNotNull("Expected non-null Bundle for trusted uid=$uid", result)
        }

        ShadowBinder.setCallingUid(UNTRUSTED_UID)
        try {
            provider.call(SheveryApiConstants.METHOD_SEND_BINDER, null, Bundle())
            fail("Expected SecurityException for untrusted sendBinder")
        } catch (e: SecurityException) {
            assertEquals("Unauthorized binder delivery from uid=$UNTRUSTED_UID", e.message)
        }

        try {
            RuntimeEnvironment.getApplication().contentResolver.call(
                Uri.parse("content://$AUTHORITY"),
                SheveryApiConstants.METHOD_SEND_BINDER,
                null,
                Bundle()
            )
            fail("Expected SecurityException via ContentResolver for untrusted sendBinder")
        } catch (e: SecurityException) {
            assertEquals("Unauthorized binder delivery from uid=$UNTRUSTED_UID", e.message)
        }
    }

    @Test
    fun sendUserServiceAllowsTrustedUidsAndRejectsUntrustedUid() {
        for (uid in intArrayOf(0, 2000, MY_UID)) {
            ShadowBinder.setCallingUid(uid)
            assertNull(provider.call(SheveryApiConstants.METHOD_SEND_USER_SERVICE, null, Bundle()))
        }

        ShadowBinder.setCallingUid(UNTRUSTED_UID)
        try {
            provider.call(SheveryApiConstants.METHOD_SEND_USER_SERVICE, null, Bundle())
            fail("Expected SecurityException for untrusted sendUserService")
        } catch (e: SecurityException) {
            assertEquals("Unauthorized binder delivery from uid=$UNTRUSTED_UID", e.message)
        }
    }

    @Test
    fun requestBinderAllowsClientUids() {
        ShadowBinder.setCallingUid(UNTRUSTED_UID)
        val result = RuntimeEnvironment.getApplication().contentResolver.call(
            Uri.parse("content://$AUTHORITY"),
            SheveryApiConstants.METHOD_REQUEST_BINDER,
            null,
            Bundle()
        )
        assertNull(result)
    }

    companion object {
        private const val MY_UID = 10400
        private const val UNTRUSTED_UID = 10234
        private const val AUTHORITY = "com.hamondev.shevery.shizuku"
    }
}
