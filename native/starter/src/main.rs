mod cgroup;
mod misc;
mod selinux;

use std::io::Write;
use std::path::Path;
use std::process::Command;

const EXIT_SUCCESS: i32 = 0;
const EXIT_FATAL_SET_CLASSPATH: i32 = 3;
const EXIT_FATAL_FORK: i32 = 4;
const EXIT_FATAL_APP_PROCESS: i32 = 5;
const EXIT_FATAL_UID: i32 = 6;
const EXIT_FATAL_PM_PATH: i32 = 7;
const EXIT_FATAL_BINDER_BLOCKED_BY_SELINUX: i32 = 10;
const EXIT_MANAGER_APP_NOT_FOUND: i32 = 50;
const EXIT_MANAGER_APP_LOOKUP_UNAVAILABLE: i32 = 51;
const STABLE_UPTIME_RESET_SECS: u64 = 60;

const PACKAGE_NAME: &str = "com.hamondev.shevery";
const SERVER_NAME: &str = "shizuku_server";
const SERVER_CLASS_PATH: &str = "rikka.shizuku.server.ShizukuService";

#[cfg(target_arch = "arm")]
const ABI: &str = "arm";
#[cfg(target_arch = "x86")]
const ABI: &str = "x86";
#[cfg(target_arch = "x86_64")]
const ABI: &str = "x86_64";
#[cfg(target_arch = "aarch64")]
const ABI: &str = "arm64";

fn run_server(dex_path: &str, main_class: &str, process_name: &str) -> ! {
    // Safety: Resetting SIGPIPE and SIGHUP to default behavior for the new server process.
    // Invariant: SIG_DFL is the valid signal handler setting for default disposition.
    unsafe {
        libc::signal(libc::SIGPIPE, libc::SIG_DFL);
        libc::signal(libc::SIGHUP, libc::SIG_DFL);
    }

    let c_classpath = std::ffi::CString::new("CLASSPATH").unwrap();
    let c_dex_path = std::ffi::CString::new(dex_path).unwrap();
    // Safety: Setting CLASSPATH environment variable for runtime dex loading by app_process.
    // Invariant: Both strings are valid null-terminated C strings.
    unsafe {
        if libc::setenv(c_classpath.as_ptr(), c_dex_path.as_ptr(), 1) != 0 {
            eprintln!("can't set CLASSPATH");
            libc::exit(EXIT_FATAL_SET_CLASSPATH);
        }
    }

    let dex_parent = Path::new(dex_path)
        .parent()
        .and_then(|p| p.to_str())
        .unwrap_or(".");
    let lib_path = format!("{}/lib/{}", dex_parent, ABI);

    let args = vec![
        std::ffi::CString::new("/system/bin/app_process").unwrap(),
        std::ffi::CString::new(format!("-Djava.class.path={}", dex_path)).unwrap(),
        std::ffi::CString::new(format!("-Dshizuku.library.path={}", lib_path)).unwrap(),
        std::ffi::CString::new("/system/bin").unwrap(),
        std::ffi::CString::new(format!("--nice-name={}", process_name)).unwrap(),
        std::ffi::CString::new(main_class).unwrap(),
    ];

    let mut c_args: Vec<*const libc::c_char> = args.iter().map(|a| a.as_ptr()).collect();
    c_args.push(std::ptr::null());

    // Safety: Invoking execvp to replace current process image with Android's app_process.
    // Invariant: Argument array is null-terminated and pointers are valid for the call duration.
    unsafe {
        libc::execvp(c_args[0], c_args.as_ptr());
        libc::exit(EXIT_FATAL_APP_PROCESS);
    }
}

