package rikka.shizuku.server;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-process watchdog, ported from NightDog (kerneldroid/Nightzuku).
 *
 * Lives inside shizuku_server (not the manager app):
 * - the server main thread calls {@link #beat()} every 5 seconds;
 * - a watchdog thread checks heartbeat freshness on a 60-second poll;
 * - if the heartbeat is overdue by 60 seconds, the watchdog kills the
 *   server process so it can be restarted (the manager's WatchdogManager
 *   observes binder death and issues the restart command);
 * - the watchdog also holds linkToDeath bindings on four system services
 *   (package, activity, user, appops) and rebinds them with exponential
 *   backoff (up to 10 attempts);
 * - a fallback pingBinder() check runs each poll cycle.
 */
public final class Watchdog {

    private static final String TAG = "Watchdog";

    private static final long HEARTBEAT_INTERVAL_MS = 5_000L;
    private static final long TIMEOUT_MS = 60_000L;
    private static final long POLL_INTERVAL_MS = 60_000L;
    private static final int MAX_REBIND_ATTEMPTS = 10;

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static HandlerThread watchdogThread;
    private static Handler handler;
    private static Handler mainHandler;

    private static final AtomicLong lastBeatTime = new AtomicLong(0L);

    private static final Map<String, IBinder> bindings = new ConcurrentHashMap<>();
    private static final Map<String, IBinder.DeathRecipient> deathRecipients = new ConcurrentHashMap<>();
    private static final Map<String, Integer> rebindAttempts = new ConcurrentHashMap<>();

    private static final Runnable beatRunnable = new Runnable() {
        @Override
        public void run() {
            beat();
            if (started.get() && mainHandler != null) {
                mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        }
    };

    private static final Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkHealth();
        }
    };

    private Watchdog() {
    }

    public static synchronized void start(Handler main) {
        if (!started.compareAndSet(false, true)) return;

        mainHandler = main;

        watchdogThread = new HandlerThread("watchdog");
        watchdogThread.start();
        handler = new Handler(watchdogThread.getLooper());

        linkToServices("package", "activity", "user", "appops");

        if (mainHandler != null) {
            mainHandler.post(beatRunnable);
        }
        handler.postDelayed(healthCheckRunnable, POLL_INTERVAL_MS);
        Log.i(TAG, "Watchdog started");
    }

    public static synchronized void stop() {
        if (!started.compareAndSet(true, false)) return;

        if (mainHandler != null) {
            try {
                mainHandler.removeCallbacks(beatRunnable);
            } catch (Throwable ignored) {
            }
            mainHandler = null;
        }

        if (handler != null) {
            try {
                handler.removeCallbacks(healthCheckRunnable);
                handler.removeCallbacksAndMessages(null);
            } catch (Throwable ignored) {
            }
        }

        for (Map.Entry<String, IBinder.DeathRecipient> entry : deathRecipients.entrySet()) {
            IBinder binder = bindings.get(entry.getKey());
            if (binder != null) {
                try {
                    binder.unlinkToDeath(entry.getValue(), 0);
                } catch (Throwable ignored) {
                }
            }
        }
        bindings.clear();
        deathRecipients.clear();
        rebindAttempts.clear();
        lastBeatTime.set(0L);

        if (watchdogThread != null) {
            try {
                watchdogThread.quitSafely();
            } catch (Throwable ignored) {
            }
        }
        watchdogThread = null;
        handler = null;
        Log.i(TAG, "Watchdog stopped");
    }

    public static void beat() {
        lastBeatTime.set(SystemClock.uptimeMillis());
    }

    public static boolean isStarted() {
        return started.get();
    }

    private static void linkToServices(String... names) {
        for (String name : names) {
            IBinder binder;
            try {
                binder = ServiceManager.getService(name);
            } catch (Throwable tr) {
                Log.e(TAG, "getService(" + name + ") failed", tr);
                continue;
            }
            if (binder == null) continue;
            linkTo(name, binder);
        }
    }

    private static void linkTo(final String name, IBinder binder) {
        IBinder.DeathRecipient recipient = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                Log.e(TAG, "Service died: " + name);
                bindings.remove(name);
                deathRecipients.remove(name);
                rebindAttempts.remove(name);
                Handler h = handler;
                if (started.get() && h != null) {
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            rebind(name);
                        }
                    }, 2000L);
                }
            }
        };
        try {
            binder.linkToDeath(recipient, 0);
            bindings.put(name, binder);
            deathRecipients.put(name, recipient);
            rebindAttempts.put(name, 0);
        } catch (Throwable tr) {
            Log.e(TAG, "linkToDeath(" + name + ") failed", tr);
        }
    }

    private static void rebind(String name) {
        if (!started.get()) return;

        Integer attemptsBoxed = rebindAttempts.get(name);
        int attempts = attemptsBoxed != null ? attemptsBoxed : 0;
        if (attempts >= MAX_REBIND_ATTEMPTS) {
            Log.e(TAG, "Service " + name + ": max rebind attempts reached, giving up");
            rebindAttempts.remove(name);
            return;
        }
        rebindAttempts.put(name, attempts + 1);

        IBinder binder;
        try {
            binder = ServiceManager.getService(name);
        } catch (Throwable tr) {
            Log.e(TAG, "getService(" + name + ") failed on rebind", tr);
            return;
        }
        if (binder != null && binder != bindings.get(name)) {
            Log.i(TAG, "Service recovered: " + name);
            linkTo(name, binder);
        } else if (binder == null) {
            long delay = Math.min(5000L * (1L << attempts), 60_000L);
            Handler h = handler;
            if (started.get() && h != null) {
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        rebind(name);
                    }
                }, delay);
            }
        }
    }

    private static void checkHealth() {
        if (!started.get()) return;

        long beat = lastBeatTime.get();
        if (beat > 0) {
            long elapsed = SystemClock.uptimeMillis() - beat;
            if (elapsed > TIMEOUT_MS) {
                Log.e(TAG, "Heartbeat overdue by " + elapsed + "ms, server may be stuck");
                stop();
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
        }

        for (Map.Entry<String, IBinder> entry : bindings.entrySet()) {
            final String name = entry.getKey();
            final IBinder binder = entry.getValue();
            try {
                if (!binder.pingBinder()) {
                    Log.w(TAG, "Service " + name + " not responding (fallback check)");
                    bindings.remove(name);
                    deathRecipients.remove(name);
                    Handler h = handler;
                    if (started.get() && h != null) {
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                rebind(name);
                            }
                        }, 2000L);
                    }
                }
            } catch (Throwable tr) {
                Log.e(TAG, "Error checking " + name, tr);
            }
        }

        Handler h = handler;
        if (started.get() && h != null) {
            h.postDelayed(healthCheckRunnable, POLL_INTERVAL_MS);
        }
    }
}
