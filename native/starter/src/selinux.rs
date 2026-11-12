use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int, c_void};

type GetConFn = unsafe extern "C" fn(*mut *mut c_char) -> c_int;
#[allow(dead_code)]
type SetConFn = unsafe extern "C" fn(*const c_char) -> c_int;
type FreeConFn = unsafe extern "C" fn(*mut c_char);
type CheckAccessFn = unsafe extern "C" fn(
    *const c_char,
    *const c_char,
    *const c_char,
    *const c_char,
    *mut c_void,
) -> c_int;

pub struct SeLinux {
    getcon_fn: Option<GetConFn>,
    freecon_fn: Option<FreeConFn>,
    check_access_fn: Option<CheckAccessFn>,
}

impl SeLinux {
    pub fn init() -> Self {
        // Safety: Dynamically opening libselinux.so if available on Android.
        // Invariant: If libselinux cannot be opened, we fall back to /proc/self/attr/current.
        unsafe {
            let lib_name = CString::new("libselinux.so").unwrap();
            let handle = libc::dlopen(lib_name.as_ptr(), libc::RTLD_LAZY | libc::RTLD_LOCAL);
            if handle.is_null() {
                return SeLinux {
                    getcon_fn: None,
                    freecon_fn: None,
                    check_access_fn: None,
                };
            }

            let getcon_sym = libc::dlsym(handle, b"getcon\0".as_ptr() as *const c_char);
            let freecon_sym = libc::dlsym(handle, b"freecon\0".as_ptr() as *const c_char);
            let check_access_sym =
                libc::dlsym(handle, b"selinux_check_access\0".as_ptr() as *const c_char);

            SeLinux {
                getcon_fn: if !getcon_sym.is_null() {
                    Some(std::mem::transmute(getcon_sym))
                } else {
                    None
                },
                freecon_fn: if !freecon_sym.is_null() {
                    Some(std::mem::transmute(freecon_sym))
                } else {
                    None
                },
                check_access_fn: if !check_access_sym.is_null() {
                    Some(std::mem::transmute(check_access_sym))
                } else {
                    None
                },
            }
        }
    }

    pub fn get_context(&self) -> Option<String> {
        if let (Some(getcon), Some(freecon)) = (self.getcon_fn, self.freecon_fn) {
            let mut ptr: *mut c_char = std::ptr::null_mut();
            // Safety: getcon allocates a C string in ptr, freecon frees it.
            // Invariant: ptr is validated for non-null before constructing CStr and freeing.
            unsafe {
                if getcon(&mut ptr) == 0 && !ptr.is_null() {
                    let s = CStr::from_ptr(ptr).to_string_lossy().into_owned();
                    freecon(ptr);
                    return Some(s);
                }
            }
        }

        // Fallback: read /proc/self/attr/current
        std::fs::read_to_string("/proc/self/attr/current")
            .ok()
            .map(|s| s.trim_matches('\0').trim().to_string())
            .filter(|s| !s.is_empty())
    }

    pub fn check_access(&self, scon: &str, tcon: &str, tclass: &str, perm: &str) -> i32 {
        if let Some(check_fn) = self.check_access_fn {
            let c_scon = CString::new(scon).unwrap();
            let c_tcon = CString::new(tcon).unwrap();
            let c_tclass = CString::new(tclass).unwrap();
            let c_perm = CString::new(perm).unwrap();
            // Safety: Passing valid null-terminated C strings to selinux_check_access.
            // Invariant: The auditdata pointer is null as per Android SELinux access checks.
            unsafe {
                check_fn(
                    c_scon.as_ptr(),
                    c_tcon.as_ptr(),
                    c_tclass.as_ptr(),
                    c_perm.as_ptr(),
                    std::ptr::null_mut(),
                )
            }
        } else {
            0
        }
    }
}