fn supervise_server(
    dex_path: &str,
    main_class: &str,
    process_name: &str,
    mut pipe_write_fd: libc::c_int,
) -> ! {
    let mut restart_count = 0;
    const MAX_RESTARTS: i32 = 5;
    let mut manager_lookup_retry_count = 0;
    const MAX_MANAGER_LOOKUP_RETRIES: i32 = 5;
    const RESTART_DELAY_SECS: u64 = 5;

    loop {
        // Safety: Forking child process for shizuku_server.
        // Invariant: On success, fork produces parent (pid > 0) and child (pid == 0).
        let pid = unsafe { libc::fork() };
        if pid == -1 {
            eprintln!("fatal: can't fork shizuku_server");
            if pipe_write_fd != -1 {
                unsafe { libc::close(pipe_write_fd) };
            }
            unsafe { libc::exit(EXIT_FATAL_FORK) };
        } else if pid == 0 {
            if pipe_write_fd != -1 {
                unsafe { libc::close(pipe_write_fd) };
            }
            // Safety: Creating new session and changing to root directory.
            unsafe {
                libc::setsid();
                let root = std::ffi::CString::new("/").unwrap();
                libc::chdir(root.as_ptr());
            }
            misc::detach_stdio_to_dev_null();
            run_server(dex_path, main_class, process_name);
        }

        let started_at = std::time::Instant::now();

        if pipe_write_fd != -1 {
            // Safety: Writing the child PID to the parent starter through pipe.
            unsafe {
                let _ = libc::write(
                    pipe_write_fd,
                    &pid as *const _ as *const libc::c_void,
                    std::mem::size_of::<libc::pid_t>(),
                );
                libc::close(pipe_write_fd);
            }
            pipe_write_fd = -1;
            misc::detach_stdio_to_dev_null();
        }

        println!("shizuku_server pid is {}", pid);
        let _ = std::io::stdout().flush();

        let mut status: libc::c_int = 0;
        loop {
            // Safety: Waiting for child server process to exit or fail.
            // Invariant: status pointer is valid and pid is positive.
            let res = unsafe { libc::waitpid(pid, &mut status, 0) };
            if res == -1 {
                let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                if err == libc::EINTR {
                    continue;
                }
            }
            break;
        }

        let uptime_secs = started_at.elapsed().as_secs();

        let mut exit_code = -1;
        let mut killed_by_signal = false;
        if libc::WIFEXITED(status) {
            exit_code = libc::WEXITSTATUS(status);
        } else if libc::WIFSIGNALED(status) {
            exit_code = 128 + libc::WTERMSIG(status);
            killed_by_signal = true;
        }

        println!(
            "shizuku_server exited with code {} after {}s",
            exit_code, uptime_secs
        );
        let _ = std::io::stdout().flush();

        if !killed_by_signal && exit_code == 0 {
            println!("shizuku_server exited cleanly, no restart");
            unsafe { libc::exit(0) };
        }

        if !killed_by_signal && exit_code == EXIT_MANAGER_APP_NOT_FOUND {
            println!("manager application not found, stopping supervisor without restart");
            unsafe { libc::exit(0) };
        }

        if !killed_by_signal && exit_code == EXIT_MANAGER_APP_LOOKUP_UNAVAILABLE {
            if manager_lookup_retry_count >= MAX_MANAGER_LOOKUP_RETRIES {
                eprintln!(
                    "fatal: manager package lookup unavailable after {} retries, giving up",
                    manager_lookup_retry_count
                );
                unsafe { libc::exit(EXIT_FATAL_APP_PROCESS) };
            }
            manager_lookup_retry_count += 1;
            println!(
                "manager package lookup unavailable, retrying ({}/{})",
                manager_lookup_retry_count, MAX_MANAGER_LOOKUP_RETRIES
            );
            let _ = std::io::stdout().flush();
            std::thread::sleep(std::time::Duration::from_secs(RESTART_DELAY_SECS));
            continue;
        }

        manager_lookup_retry_count = 0;

        if uptime_secs >= STABLE_UPTIME_RESET_SECS {
            restart_count = 0;
        }

        restart_count += 1;
        if restart_count > MAX_RESTARTS {
            eprintln!(
                "fatal: shizuku_server crashed {} times consecutively, giving up",
                restart_count
            );
            unsafe { libc::exit(EXIT_FATAL_APP_PROCESS) };
        }

        println!(
            "restarting shizuku_server in {} seconds (attempt {}/{})...",
            RESTART_DELAY_SECS, restart_count, MAX_RESTARTS
        );
        let _ = std::io::stdout().flush();
        std::thread::sleep(std::time::Duration::from_secs(RESTART_DELAY_SECS));
    }
}

