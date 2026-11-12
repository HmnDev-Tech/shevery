use crate::pts::*;
use jni::sys::{jbyte, jint, jlong};
use std::os::raw::c_int;
use std::sync::atomic::{AtomicI32, Ordering};
use std::sync::{Arc, Condvar, Mutex, OnceLock};

static WINCH_PIPE: OnceLock<[c_int; 2]> = OnceLock::new();
static OLD_STDIN: Mutex<Option<libc::termios>> = Mutex::new(None);
static TTY_IN_RAW: AtomicI32 = AtomicI32::new(0);

struct ExitState {
    finished: bool,
}

static EXIT_LOCK: OnceLock<(Mutex<ExitState>, Condvar)> = OnceLock::new();

fn get_exit_lock() -> &'static (Mutex<ExitState>, Condvar) {
    EXIT_LOCK.get_or_init(|| (Mutex::new(ExitState { finished: true }), Condvar::new()))
}

extern "C" fn sigwinch_handler(_sig: c_int) {
    if let Some(pipe) = WINCH_PIPE.get() {
        let b = 1u8;
        // Safety: Async-signal-safe write of 1 byte to non-blocking pipe descriptor.
        // Invariant: Pipe descriptor was configured with O_NONBLOCK so it cannot block inside signal handler.
        unsafe {
            let _ = libc::write(pipe[1], &b as *const _ as *const libc::c_void, 1);
        }
    }
}

pub fn prepare() -> jbyte {
    let mut atty: u8 = 0;
    // Safety: isatty queries whether standard descriptors are terminal devices.
    unsafe {
        if libc::isatty(libc::STDIN_FILENO) != 0 {
            atty |= ATTY_IN;
        }
        if libc::isatty(libc::STDOUT_FILENO) != 0 {
            atty |= ATTY_OUT;
        }
        if libc::isatty(libc::STDERR_FILENO) != 0 {
            atty |= ATTY_ERR;
        }
    }

    if (atty & ATTY_OUT) != 0 {
        // Safety: Masking SIGWINCH on the calling thread until self-pipe and handler are installed in start().
        // Invariant: sigset_t structure is properly initialized with sigemptyset and sigaddset.
        unsafe {
            let mut winch: libc::sigset_t = std::mem::zeroed();
            libc::sigemptyset(&mut winch);
            libc::sigaddset(&mut winch, libc::SIGWINCH);
            libc::pthread_sigmask(libc::SIG_BLOCK, &winch, std::ptr::null_mut());
        }
    }

    atty as jbyte
}

pub fn start(tty: jbyte, stdin_pipe: jint, stdout_pipe: jint, stderr_pipe: jint) -> jint {
    let tty = tty as u8;
    let in_tty = (tty & ATTY_IN) != 0;
    let out_tty = (tty & ATTY_OUT) != 0;
    let err_tty = (tty & ATTY_ERR) != 0;

    let tty_fd = if in_tty {
        libc::STDIN_FILENO
    } else if out_tty {
        libc::STDOUT_FILENO
    } else if err_tty {
        libc::STDERR_FILENO
    } else {
        -1
    };

    if tty == ATTY_ALL {
        let mut old = unsafe { std::mem::zeroed() };
        if make_tty_raw(tty_fd, &mut old) == 0 {
            *OLD_STDIN.lock().unwrap() = Some(old);
            TTY_IN_RAW.store(1, Ordering::SeqCst);
        }
    }

    let (lock, _cvar) = get_exit_lock();
    {
        let mut state = lock.lock().unwrap();
        state.finished = false;
    }

    let pending_count = if err_tty { 1 } else { 2 };
    let pending = Arc::new(AtomicI32::new(pending_count));

    let finish_callback = move |pending_arc: Arc<AtomicI32>| {
        if pending_arc.fetch_sub(1, Ordering::SeqCst) == 1 {
            if TTY_IN_RAW.swap(0, Ordering::SeqCst) == 1 {
                if let Some(old) = *OLD_STDIN.lock().unwrap() {
                    restore_fd(tty_fd, &old);
                }
            }
            let (l, c) = get_exit_lock();
            let mut s = l.lock().unwrap();
            s.finished = true;
            c.notify_all();
        }
    };

    transfer_async(libc::STDIN_FILENO, stdin_pipe, false, true, false, None::<fn(bool)>);

    let p1 = pending.clone();
    let cb1 = finish_callback.clone();
    transfer_async(
        stdout_pipe,
        libc::STDOUT_FILENO,
        true,
        false,
        false,
        Some(move |_failed| cb1(p1)),
    );

    if !err_tty {
        let p2 = pending;
        transfer_async(
            stderr_pipe,
            libc::STDERR_FILENO,
            true,
            false,
            false,
            Some(move |_failed| finish_callback(p2)),
        );
    }

    // Initialize winch pipe once
    WINCH_PIPE.get_or_init(|| {
        let mut fds = [-1; 2];
        // Safety: Creating pipe2 with O_CLOEXEC and O_NONBLOCK for signal self-pipe.
        unsafe {
            if libc::pipe2(fds.as_mut_ptr(), libc::O_CLOEXEC | libc::O_NONBLOCK) == 0 {
                fds
            } else {
                [-1, -1]
            }
        }
    });

    // Safety: Installing SIGWINCH signal handler and unblocking SIGWINCH after WINCH_PIPE is ready.
    // Invariant: sigwinch_handler is async-signal-safe, writing only to the non-blocking pipe.
    unsafe {
        libc::signal(libc::SIGWINCH, sigwinch_handler as *const () as usize);
        let mut winch: libc::sigset_t = std::mem::zeroed();
        libc::sigemptyset(&mut winch);
        libc::sigaddset(&mut winch, libc::SIGWINCH);
        libc::pthread_sigmask(libc::SIG_UNBLOCK, &winch, std::ptr::null_mut());
    }

    tty_fd
}

pub fn wait_for_window_size_change(fd: jint) -> jlong {
    if let Some(pipe) = WINCH_PIPE.get() {
        if pipe[0] != -1 {
            let mut b = 0u8;
            loop {
                // Safety: Reading notification byte from self-pipe.
                let res = unsafe {
                    libc::read(pipe[0], &mut b as *mut _ as *mut libc::c_void, 1)
                };
                if res < 0 {
                    let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                    if err == libc::EINTR {
                        continue;
                    }
                }
                break;
            }
        }
    }

    let mut ws: libc::winsize = unsafe { std::mem::zeroed() };
    // Safety: Querying window size with ioctl TIOCGWINSZ on the terminal descriptor.
    // Invariant: ws structure is accurately sized and aligned for TIOCGWINSZ ioctl.
    let res = unsafe { libc::ioctl(fd, libc::TIOCGWINSZ, &mut ws) };
    if res == -1 {
        0
    } else {
        // Pack winsize into jlong (8 bytes: ws_row, ws_col, ws_xpixel, ws_ypixel)
        unsafe { std::mem::transmute(ws) }
    }
}

pub fn wait_for_process_exit() {
    let (lock, cvar) = get_exit_lock();
    let mut state = lock.lock().unwrap();
    while !state.finished {
        state = cvar.wait(state).unwrap();
    }
}
