use crate::pts::*;
use jni::objects::JByteArray;
use jni::sys::{jbyte, jint, jintArray, jlong};
use jni::JNIEnv;
use std::ffi::CString;
use std::os::raw::c_int;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

fn kill_child(pid: libc::pid_t) {
    // Safety: Sending SIGKILL to child process and its process group.
    // Invariant: pid is guaranteed to be a valid positive child process ID.
    unsafe {
        let mut info: libc::siginfo_t = std::mem::zeroed();
        let unreaped = libc::waitid(
            libc::P_PID,
            pid as libc::id_t,
            &mut info,
            libc::WEXITED | libc::WNOHANG | libc::WNOWAIT,
        ) == 0;
        libc::kill(-pid, libc::SIGKILL);
        if unreaped {
            libc::kill(pid, libc::SIGKILL);
        }
    }
}

fn parse_block(data: &[u8], count: usize) -> Vec<CString> {
    let mut result = Vec::with_capacity(count);
    let mut start = 0;
    while start < data.len() && result.len() < count {
        if let Some(end) = data[start..].iter().position(|&b| b == 0) {
            let slice = &data[start..start + end];
            if let Ok(c) = CString::new(slice) {
                result.push(c);
            }
            start += end + 1;
        } else {
            break;
        }
    }
    result
}

