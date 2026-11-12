use std::ffi::CString;
use std::fs;
use std::io::Write;

const PROP_VALUE_MAX: usize = 92;

#[cfg(target_os = "android")]
extern "C" {
    fn __system_property_get(name: *const libc::c_char, value: *mut libc::c_char) -> libc::c_int;
}

#[cfg(not(target_os = "android"))]
unsafe fn __system_property_get(_name: *const libc::c_char, _value: *mut libc::c_char) -> libc::c_int {
    0
}

pub fn get_api_level() -> i32 {
    let key = CString::new("ro.build.version.sdk").unwrap();
    let mut buf = [0u8; PROP_VALUE_MAX + 1];
    // Safety: Passing valid key and appropriately-sized buffer to __system_property_get.
    // Invariant: The buffer length is at least PROP_VALUE_MAX + 1 bytes.
    let len = unsafe {
        __system_property_get(key.as_ptr(), buf.as_mut_ptr() as *mut libc::c_char)
    };
    if len > 0 {
        if let Ok(s) = std::str::from_utf8(&buf[..len as usize]) {
            return s.trim().parse::<i32>().unwrap_or(0);
        }
    }
    0
}

pub fn detach_stdio_to_dev_null() {
    let dev_null = CString::new("/dev/null").unwrap();
    // Safety: Open /dev/null and duplicate descriptor onto stdin (0), stdout (1), stderr (2).
    // Invariant: Fd is safely closed if opened above standard descriptor limit (fd > 2).
    unsafe {
        let fd = libc::open(dev_null.as_ptr(), libc::O_RDWR);
        if fd != -1 {
            libc::dup2(fd, libc::STDIN_FILENO);
            libc::dup2(fd, libc::STDOUT_FILENO);
            libc::dup2(fd, libc::STDERR_FILENO);
            if fd > 2 {
                libc::close(fd);
            }
        }
    }
}

pub fn switch_mnt_ns(pid: libc::pid_t) -> i32 {
    let mnt_path = format!("/proc/{}/ns/mnt\0", pid);
    // Safety: Opening mount namespace file and invoking setns syscall.
    // Invariant: File descriptor is validated before setns and closed afterwards.
    unsafe {
        let fd = libc::open(mnt_path.as_ptr() as *const libc::c_char, libc::O_RDONLY);
        if fd < 0 {
            return -1;
        }
        let res = libc::setns(fd, 0);
        libc::close(fd);
        res
    }
}

pub fn kill_old_processes(server_name: &str) {
    // Safety: getpid has no preconditions or side effects.
    let my_pid = unsafe { libc::getpid() };

    let entries = match fs::read_dir("/proc") {
        Ok(e) => e,
        Err(_) => return,
    };

    for entry in entries.flatten() {
        let file_name = entry.file_name();
        let name_str = file_name.to_string_lossy();
        let pid: libc::pid_t = match name_str.parse() {
            Ok(p) => p,
            Err(_) => continue,
        };

        if pid == my_pid {
            continue;
        }

        let cmdline_path = format!("/proc/{}/cmdline", pid);
        let cmdline_bytes = fs::read(&cmdline_path).unwrap_or_default();
        let first_arg = cmdline_bytes.split(|&b| b == 0).next().unwrap_or(&[]);
        let proc_name_cow = String::from_utf8_lossy(first_arg);
        let proc_name = proc_name_cow.trim();

        let is_server = proc_name == server_name;
        let mut is_old_supervisor = proc_name.contains("libshizuku.so");

        if !is_server && !is_old_supervisor {
            let exe_path = format!("/proc/{}/exe", pid);
            if let Ok(target) = fs::read_link(exe_path) {
                if target.to_string_lossy().contains("libshizuku.so") {
                    is_old_supervisor = true;
                }
            }
        }

        if !is_server && !is_old_supervisor {
            continue;
        }

        // Safety: Sending SIGKILL to identified stale Shizuku server or supervisor process.
        // Invariant: Process PID is positive and verified to not be our own PID.
        let kill_res = unsafe { libc::kill(pid, libc::SIGKILL) };
        if kill_res == 0 {
            println!("info: killed {} ({})", pid, proc_name);
        } else {
            let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
            if err == libc::EPERM {
                println!(
                    "warn: can't kill {} (EPERM), ignoring potential fake process.",
                    pid
                );
            } else {
                println!("warn: failed to kill {} ({})", pid, proc_name);
            }
        }
    }
    let _ = std::io::stdout().flush();
}
