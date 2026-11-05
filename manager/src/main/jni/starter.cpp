#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <ctime>
#include <cstring>
#include <libgen.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <sys/system_properties.h>
#include <cerrno>
#include <string>
#include <termios.h>
#include <csignal>
#include "android.h"
#include "misc.h"
#include "selinux.h"
#include "cgroup.h"
#include "logging.h"

#ifdef DEBUG
#define JAVA_DEBUGGABLE
#endif

#define perrorf(...) fprintf(stderr, __VA_ARGS__)

#define EXIT_FATAL_SET_CLASSPATH 3
#define EXIT_FATAL_FORK 4
#define EXIT_FATAL_APP_PROCESS 5
#define EXIT_FATAL_UID 6
#define EXIT_FATAL_PM_PATH 7
#define EXIT_FATAL_KILL 9
#define EXIT_FATAL_BINDER_BLOCKED_BY_SELINUX 10
#define EXIT_MANAGER_APP_NOT_FOUND 50
#define EXIT_MANAGER_APP_LOOKUP_UNAVAILABLE 51
#define STABLE_UPTIME_RESET_SECS 60

#define PACKAGE_NAME "com.hamondev.shevery"
#define SERVER_NAME "shizuku_server"
#define SERVER_CLASS_PATH "rikka.shizuku.server.ShizukuService"

#if defined(__arm__)
#define ABI "arm"
#elif defined(__i386__)
#define ABI "x86"
#elif defined(__x86_64__)
#define ABI "x86_64"
#elif defined(__aarch64__)
#define ABI "arm64"
#endif

static void detach_stdio_to_dev_null() {
    int fd = open("/dev/null", O_RDWR);
    if (fd != -1) {
        dup2(fd, STDIN_FILENO);
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        if (fd > 2) close(fd);
    }
}

