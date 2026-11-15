package moe.shizuku.privileged.api

import android.app.Application
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import org.junit.After
import org.junit.Assert.assertEquals
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
class ForwardShizukuProviderTest {

    private lateinit var provider: ForwardShizukuProvider

    @Before
    fun setUp() {
        ShadowProcess.setUid(MY_UID)
        val info = ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            multiprocess = false
        }
        provider = Robolectric.buildContentProvider(ForwardShizukuProvider::class.java)
            .create(info)
            .get()
    }

    @After
    fun tearDown() {
        ShadowBinder.reset()
        ShadowProcess.reset()
    }

    @Test
    fun requestBinderAllowsClientUid() {
        ShadowBinder.setCallingUid(UNTRUSTED_UID)
        val reply = RuntimeEnvironment.getApplication().contentResolver.call(
            Uri.parse("content://$AUTHORITY"),
            ForwardShizukuProvider.METHOD_REQUEST_BINDER,
            null,
            Bundle()
        )
        assertNull(reply)
    }

    @Test
    fun nonRequestBinderMethodsAllowTrustedUidsAndRejectUntrustedUid() {
        val protectedMethods = listOf("sendBinder", "sendUserService", "getBinder")
        for (method in protectedMethods) {
            for (uid in intArrayOf(0, 2000, MY_UID)) {
                ShadowBinder.setCallingUid(uid)
                assertNull(provider.call(method, null, Bundle()))
            }

            ShadowBinder.setCallingUid(UNTRUSTED_UID)
            try {
                provider.call(method, null, Bundle())
                fail("Expected SecurityException for method=$method and uid=$UNTRUSTED_UID")
            } catch (e: SecurityException) {
                assertEquals("Unauthorized binder delivery from uid=$UNTRUSTED_UID", e.message)
            }
        }
    }

    companion object {
        private const val MY_UID = 10410
        private const val UNTRUSTED_UID = 10234
        private const val AUTHORITY = "moe.shizuku.privileged.api.shizuku"
    }
}
