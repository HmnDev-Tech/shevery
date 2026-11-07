package rikka.shizuku.server.util;

import java.util.concurrent.Callable;

/** Only repeated successful absence queries can stop the native supervisor. */
public final class ManagerApplicationLookup {
    private ManagerApplicationLookup() {}

    public static final class Result<T> {
        public final T application;
        public final boolean confirmedMissing;
        public final Exception failure;

        private Result(T application, boolean confirmedMissing, Exception failure) {
            this.application = application;
            this.confirmedMissing = confirmedMissing;
            this.failure = failure;
        }
    }

    public static <T> Result<T> resolve(Callable<T> query, Runnable beforeRetry) {
        int consecutiveMissing = 0;
        Exception failure = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (attempt > 0) beforeRetry.run();
            try {
                T application = query.call();
                if (application != null) return new Result<>(application, false, null);
                consecutiveMissing++;
            } catch (Exception e) {
                failure = e;
                consecutiveMissing = 0;
            }
        }
        return new Result<>(null, consecutiveMissing >= 3, failure);
    }
}
