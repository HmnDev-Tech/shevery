package com.rosan.dhizuku

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
class ForwardDhizukuProviderTest {

    private lateinit var provider: ForwardDhizukuProvider

    @Before
    fun setUp() {
        ShadowProcess.setUid(MY_UID)
        val info = ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            multiprocess = false
        }
        provider = Robolectric.buildContentProvider(ForwardDhizukuProvider::class.java)
            .create(info)
            .get()
    }

    @After
    fun tearDown() {
        ShadowBinder.reset()
        ShadowProcess.reset()
    }

    @Test
    fun binderDeliveryMethodsAllowTrustedUidsAndRejectUntrustedUid() {
        val protectedMethods = listOf(
            ForwardDhizukuProvider.METHOD_SEND_BINDER,
            ForwardDhizukuProvider.METHOD_SEND_USER_SERVICE
        )
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
                assertEquals("Unauthorized binder delivery from uid=$UNTRUSTED_UID", e.getMessage())
            }

            try {
                RuntimeEnvironment.getApplication().contentResolver.call(
                    Uri.parse("content://$AUTHORITY"),
                    method,
                    null,
                    Bundle()
                )
                fail("Expected SecurityException via ContentResolver for method=$method and uid=$UNTRUSTED_UID")
            } catch (e: SecurityException) {
                assertEquals("Unauthorized binder delivery from uid=$UNTRUSTED_UID", e.getMessage())
            }
        }
    }

    companion object {
        private const val MY_UID = 10420
        private const val UNTRUSTED_UID = 10234
        private const val AUTHORITY = "com.rosan.dhizuku.provider"
    }
}