static void run_server(const char *dex_path, const char *main_class, const char *process_name) {
    signal(SIGPIPE, SIG_DFL);
    signal(SIGHUP, SIG_DFL);

    if (setenv("CLASSPATH", dex_path, true)) {
        LOGE("can't set CLASSPATH\n");
        exit(EXIT_FATAL_SET_CLASSPATH);
    }

#define ARG(v) char **v = nullptr; \
    char buf_##v[PATH_MAX]; \
    size_t v_size = 0; \
    uintptr_t v_current = 0;
#define ARG_PUSH(v, arg) v_size += sizeof(char *); \
if (v == nullptr) { \
    v = (char **) malloc(v_size); \
} else { \
    v = (char **) realloc(v, v_size);\
} \
v_current = (uintptr_t) v + v_size - sizeof(char *); \
*((char **) v_current) = arg ? strdup(arg) : nullptr;

#define ARG_END(v) ARG_PUSH(v, nullptr)

#define ARG_PUSH_FMT(v, fmt, ...) snprintf(buf_##v, PATH_MAX, fmt, __VA_ARGS__); \
    ARG_PUSH(v, buf_##v)

#ifdef JAVA_DEBUGGABLE
#define ARG_PUSH_DEBUG_ONLY(v, arg) ARG_PUSH(v, arg)
#define ARG_PUSH_DEBUG_VM_PARAMS(v) \
    if (android_get_device_api_level() >= 30) { \
        ARG_PUSH(v, "-Xcompiler-option"); \
        ARG_PUSH(v, "--debuggable"); \
        ARG_PUSH(v, "-XjdwpProvider:adbconnection"); \
        ARG_PUSH(v, "-XjdwpOptions:suspend=n,server=y"); \
    } else if (android_get_device_api_level() >= 28) { \
        ARG_PUSH(v, "-Xcompiler-option"); \
        ARG_PUSH(v, "--debuggable"); \
        ARG_PUSH(v, "-XjdwpProvider:internal"); \
        ARG_PUSH(v, "-XjdwpOptions:transport=dt_android_adb,suspend=n,server=y"); \
    } else { \
        ARG_PUSH(v, "-Xcompiler-option"); \
        ARG_PUSH(v, "--debuggable"); \
        ARG_PUSH(v, "-agentlib:jdwp=transport=dt_android_adb,suspend=n,server=y"); \
    }
#else
#define ARG_PUSH_DEBUG_VM_PARAMS(v)
#define ARG_PUSH_DEBUG_ONLY(v, arg)
#endif

    char dex_copy[PATH_MAX]{0};
    strncpy(dex_copy, dex_path, PATH_MAX - 1);
    char lib_path[PATH_MAX]{0};
    snprintf(lib_path, PATH_MAX, "%s/lib/%s", dirname(dex_copy), ABI);

    ARG(argv)
    ARG_PUSH(argv, "/system/bin/app_process")
    ARG_PUSH_FMT(argv, "-Djava.class.path=%s", dex_path)
    ARG_PUSH_FMT(argv, "-Dshizuku.library.path=%s", lib_path)
    ARG_PUSH_DEBUG_VM_PARAMS(argv)
    ARG_PUSH(argv, "/system/bin")
    ARG_PUSH_FMT(argv, "--nice-name=%s", process_name)
    ARG_PUSH(argv, main_class)
    ARG_PUSH_DEBUG_ONLY(argv, "--debug")
    ARG_END(argv)

    LOGD("exec app_process for %s", process_name);

    if (execvp((const char *) argv[0], argv)) {
        PLOGE("execvp failed for app_process");
        exit(EXIT_FATAL_APP_PROCESS);
    }
}

[[noreturn]] static void supervise_server(const char *path, const char *main_class, const char *process_name, int pipe_write_fd) {
    int restart_count = 0;
    const int max_restarts = 5;
    int manager_lookup_retry_count = 0;
    const int max_manager_lookup_retries = 5;
    const unsigned int restart_delay_secs = 5;

    for (;;) {
        pid_t pid = fork();
        if (pid == -1) {
            LOGE("fatal: can't fork shizuku_server");
            if (pipe_write_fd != -1) {
                close(pipe_write_fd);
            }
            exit(EXIT_FATAL_FORK);
        } else if (pid == 0) {
            if (pipe_write_fd != -1) {
                close(pipe_write_fd);
            }
            setsid();
            chdir("/");
            detach_stdio_to_dev_null();
            run_server(path, main_class, process_name);
            _exit(EXIT_FATAL_APP_PROCESS);
        }

        struct timespec started_at{};
        clock_gettime(CLOCK_MONOTONIC, &started_at);

        if (pipe_write_fd != -1) {
            ssize_t ignored = write(pipe_write_fd, &pid, sizeof(pid));
            (void) ignored;
            close(pipe_write_fd);
            pipe_write_fd = -1;
            detach_stdio_to_dev_null();
        }
        LOGI("shizuku_server pid is %d", pid);

        int status = 0;
        while (waitpid(pid, &status, 0) == -1) {
            if (errno == EINTR) continue;
            break;
        }

        struct timespec ended_at{};
        clock_gettime(CLOCK_MONOTONIC, &ended_at);
        long uptime_secs = ended_at.tv_sec - started_at.tv_sec;
        if (uptime_secs < 0) uptime_secs = 0;

        int exit_code = -1;
        bool killed_by_signal = false;
        if (WIFEXITED(status)) {
            exit_code = WEXITSTATUS(status);
        } else if (WIFSIGNALED(status)) {
            exit_code = 128 + WTERMSIG(status);
            killed_by_signal = true;
        }

        LOGI("shizuku_server exited with code %d after %lds", exit_code, uptime_secs);

        if (!killed_by_signal && exit_code == 0) {
            LOGI("shizuku_server exited cleanly, no restart");
            exit(0);
        }

        if (!killed_by_signal && exit_code == EXIT_MANAGER_APP_NOT_FOUND) {
            LOGW("manager application not found, stopping supervisor without restart");
            exit(0);
        }

        if (!killed_by_signal && exit_code == EXIT_MANAGER_APP_LOOKUP_UNAVAILABLE) {
            if (manager_lookup_retry_count >= max_manager_lookup_retries) {
                LOGE("fatal: manager package lookup unavailable after %d retries, giving up",
                     manager_lookup_retry_count);
                exit(EXIT_FATAL_APP_PROCESS);
            }
            manager_lookup_retry_count += 1;
            LOGW("manager package lookup unavailable, retrying (%d/%d)",
                 manager_lookup_retry_count, max_manager_lookup_retries);
            sleep(restart_delay_secs);
            continue;
        }

        manager_lookup_retry_count = 0;

        if (uptime_secs >= STABLE_UPTIME_RESET_SECS) {
            restart_count = 0;
        }

        restart_count += 1;
        if (restart_count > max_restarts) {
            LOGE("fatal: shizuku_server crashed %d times consecutively, giving up", restart_count);
            exit(EXIT_FATAL_APP_PROCESS);
        }

        LOGW("restarting shizuku_server in %u seconds (attempt %d/%d)...",
             restart_delay_secs, restart_count, max_restarts);
        sleep(restart_delay_secs);
    }
}

[[noreturn]] static void start_server(const char *path, const char *main_class, const char *process_name) {
    int pipe_fds[2] = {-1, -1};
    if (pipe(pipe_fds) != 0) {
        pipe_fds[0] = -1;
        pipe_fds[1] = -1;
    }

    pid_t supervisor_pid = fork();
    if (supervisor_pid == -1) {
        if (pipe_fds[0] != -1) {
            close(pipe_fds[0]);
            close(pipe_fds[1]);
        }
        perrorf("fatal: can't fork\n");
        exit(EXIT_FATAL_FORK);
    } else if (supervisor_pid == 0) {
        if (pipe_fds[0] != -1) {
            close(pipe_fds[0]);
        }
        setsid();
        chdir("/");
        supervise_server(path, main_class, process_name, pipe_fds[1]);
    }

    if (pipe_fds[1] != -1) {
        close(pipe_fds[1]);
    }

    pid_t server_pid = supervisor_pid;
    if (pipe_fds[0] != -1) {
        pid_t reported_pid = 0;
        ssize_t read_bytes = read(pipe_fds[0], &reported_pid, sizeof(reported_pid));
        close(pipe_fds[0]);
        if (read_bytes == (ssize_t) sizeof(reported_pid)) {
            server_pid = reported_pid;
        } else {
            perrorf("fatal: failed to spawn shizuku_server\n");
            exit(EXIT_FATAL_FORK);
        }
    }

    printf("info: shizuku_server pid is %d\n", server_pid);
    printf("info: shizuku_starter exit with 0\n");
    fflush(stdout);
    exit(EXIT_SUCCESS);
}

static int check_selinux(const char *s, const char *t, const char *c, const char *p) {
    int res = se::selinux_check_access(s, t, c, p, nullptr);
#ifndef DEBUG
    if (res != 0) {
#endif
    printf("info: selinux_check_access %s %s %s %s: %d\n", s, t, c, p, res);
    fflush(stdout);
#ifndef DEBUG
    }
#endif
    return res;
}

static int switch_cgroup() {
    int pid = getpid();
    if (cgroup::switch_cgroup("/acct", pid)) {
        printf("info: switch cgroup succeeded, cgroup in /acct\n");
        return 0;
    }
    if (cgroup::switch_cgroup("/dev/cg2_bpf", pid)) {
        printf("info: switch cgroup succeeded, cgroup in /dev/cg2_bpf\n");
        return 0;
    }
    if (cgroup::switch_cgroup("/sys/fs/cgroup", pid)) {
        printf("info: switch cgroup succeeded, cgroup in /sys/fs/cgroup\n");
        return 0;
    }
    char buf[PROP_VALUE_MAX + 1];
    if (__system_property_get("ro.config.per_app_memcg", buf) > 0 &&
        strncmp(buf, "false", 5) != 0) {
        if (cgroup::switch_cgroup("/dev/memcg/apps", pid)) {
            printf("info: switch cgroup succeeded, cgroup in /dev/memcg/apps\n");
            return 0;
        }
    }
    printf("warn: can't switch cgroup\n");
    fflush(stdout);
    return -1;
}

static char s_self_exe[PATH_MAX]{0};
static ssize_t s_self_exe_len = 0;

static void kill_old_processes() {
    s_self_exe_len = readlink("/proc/self/exe", s_self_exe, sizeof(s_self_exe) - 1);
    if (s_self_exe_len > 0) {
        s_self_exe[s_self_exe_len] = '\0';
    }

    foreach_proc([](pid_t pid) {
        if (pid == getpid()) return;

        char name[1024]{0};
        if (get_proc_name(pid, name, sizeof(name)) != 0) return;

        bool is_server = (strcmp(SERVER_NAME, name) == 0);
        bool is_old_supervisor = false;
        if (!is_server && s_self_exe_len > 0) {
            char proc_exe_path[64]{0};
            snprintf(proc_exe_path, sizeof(proc_exe_path), "/proc/%d/exe", pid);
            char exe_buf[PATH_MAX]{0};
            ssize_t len = readlink(proc_exe_path, exe_buf, sizeof(exe_buf) - 1);
            if (len == s_self_exe_len && memcmp(exe_buf, s_self_exe, (size_t) len) == 0) {
                is_old_supervisor = true;
            }
        }

        if (!is_server && !is_old_supervisor) return;

        if (kill(pid, SIGKILL) == 0) {
            printf("info: killed %d (%s)\n", pid, name);
        } else if (errno == EPERM) {
            printf("warn: can't kill %d (EPERM), ignoring potential fake process.\n", pid);
        } else {
            printf("warn: failed to kill %d (%s)\n", pid, name);
        }
    });
}

int main(int argc, char *argv[]) {
    signal(SIGPIPE, SIG_IGN);
    signal(SIGHUP, SIG_IGN);

    std::string apk_path;
    for (int i = 0; i < argc; ++i) {
        if (strncmp(argv[i], "--apk=", 6) == 0) {
            apk_path = argv[i] + 6;
        }
    }

    uid_t uid = getuid();
    if (uid != 0 && uid != 2000) {
        perrorf("fatal: run Shizuku from non root nor adb user (uid=%d).\n", uid);
        exit(EXIT_FATAL_UID);
    }

    se::init();

    if (uid == 0) {
        switch_cgroup();

        if (android_get_device_api_level() >= 29) {
            printf("info: switching mount namespace to init...\n");
            switch_mnt_ns(1);
        }
    }

    if (uid == 0) {
        char *context = nullptr;
        if (se::getcon(&context) == 0) {
            int res = 0;

            res |= check_selinux("u:r:untrusted_app:s0", context, "binder", "call");
            res |= check_selinux("u:r:untrusted_app:s0", context, "binder", "transfer");

            if (res != 0) {
                perrorf("fatal: the su you are using does not allow app (u:r:untrusted_app:s0) to connect to su (%s) with binder.\n",
                        context);
                exit(EXIT_FATAL_BINDER_BLOCKED_BY_SELINUX);
            }
            se::freecon(context);
        }
    }

    printf("info: starter begin\n");
    fflush(stdout);

    printf("info: killing old process...\n");
    fflush(stdout);

    kill_old_processes();

    if (access(apk_path.c_str(), R_OK) == 0) {
        printf("info: use apk path from argv\n");
        fflush(stdout);
    }

    if (apk_path.empty()) {
        auto f = popen("pm path " PACKAGE_NAME, "r");
        if (f) {
            char line[PATH_MAX]{0};
            fgets(line, PATH_MAX, f);
            trim(line);
            if (strstr(line, "package:") == line) {
                apk_path = line + strlen("package:");
            }
            pclose(f);
        }
    }

    if (apk_path.empty()) {
        perrorf("fatal: can't get path of manager\n");
        exit(EXIT_FATAL_PM_PATH);
    }

    printf("info: apk path is %s\n", apk_path.c_str());
    if (access(apk_path.c_str(), R_OK) != 0) {
        perrorf("fatal: can't access manager %s\n", apk_path.c_str());
        exit(EXIT_FATAL_PM_PATH);
    }

    printf("info: starting server...\n");
    fflush(stdout);
    LOGD("start_server");
    start_server(apk_path.c_str(), SERVER_CLASS_PATH, SERVER_NAME);
}
