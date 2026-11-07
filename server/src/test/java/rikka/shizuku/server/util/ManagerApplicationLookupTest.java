package rikka.shizuku.server.util;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManagerApplicationLookupTest {
    @Test public void transientPackageManagerFailureRecovers() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger retries = new AtomicInteger();
        Object application = new Object();
        ManagerApplicationLookup.Result<Object> result = ManagerApplicationLookup.resolve(() -> {
            if (calls.incrementAndGet() < 3) throw new IOException("package manager restarting");
            return application;
        }, retries::incrementAndGet);
        assertSame(application, result.application);
        assertFalse(result.confirmedMissing);
        assertEquals(3, calls.get());
        assertEquals(2, retries.get());
    }

    @Test public void repeatedNullDuringReplacementDoesNotStopWhenPackageReturns() {
        AtomicInteger calls = new AtomicInteger();
        Object application = new Object();
        ManagerApplicationLookup.Result<Object> result = ManagerApplicationLookup.resolve(
                () -> calls.incrementAndGet() < 5 ? null : application, () -> {});
        assertSame(application, result.application);
        assertFalse(result.confirmedMissing);
    }

    @Test public void repeatedExceptionsNeverConfirmUninstallAndRetriesAreBounded() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger retries = new AtomicInteger();
        IOException failure = new IOException("Binder unavailable");
        ManagerApplicationLookup.Result<Object> result = ManagerApplicationLookup.resolve(() -> {
            calls.incrementAndGet();
            throw failure;
        }, retries::incrementAndGet);
        assertNull(result.application);
        assertFalse(result.confirmedMissing);
        assertSame(failure, result.failure);
        assertEquals(5, calls.get());
        assertEquals(4, retries.get());
    }

    @Test public void onlyConsecutiveSuccessfulAbsenceCanConfirmUninstall() {
        AtomicInteger calls = new AtomicInteger();
        ManagerApplicationLookup.Result<Object> result = ManagerApplicationLookup.resolve(() -> {
            if (calls.incrementAndGet() == 3) throw new IOException("interrupted");
            return null;
        }, () -> {});
        assertFalse(result.confirmedMissing);
    }

    @Test public void repeatedSuccessfulAbsenceConfirmsUninstall() {
        ManagerApplicationLookup.Result<Object> result = ManagerApplicationLookup.resolve(() -> null, () -> {});
        assertNull(result.application);
        assertTrue(result.confirmedMissing);
    }
}
