use std::os::raw::c_int;

pub const ATTY_IN: u8 = 1;
pub const ATTY_OUT: u8 = 2;
pub const ATTY_ERR: u8 = 4;
pub const ATTY_ALL: u8 = ATTY_IN | ATTY_OUT | ATTY_ERR;

pub fn open_ptmx() -> c_int {
    // Safety: Opening /dev/ptmx with O_RDWR and O_CLOEXEC.
    // Invariant: grantpt and unlockpt initialize the slave pseudo-terminal multiplexer.
    unsafe {
        let path = b"/dev/ptmx\0";
        let fd = libc::open(path.as_ptr() as *const libc::c_char, libc::O_RDWR | libc::O_CLOEXEC);
        if fd == -1 {
            return -1;
        }

        if libc::grantpt(fd) == -1 {
            libc::close(fd);
            return -1;
        }

        if libc::unlockpt(fd) == -1 {
            libc::close(fd);
            return -1;
        }

        fd
    }
}

pub fn make_tty_raw(fd: c_int, old_termios: &mut libc::termios) -> c_int {
    // Safety: tcgetattr queries terminal settings; cfmakeraw configures raw mode;
    // tcsetattr applies the raw mode immediately.
    // Invariant: old_termios stores the previous terminal state for restoration.
    unsafe {
        let mut termios: libc::termios = std::mem::zeroed();
        if libc::tcgetattr(fd, &mut termios) < 0 {
            return -1;
        }
        *old_termios = termios;

        libc::cfmakeraw(&mut termios);

        if libc::tcsetattr(fd, libc::TCSANOW, &termios) < 0 {
            return -1;
        }
        0
    }
}

pub fn restore_fd(fd: c_int, old_termios: &libc::termios) -> c_int {
    // Safety: tcsetattr restores the saved terminal mode.
    // Invariant: old_termios contains valid terminal flags obtained from tcgetattr.
    unsafe {
        if libc::tcsetattr(fd, libc::TCSANOW, old_termios) < 0 {
            return -1;
        }
        0
    }
}

pub fn write_full(fd: c_int, mut buf: &[u8]) -> bool {
    while !buf.is_empty() {
        // Safety: libc::write is passed a valid buffer pointer and slice length.
        // Invariant: Handles EINTR interrupts by continuing the write loop.
        let written = unsafe {
            libc::write(
                fd,
                buf.as_ptr() as *const libc::c_void,
                buf.len().min(isize::MAX as usize),
            )
        };
        if written <= 0 {
            let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
            if err == libc::EINTR {
                continue;
            }
            return false;
        }
        buf = &buf[written as usize..];
    }
    true
}

pub fn transfer(
    in_fd: c_int,
    out_fd: c_int,
    close_in: bool,
    close_out: bool,
    watch_out: bool,
    on_complete: Option<Box<dyn FnOnce(bool) + Send>>,
) {
    let mut buf = [0u8; 8192];
    let mut failed = false;

    loop {
        if watch_out {
            let mut fds = [
                libc::pollfd {
                    fd: in_fd,
                    events: libc::POLLIN,
                    revents: 0,
                },
                libc::pollfd {
                    fd: out_fd,
                    events: 0,
                    revents: 0,
                },
            ];

            // Safety: libc::poll polls the two descriptors without modifying memory beyond fds array.
            // Invariant: fds contains valid descriptors and length 2.
            let poll_res = unsafe { libc::poll(fds.as_mut_ptr(), 2, -1) };
            if poll_res == -1 {
                let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                if err == libc::EINTR {
                    continue;
                }
                failed = true;
                break;
            }

            if (fds[1].revents & (libc::POLLERR | libc::POLLHUP | libc::POLLNVAL)) != 0 {
                failed = true;
                break;
            }
        }

        // Safety: Reading up to buf.len() from in_fd.
        // Invariant: buf is an allocated slice on stack.
        let len = unsafe {
            libc::read(
                in_fd,
                buf.as_mut_ptr() as *mut libc::c_void,
                buf.len(),
            )
        };

        if len <= 0 {
            if len < 0 {
                let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                if err == libc::EINTR {
                    continue;
                }
            }
            break;
        }

        if !write_full(out_fd, &buf[..len as usize]) {
            failed = true;
            break;
        }
    }

    if close_in {
        // Safety: Closing descriptor as requested by caller.
        unsafe { libc::close(in_fd) };
    }
    if close_out {
        // Safety: Closing descriptor as requested by caller.
        unsafe { libc::close(out_fd) };
    }

    if let Some(cb) = on_complete {
        cb(failed);
    }
}

pub fn transfer_async<F>(
    in_fd: c_int,
    out_fd: c_int,
    close_in: bool,
    close_out: bool,
    watch_out: bool,
    on_complete: Option<F>,
) where
    F: FnOnce(bool) + Send + 'static,
{
    let cb: Option<Box<dyn FnOnce(bool) + Send>> = on_complete.map(|f| Box::new(f) as _);
    std::thread::spawn(move || {
        transfer(in_fd, out_fd, close_in, close_out, watch_out, cb);
    });
}
