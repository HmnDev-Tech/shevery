mod host;
mod pts;
mod terminal;

use jni::objects::JByteArray;
use jni::sys::{jbyte, jint, jintArray, jlong, JNIEnv, JNINativeMethod, JNI_ERR, JNI_VERSION_1_6};
use std::ffi::CString;
use std::os::raw::c_void;

#[no_mangle]
pub unsafe extern "C" fn jni_RishTerminal_prepare(
    _env: *mut JNIEnv,
    _class: jni::sys::jclass,
) -> jbyte {
    terminal::prepare()
}

#[no_mangle]
pub unsafe extern "C" fn jni_RishTerminal_start(
    _env: *mut JNIEnv,
    _class: jni::sys::jclass,
    tty: jbyte,
    stdin_pipe: jint,
    stdout_pipe: jint,
    stderr_pipe: jint,
) -> jint {
    terminal::start(tty, stdin_pipe, stdout_pipe, stderr_pipe)
}

#[no_mangle]
pub unsafe extern "C" fn jni_RishTerminal_waitForWindowSizeChange(
    _env: *mut JNIEnv,
    _class: jni::sys::jclass,
    fd: jint,
) -> jlong {
    terminal::wait_for_window_size_change(fd)
}

#[no_mangle]
pub unsafe extern "C" fn jni_RishTerminal_waitForProcessExit(
    _env: *mut JNIEnv,
    _class: jni::sys::jclass,
) {
    terminal::wait_for_process_exit();
}

#[no_mangle]
pub unsafe extern "C" fn jni_RishHost_start(
    raw_env: *mut JNIEnv,
    _class: jni::sys::jclass,
    arg_block: jni::sys::jbyteArray,
    argc: jint,
    env_block: jni::sys::jbyteArray,
    envc: jint,
    dir_block: jni::sys::jbyteArray,
    tty: jbyte,
    stdin_read: jint,
    stdout_write: jint,
    stderr_write: jint,
) -> jintArray {
    let env = jni::JNIEnv::from_raw(raw_env as *mut _).unwrap();
    let arg_b = JByteArray::from_raw(arg_block);
    let env_b = JByteArray::from_raw(env_block);
    let dir_b = JByteArray::from_raw(dir_block);

    host::start_host(
        env,
        arg_b,
        argc,
        env_b,
        envc,
        dir_b,
        tty,
        stdin_read,
        stdout_write,
        stderr_write,
    )
}

#[no_mangle]
pub unsafe extern "C" fn jni_RishHost_setWindowSize(
    _env: *mut JNIEnv,
    _class: jni::sys::jclass,
    ptmx: jint,
    size: jlong,
) {
    host::set_window_size(ptmx, size);
}

#[no_mangle]
pub unsafe extern "C" fn jni_RishHost_waitFor(
    _env: *mut JNIEnv,
    _class: jni::sys::jclass,
    pid: jint,
) -> jint {
    host::wait_for(pid)
}

#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: *mut jni::sys::JavaVM, _reserved: *mut c_void) -> jint {
    // Safety: Initializing JNIEnv pointer from JavaVM.
    // Invariant: vm pointer is validated and JNI_VERSION_1_6 requested.
    unsafe {
        let mut env_ptr: *mut c_void = std::ptr::null_mut();
        if (**vm).GetEnv.unwrap()(vm, &mut env_ptr, JNI_VERSION_1_6) != 0 {
            return JNI_ERR;
        }
        let env = env_ptr as *mut JNIEnv;

        // Register rikka/rish/RishTerminal
        let terminal_class_name = CString::new("rikka/rish/RishTerminal").unwrap();
        let terminal_class = (**env).FindClass.unwrap()(env, terminal_class_name.as_ptr());
        if terminal_class.is_null() {
            return JNI_ERR;
        }

        let terminal_methods = [
            JNINativeMethod {
                name: b"prepare\0".as_ptr() as *mut _,
                signature: b"()B\0".as_ptr() as *mut _,
                fnPtr: jni_RishTerminal_prepare as *mut _,
            },
            JNINativeMethod {
                name: b"start\0".as_ptr() as *mut _,
                signature: b"(BIII)I\0".as_ptr() as *mut _,
                fnPtr: jni_RishTerminal_start as *mut _,
            },
            JNINativeMethod {
                name: b"waitForWindowSizeChange\0".as_ptr() as *mut _,
                signature: b"(I)J\0".as_ptr() as *mut _,
                fnPtr: jni_RishTerminal_waitForWindowSizeChange as *mut _,
            },
            JNINativeMethod {
                name: b"waitForProcessExit\0".as_ptr() as *mut _,
                signature: b"()V\0".as_ptr() as *mut _,
                fnPtr: jni_RishTerminal_waitForProcessExit as *mut _,
            },
        ];

        if (**env).RegisterNatives.unwrap()(
            env,
            terminal_class,
            terminal_methods.as_ptr(),
            terminal_methods.len() as jint,
        ) != 0
        {
            return JNI_ERR;
        }

        // Register rikka/rish/RishHost
        let host_class_name = CString::new("rikka/rish/RishHost").unwrap();
        let host_class = (**env).FindClass.unwrap()(env, host_class_name.as_ptr());
        if host_class.is_null() {
            return JNI_ERR;
        }

        let host_methods = [
            JNINativeMethod {
                name: b"start\0".as_ptr() as *mut _,
                signature: b"([BI[BI[BBIII)[I\0".as_ptr() as *mut _,
                fnPtr: jni_RishHost_start as *mut _,
            },
            JNINativeMethod {
                name: b"setWindowSize\0".as_ptr() as *mut _,
                signature: b"(IJ)V\0".as_ptr() as *mut _,
                fnPtr: jni_RishHost_setWindowSize as *mut _,
            },
            JNINativeMethod {
                name: b"waitFor\0".as_ptr() as *mut _,
                signature: b"(I)I\0".as_ptr() as *mut _,
                fnPtr: jni_RishHost_waitFor as *mut _,
            },
        ];

        if (**env).RegisterNatives.unwrap()(
            env,
            host_class,
            host_methods.as_ptr(),
            host_methods.len() as jint,
        ) != 0
        {
            return JNI_ERR;
        }

        JNI_VERSION_1_6
    }
}
