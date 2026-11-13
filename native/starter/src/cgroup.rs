use std::ffi::CString;
use std::io::Write;
use std::path::Path;

fn switch_cgroup_dir(cgroup: &str, pid: libc::pid_t) -> bool {
    let procs_path = format!("{}/cgroup.procs", cgroup);
    if !Path::new(&procs_path).exists() {
        return false;
    }

    if let Ok(mut file) = std::fs::OpenOptions::new()
        .write(true)
        .append(true)
        .open(&procs_path)
    {
        return writeln!(file, "{}", pid).is_ok();
    }
    false
}

const PROP_VALUE_MAX: usize = 92;

#[cfg(target_os = "android")]
extern "C" {
    fn __system_property_get(name: *const libc::c_char, value: *mut libc::c_char) -> libc::c_int;
}

#[cfg(not(target_os = "android"))]
unsafe fn __system_property_get(_name: *const libc::c_char, _value: *mut libc::c_char) -> libc::c_int {
    0
}

pub fn switch_cgroup() -> i32 {
    // Safety: getpid is a reentrant, always-safe POSIX syscall without parameters.
    let pid = unsafe { libc::getpid() };

    for dir in &["/acct", "/dev/cg2_bpf", "/sys/fs/cgroup"] {
        if switch_cgroup_dir(dir, pid) {
            println!("info: switch cgroup succeeded, cgroup in {}", dir);
            let _ = std::io::stdout().flush();
            return 0;
        }
    }

    let key = CString::new("ro.config.per_app_memcg").unwrap();
    let mut buf = [0u8; PROP_VALUE_MAX + 1];
    // Safety: Passing valid null-terminated key and appropriately-sized buffer to __system_property_get.
    // Invariant: The buffer length is at least PROP_VALUE_MAX + 1 bytes.
    let len = unsafe {
        __system_property_get(key.as_ptr(), buf.as_mut_ptr() as *mut libc::c_char)
    };
    if len > 0 {
        if let Ok(val) = std::str::from_utf8(&buf[..len as usize]) {
            if !val.starts_with("false") && switch_cgroup_dir("/dev/memcg/apps", pid) {
                println!("info: switch cgroup succeeded, cgroup in /dev/memcg/apps");
                let _ = std::io::stdout().flush();
                return 0;
            }
        }
    }

    println!("warn: can't switch cgroup");
    let _ = std::io::stdout().flush();
    -1
}
