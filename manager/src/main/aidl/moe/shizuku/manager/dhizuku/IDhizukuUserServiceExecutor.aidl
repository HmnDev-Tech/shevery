package moe.shizuku.manager.dhizuku;

import android.content.ComponentName;
import android.os.IBinder;

interface IDhizukuUserServiceExecutor {
    IBinder getOrCreateService(in ComponentName component);
    void destroyService(in ComponentName component);
}
