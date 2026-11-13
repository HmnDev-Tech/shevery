use sha2::{Digest, Sha256};
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

const DISABLE_FILE: &str = "disable";
const SOURCE_PROP_FILE: &str = ".source.prop";

fn collect_module_files(root: &Path, current: &Path, out: &mut Vec<(String, PathBuf)>) {
    let Ok(entries) = fs::read_dir(current) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        let Ok(ft) = entry.file_type() else {
            continue;
        };
        if ft.is_dir() {
            collect_module_files(root, &path, out);
        } else if ft.is_file() {
            let Some(name) = path.file_name().and_then(|n| n.to_str()) else {
                continue;
            };
            if name == DISABLE_FILE || name == SOURCE_PROP_FILE {
                continue;
            }
            if let Ok(rel) = path.strip_prefix(root) {
                let rel_str = rel
                    .components()
                    .map(|c| c.as_os_str().to_string_lossy())
                    .collect::<Vec<_>>()
                    .join("/");
                out.push((rel_str, path));
            }
        }
    }
}

pub fn compute_module_fingerprint(dir_path: &str) -> Option<String> {
    let root = Path::new(dir_path);
    if !root.is_dir() {
        return None;
    }

    let mut files = Vec::new();
    collect_module_files(root, root, &mut files);
    files.sort_by(|a, b| a.0.cmp(&b.0));

    let mut hasher = Sha256::new();
    let mut buf = [0u8; 8192];

    for (rel, path) in files {
        hasher.update(rel.as_bytes());
        hasher.update([0u8]);
        let mut f = fs::File::open(path).ok()?;
        loop {
            let read = f.read(&mut buf).ok()?;
            if read == 0 {
                break;
            }
            hasher.update(&buf[..read]);
        }
        hasher.update([0u8]);
    }

    let digest = hasher.finalize();
    let mut hex = String::with_capacity(64);
    for b in digest {
        use std::fmt::Write;
        let _ = write!(&mut hex, "{:02x}", b);
    }
    Some(hex)
}

/// Reads from a raw file descriptor until EOF, keeping at most the last `max_chars`
/// Unicode scalar values, and closes `fd` before returning.
pub fn read_fd_tail(fd: i32, max_chars: usize) -> String {
    if fd < 0 || max_chars == 0 {
        if fd >= 0 {
            unsafe {
                libc::close(fd);
            }
        }
        return String::new();
    }

    let max_bytes = max_chars.saturating_mul(4);
    let mut raw_tail = Vec::with_capacity(max_chars.min(65536));
    let mut buf = [0u8; 8192];

    loop {
        // Safety: Reading from valid file descriptor into stack buffer.
        let n = unsafe { libc::read(fd, buf.as_mut_ptr() as *mut libc::c_void, buf.len()) };
        if n > 0 {
            let n = n as usize;
            raw_tail.extend_from_slice(&buf[..n]);
            if raw_tail.len() > max_bytes.saturating_mul(2) {
                let drop_len = raw_tail.len() - max_bytes;
                raw_tail.drain(0..drop_len);
            }
        } else if n == 0 {
            break;
        } else {
            let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
            if err == libc::EINTR {
                continue;
            }
            break;
        }
    }

    // Safety: Closing the detached file descriptor once reading is complete.
    unsafe {
        libc::close(fd);
    }

    let text = String::from_utf8_lossy(&raw_tail);
    let char_count = text.chars().count();
    if char_count <= max_chars {
        text.into_owned()
    } else {
        let skip = char_count - max_chars;
        text.chars().skip(skip).collect()
    }
}
