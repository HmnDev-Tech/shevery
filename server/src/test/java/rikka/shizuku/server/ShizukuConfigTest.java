package rikka.shizuku.server;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class ShizukuConfigTest {

    private ShizukuConfig.PackageEntry grant(int uid, String... names) {
        ShizukuConfig.PackageEntry entry = new ShizukuConfig.PackageEntry(uid, ConfigManager.FLAG_ALLOWED);
        entry.packages = new ArrayList<>(Arrays.asList(names));
        return entry;
    }

    @Test public void unavailableLookupPreservesGrantWithoutSchedulingWrite() {
        ShizukuConfig config = new ShizukuConfig();
        ShizukuConfig.PackageEntry valid = grant(12345, "app.one");
        config.packages.add(valid);
        assertFalse(config.reconcilePackages(uid -> null));
        assertSame(valid, config.packages.get(0));
        assertTrue(valid.isAllowed());
    }

    @Test public void confirmedAbsenceAndReassignedUidRemoveOnlyTheirEntries() {
        ShizukuConfig config = new ShizukuConfig();
        ShizukuConfig.PackageEntry shared = grant(12, "app.one", "app.two");
        config.packages.addAll(Arrays.asList(shared, grant(13, "app.gone"), grant(14, "app.old")));
        assertTrue(config.reconcilePackages(uid -> {
            if (uid == 12) return Collections.singletonList("app.two");
            if (uid == 13) return Collections.emptyList();
            return Collections.singletonList("app.new");
        }));
        assertEquals(Collections.singletonList(shared), config.packages);
    }

    @Test public void repairsMalformedRecordsAndPersistsDuplicateCleanupOnce() {
        ShizukuConfig config = new ShizukuConfig();
        ShizukuConfig.PackageEntry missing = grant(11);
        missing.packages = null;
        ShizukuConfig.PackageEntry valid = grant(12, "app.one", null, "", "app.one");
        config.packages.addAll(Arrays.asList(null, missing, grant(13, "", null), valid));
        assertTrue(config.reconcilePackages(uid -> Collections.singletonList("app.one")));
        assertEquals(Collections.singletonList(valid), config.packages);
        assertEquals(Collections.singletonList("app.one"), valid.packages);
        assertFalse(config.reconcilePackages(uid -> Collections.singletonList("app.one")));
    }
}