pub fn start_host(
    mut env: JNIEnv,
    arg_block: JByteArray,
    argc: jint,
    env_block: JByteArray,
    envc: jint,
    dir_block: JByteArray,
    tty: jbyte,
    stdin_read: jint,
    stdout_write: jint,
    stderr_write: jint,
) -> jintArray {
    let tty = tty as u8;
    let in_tty = (tty & ATTY_IN) != 0;
    let out_tty = (tty & ATTY_OUT) != 0;
    let err_tty = (tty & ATTY_ERR) != 0;

    let ptmx = if tty != 0 {
        let fd = open_ptmx();
        if fd == -1 {
            let _ = env.throw_new("java/lang/IllegalStateException", "Unable to open ptmx");
            return std::ptr::null_mut();
        }
        fd
    } else {
        -1
    };

    let mut stdin_pipe = [-1; 2];
    let mut stdout_pipe = [-1; 2];
    let mut stderr_pipe = [-1; 2];

    // Safety: Creating pipe2 descriptors with flag 0 as needed for non-tty pipes.
    unsafe {
        if !in_tty {
            libc::pipe2(stdin_pipe.as_mut_ptr(), 0);
        }
        if !out_tty {
            libc::pipe2(stdout_pipe.as_mut_ptr(), 0);
        }
        if !err_tty {
            libc::pipe2(stderr_pipe.as_mut_ptr(), 0);
        }
    }

    let arg_bytes = match env.convert_byte_array(&arg_block) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };
    let parsed_args = parse_block(&arg_bytes, argc as usize);

    let parsed_envs = if envc > 0 && !env_block.is_null() {
        match env.convert_byte_array(&env_block) {
            Ok(b) => Some(parse_block(&b, envc as usize)),
            Err(_) => None,
        }
    } else {
        None
    };

    let dir_str = if !dir_block.is_null() {
        env.convert_byte_array(&dir_block)
            .ok()
            .and_then(|b| CString::new(b).ok())
    } else {
        None
    };

    // Safety: Forking child process for executing shell command.
    let pid = unsafe { libc::fork() };
    if pid == -1 {
        unsafe {
            if ptmx != -1 {
                libc::close(ptmx);
            }
            if stdin_pipe[0] != -1 {
                libc::close(stdin_pipe[0]);
                libc::close(stdin_pipe[1]);
            }
            if stdout_pipe[0] != -1 {
                libc::close(stdout_pipe[0]);
                libc::close(stdout_pipe[1]);
            }
            if stderr_pipe[0] != -1 {
                libc::close(stderr_pipe[0]);
                libc::close(stderr_pipe[1]);
            }
            if stdin_read != -1 {
                libc::close(stdin_read);
            }
            if stdout_write != -1 {
                libc::close(stdout_write);
            }
            if stderr_write != -1 {
                libc::close(stderr_write);
            }
        }
        let _ = env.throw_new("java/lang/IllegalStateException", "Unable to fork");
        return std::ptr::null_mut();
    }

    if pid > 0 {
        // Parent process
        let called = Arc::new(AtomicBool::new(false));
        let c_called = called.clone();
        let on_client_dead = move |failed: bool| {
            if failed && !c_called.swap(true, Ordering::SeqCst) {
                kill_child(pid);
            }
        };

        // Safety: Configuring FD_CLOEXEC on host-side pipes.
        unsafe {
            if !in_tty {
                libc::fcntl(stdin_pipe[1], libc::F_SETFD, libc::FD_CLOEXEC);
            }
            if !out_tty {
                libc::fcntl(stdout_pipe[0], libc::F_SETFD, libc::FD_CLOEXEC);
            }
            if !err_tty {
                libc::fcntl(stderr_pipe[0], libc::F_SETFD, libc::FD_CLOEXEC);
            }
        }

        let dup_pty = move || -> c_int {
            // Safety: Duplicating ptmx descriptor with F_DUPFD_CLOEXEC.
            let fd = unsafe { libc::fcntl(ptmx, libc::F_DUPFD_CLOEXEC, 0) };
            if fd == -1 {
                kill_child(pid);
            }
            fd
        };

        if in_tty {
            let fd = dup_pty();
            if fd != -1 {
                transfer_async(stdin_read, fd, true, true, false, None::<fn(bool)>);
            } else {
                unsafe { libc::close(stdin_read) };
            }
        } else {
            transfer_async(
                stdin_read,
                stdin_pipe[1],
                true,
                true,
                false,
                None::<fn(bool)>,
            );
            unsafe { libc::close(stdin_pipe[0]) };
        }

        if out_tty {
            let fd = dup_pty();
            if fd != -1 {
                let cb = on_client_dead.clone();
                transfer_async(fd, stdout_write, true, true, true, Some(cb));
            } else {
                unsafe { libc::close(stdout_write) };
            }
        } else {
            let cb = on_client_dead.clone();
            transfer_async(stdout_pipe[0], stdout_write, true, true, true, Some(cb));
            unsafe { libc::close(stdout_pipe[1]) };

            if in_tty || err_tty {
                let dev_null = CString::new("/dev/null").unwrap();
                // Safety: Opening /dev/null for draining master PTY when out is not a TTY.
                let null_fd = unsafe {
                    libc::open(dev_null.as_ptr(), libc::O_WRONLY | libc::O_CLOEXEC)
                };
                if null_fd != -1 {
                    let fd = dup_pty();
                    if fd != -1 {
                        let c = called.clone();
                        transfer_async(
                            fd,
                            null_fd,
                            true,
                            true,
                            false,
                            Some(move |failed| {
                                if failed && !c.swap(true, Ordering::SeqCst) {
                                    kill_child(pid);
                                }
                            }),
                        );
                    } else {
                        unsafe { libc::close(null_fd) };
                    }
                }
            }
        }

        if !err_tty {
            transfer_async(
                stderr_pipe[0],
                stderr_write,
                true,
                true,
                false,
                None::<fn(bool)>,
            );
            unsafe { libc::close(stderr_pipe[1]) };
        }

        match env.new_int_array(2) {
            Ok(res_array) => {
                let result = [pid, ptmx];
                let _ = env.set_int_array_region(&res_array, 0, &result);
                res_array.into_raw()
            }
            Err(_) => std::ptr::null_mut(),
        }
    } else {
        // Child process
        // Safety: Creating a new session, changing working directory, opening slave pts, dup2 into stdio, execvp.
        unsafe {
            if libc::setsid() < 0 {
                libc::exit(1);
            }

            if let Some(dir) = dir_str {
                if libc::access(dir.as_ptr(), libc::X_OK) == 0 {
                    let _ = libc::chdir(dir.as_ptr());
                }
            }

            let mut pts = -1;
            if tty != 0 {
                let mut pts_slave = [0u8; libc::PATH_MAX as usize];
                if libc::ptsname_r(
                    ptmx,
                    pts_slave.as_mut_ptr() as *mut libc::c_char,
                    pts_slave.len() - 1,
                ) == -1
                {
                    libc::exit(1);
                }
                pts = libc::open(pts_slave.as_ptr() as *const libc::c_char, libc::O_RDWR);
            }

            if in_tty {
                libc::dup2(pts, libc::STDIN_FILENO);
            } else {
                libc::dup2(stdin_pipe[0], libc::STDIN_FILENO);
                libc::close(stdin_pipe[1]);
            }

            if out_tty {
                libc::dup2(pts, libc::STDOUT_FILENO);
            } else {
                libc::dup2(stdout_pipe[1], libc::STDOUT_FILENO);
                libc::close(stdout_pipe[0]);
            }

            if err_tty {
                libc::dup2(pts, libc::STDERR_FILENO);
            } else {
                libc::dup2(stderr_pipe[1], libc::STDERR_FILENO);
                libc::close(stderr_pipe[0]);
            }

            if pts != -1 {
                libc::close(pts);
            }

            let sh = CString::new("/system/bin/sh").unwrap();
            let mut argv_c: Vec<*const libc::c_char> = Vec::with_capacity(parsed_args.len() + 2);
            argv_c.push(sh.as_ptr());
            for arg in &parsed_args {
                argv_c.push(arg.as_ptr());
            }
            argv_c.push(std::ptr::null());

            if let Some(envs) = parsed_envs {
                let mut envv_c: Vec<*const libc::c_char> = Vec::with_capacity(envs.len() + 1);
                for ev in &envs {
                    envv_c.push(ev.as_ptr());
                }
                envv_c.push(std::ptr::null());
                libc::execvpe(sh.as_ptr(), argv_c.as_ptr(), envv_c.as_ptr());
            } else {
                libc::execvp(sh.as_ptr(), argv_c.as_ptr());
            }
            libc::exit(1);
        }
    }
}

pub fn set_window_size(ptmx: jint, size: jlong) {
    // Safety: Transmuting jlong to winsize and invoking ioctl TIOCSWINSZ.
    // Invariant: winsize is 8 bytes identical to jlong layout.
    unsafe {
        let ws: libc::winsize = std::mem::transmute(size);
        let _ = libc::ioctl(ptmx, libc::TIOCSWINSZ, &ws);
    }
}

pub fn wait_for(pid: jint) -> jint {
    let mut status: libc::c_int = 0;
    loop {
        // Safety: Waiting for specified child PID to terminate.
        let ret = unsafe { libc::waitpid(pid, &mut status, 0) };
        if ret != -1 {
            if libc::WIFEXITED(status) {
                return libc::WEXITSTATUS(status);
            }
            if libc::WIFSIGNALED(status) {
                return 128 + libc::WTERMSIG(status);
            }
        } else {
            let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
            if err == libc::EINTR {
                continue;
            }
            if err == libc::ECHILD {
                return 0;
            }
            return -1;
        }
    }
}
