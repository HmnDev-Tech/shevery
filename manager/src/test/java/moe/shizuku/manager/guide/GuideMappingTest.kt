package moe.shizuku.manager.guide

import moe.shizuku.manager.Helps
import moe.shizuku.manager.settings.SettingsNav
import moe.shizuku.manager.settings.SettingsSection
import moe.shizuku.manager.settings.guideTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideMappingTest {
    @Test
    fun mapsEverySettingsSectionToItsGuide() {
        val expected = mapOf(
            SettingsSection.APPLICATION to GuideTopic.SETTINGS,
            SettingsSection.APPEARANCE to GuideTopic.SETTINGS,
            SettingsSection.SECURITY to GuideTopic.SECURITY,
            SettingsSection.DEVICE_OWNER to GuideTopic.DEVICE_OWNER,
            SettingsSection.MODULES to GuideTopic.MODULE_POLICY,
            SettingsSection.UPDATES to GuideTopic.UPDATES,
            SettingsSection.AI to GuideTopic.COMMANDIUM,
            SettingsSection.BACKUPS to GuideTopic.SETTINGS,
            SettingsSection.AUTOMATION to GuideTopic.CONNECTORS,
            SettingsSection.EXTRAS to GuideTopic.FAQ,
            SettingsSection.ABOUT to null
        )

        assertEquals(expected, SettingsSection.entries.associateWith { SettingsNav.Section(it).guideTopic() })
    }

    @Test
    fun mapsSettingsSubpagesAndUsesCatalogForGeneralScreens() {
        assertEquals(GuideTopic.UPDATES, SettingsNav.UpdateSettings.guideTopic())
        assertEquals(GuideTopic.COMPAT_STUBS, SettingsNav.CompatStubs.guideTopic())
        assertEquals(GuideTopic.DEVICE_OWNER, SettingsNav.DeviceOwnerTransfer.guideTopic())
        assertEquals(GuideTopic.DEVICE_OWNER, SettingsNav.DeviceOwnerDelegation.guideTopic())
        assertNull(SettingsNav.Hub.guideTopic())
        assertNull(SettingsNav.ContributorsCredits.guideTopic())
        assertNull(SettingsNav.Recommendations.guideTopic())
    }

    @Test
    fun catalogCoversWikiTopicsAndScreensWithoutDedicatedPages() {
        val wikiPages = setOf(
            "Installation", "Comput", "Commandium", "ADB-Modules", "Module-catalog",
            "Updates", "Dhizuku", "Settings", "Connectors", "App-authorization",
            "Tasker-Plugin", "Intents", "Module-policy", "FAQ", "How-it-works",
            "Publishing-a-module", "Build-from-source"
        )
        val wikiPrefix = "${Helps.SHEVERY_WIKI}/"
        val urls = GuideTopic.entries.mapNotNull { it.url }
        assertEquals(wikiPages, urls.filter { it.startsWith(wikiPrefix) }
            .map { it.removePrefix(wikiPrefix).substringBefore('#') }.toSet())

        assertTrue(GuideTopic.WIRELESS_DEBUGGING.url.orEmpty().endsWith("#wireless-debugging-no-pc"))
        assertTrue(GuideTopic.SECURITY.url.orEmpty().endsWith("#security"))
        assertTrue(GuideTopic.COMPAT_STUBS.url.orEmpty().endsWith("#application--startup-and-service"))
        assertTrue(GuideTopic.USB_DEBUGGING.url.orEmpty().endsWith("#2-usb-debugging-hiding"))
        assertTrue(GuideTopic.FIREWALL.url.orEmpty().endsWith("#3-network-firewall"))
        assertNull(GuideTopic.DEVELOPER_OPTIONS.url)
    }

    @Test
    fun eachGuideHasDistinctLocalizedContent() {
        val resourceIds = GuideTopic.entries.flatMap {
            listOf(it.title, it.overview, it.instructions, it.caution)
        }
        assertTrue(resourceIds.all { it != 0 })
        assertEquals(resourceIds.size, resourceIds.toSet().size)
    }
}