fn start_server(dex_path: &str, main_class: &str, process_name: &str) -> ! {
    let mut pipe_fds = [-1; 2];
    // Safety: Creating anonymous pipe for supervisor communication.
    unsafe {
        if libc::pipe(pipe_fds.as_mut_ptr()) != 0 {
            pipe_fds[0] = -1;
            pipe_fds[1] = -1;
        }
    }

    // Safety: Forking supervisor process.
    let supervisor_pid = unsafe { libc::fork() };
    if supervisor_pid == -1 {
        if pipe_fds[0] != -1 {
            unsafe {
                libc::close(pipe_fds[0]);
                libc::close(pipe_fds[1]);
            }
        }
        eprintln!("fatal: can't fork");
        unsafe { libc::exit(EXIT_FATAL_FORK) };
    } else if supervisor_pid == 0 {
        if pipe_fds[0] != -1 {
            unsafe { libc::close(pipe_fds[0]) };
        }
        unsafe {
            libc::setsid();
            let root = std::ffi::CString::new("/").unwrap();
            libc::chdir(root.as_ptr());
        }
        supervise_server(dex_path, main_class, process_name, pipe_fds[1]);
    }

    if pipe_fds[1] != -1 {
        unsafe { libc::close(pipe_fds[1]) };
    }

    let mut server_pid = supervisor_pid;
    if pipe_fds[0] != -1 {
        let mut reported_pid: libc::pid_t = 0;
        // Safety: Reading PID of spawned shizuku_server from supervisor.
        // Invariant: Buffer pointer is valid and sized for pid_t.
        let read_bytes = unsafe {
            libc::read(
                pipe_fds[0],
                &mut reported_pid as *mut _ as *mut libc::c_void,
                std::mem::size_of::<libc::pid_t>(),
            )
        };
        unsafe { libc::close(pipe_fds[0]) };
        if read_bytes == std::mem::size_of::<libc::pid_t>() as isize {
            server_pid = reported_pid;
        } else {
            eprintln!("fatal: failed to spawn shizuku_server");
            unsafe { libc::exit(EXIT_FATAL_FORK) };
        }
    }

    println!("info: shizuku_server pid is {}", server_pid);
    println!("info: shizuku_starter exit with 0");
    let _ = std::io::stdout().flush();
    unsafe { libc::exit(EXIT_SUCCESS) };
}

fn check_selinux(se: &selinux::SeLinux, s: &str, t: &str, c: &str, p: &str) -> i32 {
    let res = se.check_access(s, t, c, p);
    if res != 0 {
        println!("info: selinux_check_access {} {} {} {}: {}", s, t, c, p, res);
        let _ = std::io::stdout().flush();
    }
    res
}

fn main() {
    // Safety: Ignoring SIGPIPE and SIGHUP in the initial starter process.
    unsafe {
        libc::signal(libc::SIGPIPE, libc::SIG_IGN);
        libc::signal(libc::SIGHUP, libc::SIG_IGN);
    }

    let mut apk_path = String::new();
    for arg in std::env::args() {
        if let Some(path) = arg.strip_prefix("--apk=") {
            apk_path = path.to_string();
        }
    }

    // Safety: getuid is a safe POSIX syscall with no side effects.
    let uid = unsafe { libc::getuid() };
    if uid != 0 && uid != 2000 {
        eprintln!(
            "fatal: run Shizuku from non root nor adb user (uid={}).",
            uid
        );
        unsafe { libc::exit(EXIT_FATAL_UID) };
    }

    let se = selinux::SeLinux::init();

    if uid == 0 {
        cgroup::switch_cgroup();

        if misc::get_api_level() >= 29 {
            println!("info: switching mount namespace to init...");
            let _ = std::io::stdout().flush();
            misc::switch_mnt_ns(1);
        }

        if let Some(context) = se.get_context() {
            let mut res = 0;
            res |= check_selinux(&se, "u:r:untrusted_app:s0", &context, "binder", "call");
            res |= check_selinux(
                &se,
                "u:r:untrusted_app:s0",
                &context,
                "binder",
                "transfer",
            );

            if res != 0 {
                eprintln!(
                    "fatal: the su you are using does not allow app (u:r:untrusted_app:s0) to connect to su ({}) with binder.",
                    context
                );
                unsafe { libc::exit(EXIT_FATAL_BINDER_BLOCKED_BY_SELINUX) };
            }
        }
    }

    println!("info: starter begin");
    println!("info: killing old process...");
    let _ = std::io::stdout().flush();

    misc::kill_old_processes(SERVER_NAME);

    if !apk_path.is_empty() && Path::new(&apk_path).exists() {
        println!("info: use apk path from argv");
        let _ = std::io::stdout().flush();
    } else {
        apk_path.clear();
    }

    if apk_path.is_empty() {
        if let Ok(output) = Command::new("pm")
            .arg("path")
            .arg(PACKAGE_NAME)
            .output()
        {
            if let Ok(text) = std::str::from_utf8(&output.stdout) {
                for line in text.lines() {
                    let trimmed = line.trim();
                    if let Some(stripped) = trimmed.strip_prefix("package:") {
                        apk_path = stripped.trim().to_string();
                        break;
                    }
                }
            }
        }
    }

    if apk_path.is_empty() {
        eprintln!("fatal: can't get path of manager");
        unsafe { libc::exit(EXIT_FATAL_PM_PATH) };
    }

    println!("info: apk path is {}", apk_path);
    let _ = std::io::stdout().flush();

    if !Path::new(&apk_path).exists() {
        eprintln!("fatal: can't access manager {}", apk_path);
        unsafe { libc::exit(EXIT_FATAL_PM_PATH) };
    }

    println!("info: starting server...");
    let _ = std::io::stdout().flush();

    start_server(&apk_path, SERVER_CLASS_PATH, SERVER_NAME);
}
