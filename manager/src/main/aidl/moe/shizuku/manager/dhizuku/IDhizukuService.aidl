package moe.shizuku.manager.dhizuku;

interface IDhizukuService {
    void runCommand(String command);
    boolean enableAdb();
    int getAdbPort();
    boolean bindAdbTcp(int port);
}
