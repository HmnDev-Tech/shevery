package rikka.shizuku.server;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

public class ShizukuConfig {

    public static final int LATEST_VERSION = 2;

    @SerializedName("version")
    public int version = LATEST_VERSION;

    @SerializedName("packages")
    public List<PackageEntry> packages = new ArrayList<>();

    @SerializedName("errorprotect_enabled")
    public boolean errorProtectEnabled = true;

    public static class PackageEntry extends ConfigPackageEntry {

        @SerializedName("uid")
        public final int uid;

        @SerializedName("flags")
        public volatile int flags;

        @SerializedName("packages")
        public List<String> packages;

        public PackageEntry(int uid, int flags) {
            this.uid = uid;
            this.flags = flags;
            this.packages = new ArrayList<>();
        }

        @Override
        public boolean isAllowed() {
            return (flags & ConfigManager.FLAG_ALLOWED) != 0;
        }

        @Override
        public boolean isDenied() {
            return (flags & ConfigManager.FLAG_DENIED) != 0;
        }
    }

    /**
     * An empty lookup confirms an unused UID. A null lookup means the package
     * service could not answer, so keep the record for the next identity check.
     */
    boolean reconcilePackages(IntFunction<List<String>> installedPackages) {
        if (packages == null) {
            packages = new ArrayList<>();
            return true;
        }
        boolean changed = false;
        for (Iterator<PackageEntry> it = packages.iterator(); it.hasNext();) {
            PackageEntry entry = it.next();
            if (entry == null || entry.packages == null || entry.packages.isEmpty()) {
                it.remove();
                changed = true;
                continue;
            }
            Set<String> names = new LinkedHashSet<>(entry.packages);
            names.remove(null);
            names.remove("");
            if (names.isEmpty()) {
                it.remove();
                changed = true;
                continue;
            }
            List<String> installed = installedPackages.apply(entry.uid);
            if (installed != null && Collections.disjoint(names, installed)) {
                it.remove();
                changed = true;
            } else if (names.size() != entry.packages.size()) {
                entry.packages = new ArrayList<>(names);
                changed = true;
            }
        }
        return changed;
    }

    public ShizukuConfig() {
    }

    public ShizukuConfig(@NonNull List<PackageEntry> packages) {
        this.version = LATEST_VERSION;
        this.packages = packages;
    }
}
