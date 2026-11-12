use sha2::{Digest, Sha256};

const STEALTH_LABEL_PLACEHOLDER: &str = "SheveryStealthLabelPlaceholder32";
const CHUNK_SIZE_BYTES: usize = 1024 * 1024;

const PRESERVED_ASCII: &[&[u8]] = &[
    b".tasker",
    b".api",
    b".permission.PRIVILEGED_API",
];

const PRESERVED_UTF16LE: &[&[u8]] = &[
    b".\0t\0a\0s\0k\0e\0r\0",
    b".\0a\0p\0i\0",
    b".\0p\0e\0r\0m\0i\0s\0s\0i\0o\0n\0.\0P\0R\0I\0V\0I\0L\0E\0G\0E\0D\0_\0A\0P\0I\0",
];

#[inline]
fn find_subslice(haystack: &[u8], needle: &[u8], from: usize) -> Option<usize> {
    if needle.is_empty() || from + needle.len() > haystack.len() {
        return None;
    }
    haystack[from..]
        .windows(needle.len())
        .position(|w| w == needle)
        .map(|pos| from + pos)
}

pub fn swap_filtered(data: &[u8], old: &[u8], repl: &[u8], is_utf16: bool) -> Vec<u8> {
    if old.is_empty() || old.len() != repl.len() || data.len() < old.len() {
        return data.to_vec();
    }
    let Some(mut idx) = find_subslice(data, old, 0) else {
        return data.to_vec();
    };

    let mut out = data.to_vec();
    let preserved = if is_utf16 {
        PRESERVED_UTF16LE
    } else {
        PRESERVED_ASCII
    };

    loop {
        let after = idx + old.len();
        let skipped = preserved.iter().any(|suffix| {
            !suffix.is_empty()
                && after + suffix.len() <= out.len()
                && &out[after..after + suffix.len()] == *suffix
        });
        if !skipped {
            out[idx..after].copy_from_slice(repl);
        }
        match find_subslice(&out, old, after) {
            Some(next) => idx = next,
            None => break,
        }
    }
    out
}

#[inline]
fn read_u16_le(buf: &[u8], offset: usize) -> u16 {
    u16::from_le_bytes([buf[offset], buf[offset + 1]])
}

#[inline]
fn read_i32_le(buf: &[u8], offset: usize) -> i32 {
    i32::from_le_bytes([
        buf[offset],
        buf[offset + 1],
        buf[offset + 2],
        buf[offset + 3],
    ])
}

#[inline]
fn write_u16_le(buf: &mut [u8], offset: usize, val: u16) {
    let bytes = val.to_le_bytes();
    buf[offset] = bytes[0];
    buf[offset + 1] = bytes[1];
}

#[inline]
fn write_i32_le(buf: &mut [u8], offset: usize, val: i32) {
    let bytes = val.to_le_bytes();
    buf[offset..offset + 4].copy_from_slice(&bytes);
}

pub fn patch_manifest_label(axml: &[u8], app_name: &str) -> Vec<u8> {
    if axml.len() < 36 {
        return axml.to_vec();
    }
    let mut out = axml.to_vec();
    if read_u16_le(&out, 0) != 0x0003 {
        return axml.to_vec();
    }
    let sp_offset: usize = 8;
    if read_u16_le(&out, sp_offset) != 0x0001 {
        return axml.to_vec();
    }

    let sp_chunk_size = read_i32_le(&out, sp_offset + 4);
    let string_count = read_i32_le(&out, sp_offset + 8);
    let flags = read_i32_le(&out, sp_offset + 16);
    let strings_start = read_i32_le(&out, sp_offset + 20);
    if sp_chunk_size <= 0 || string_count <= 0 || strings_start < 0 {
        return out;
    }

    let is_utf8 = (flags & 0x100) != 0;
    let offsets_base = sp_offset + 28;
    let pool_data_base = sp_offset + (strings_start as usize);
    let mut placeholder_idx: i32 = -1;

    for i in 0..(string_count as usize) {
        let off_pos = offsets_base + i * 4;
        if off_pos + 4 > out.len() {
            break;
        }
        let rel = read_i32_le(&out, off_pos);
        if rel < 0 {
            continue;
        }
        let str_offset = pool_data_base.saturating_add(rel as usize);
        if str_offset >= out.len() {
            continue;
        }

        if is_utf8 {
            if str_offset + 2 > out.len() {
                continue;
            }
            let byte_len = out[str_offset + 1] as usize;
            if str_offset + 2 + byte_len <= out.len() {
                if &out[str_offset + 2..str_offset + 2 + byte_len]
                    == STEALTH_LABEL_PLACEHOLDER.as_bytes()
                {
                    let mut trimmed = app_name;
                    while trimmed.as_bytes().len() > byte_len && !trimmed.is_empty() {
                        let mut chars = trimmed.chars();
                        chars.next_back();
                        trimmed = chars.as_str();
                    }
                    let new_bytes = trimmed.as_bytes();
                    let utf16_len = trimmed.encode_utf16().count().min(byte_len);
                    out[str_offset] = utf16_len as u8;
                    out[str_offset + 1] = new_bytes.len() as u8;
                    for k in 0..=byte_len {
                        if str_offset + 2 + k < out.len() {
                            out[str_offset + 2 + k] = 0;
                        }
                    }
                    out[str_offset + 2..str_offset + 2 + new_bytes.len()]
                        .copy_from_slice(new_bytes);
                    placeholder_idx = i as i32;
                    break;
                }
            }
        } else {
            if str_offset + 2 > out.len() {
                continue;
            }
            let char_len = read_u16_le(&out, str_offset) as usize;
            let byte_len = char_len * 2;
            if str_offset + 2 + byte_len <= out.len() {
                let mut units = Vec::with_capacity(char_len);
                for c in 0..char_len {
                    units.push(read_u16_le(&out, str_offset + 2 + c * 2));
                }
                if String::from_utf16(&units).ok().as_deref() == Some(STEALTH_LABEL_PLACEHOLDER) {
                    let new_units: Vec<u16> = app_name.encode_utf16().take(char_len).collect();
                    write_u16_le(&mut out, str_offset, new_units.len() as u16);
                    for k in 0..(char_len + 1) * 2 {
                        if str_offset + 2 + k < out.len() {
                            out[str_offset + 2 + k] = 0;
                        }
                    }
                    for (idx_u, &u) in new_units.iter().enumerate() {
                        write_u16_le(&mut out, str_offset + 2 + idx_u * 2, u);
                    }
                    placeholder_idx = i as i32;
                    break;
                }
            }
        }
    }

    if placeholder_idx < 0 {
        return out;
    }

    let mut pos = sp_offset.saturating_add(sp_chunk_size as usize);
    let mut label_attr_name_idx: i32 = -1;
    while pos + 8 <= out.len() {
        let chunk_type = read_u16_le(&out, pos);
        let chunk_size = read_i32_le(&out, pos + 4);
        if chunk_size <= 0 || pos + (chunk_size as usize) > out.len() {
            break;
        }
        let csize = chunk_size as usize;

        if chunk_type == 0x0180 && csize >= 8 {
            let count = (csize - 8) / 4;
            for i in 0..count {
                if read_i32_le(&out, pos + 8 + i * 4) == 0x01010001 {
                    label_attr_name_idx = i as i32;
                    break;
                }
            }
        } else if chunk_type == 0x0102 && label_attr_name_idx >= 0 && pos + 26 <= out.len() {
            let attr_start = read_u16_le(&out, pos + 20) as usize;
            let attr_size = read_u16_le(&out, pos + 22) as usize;
            let attr_count = read_u16_le(&out, pos + 24) as usize;
            if attr_size >= 20 {
                let attrs_base = pos + 16 + attr_start;
                for a in 0..attr_count {
                    let attr_pos = attrs_base + a * attr_size;
                    if attr_pos + 20 > out.len() {
                        break;
                    }
                    let attr_name_idx = read_i32_le(&out, attr_pos + 4);
                    let attr_data_type = out[attr_pos + 15];
                    if attr_name_idx == label_attr_name_idx && attr_data_type == 0x01 {
                        write_i32_le(&mut out, attr_pos + 8, placeholder_idx);
                        out[attr_pos + 15] = 0x03; // TYPE_STRING
                        write_i32_le(&mut out, attr_pos + 16, placeholder_idx);
                        return out;
                    }
                }
            }
        }
        pos += csize;
    }

    out
}

/// Pure-Rust RFC 3174 SHA-1 implementation for DEX header signature recalculation.
pub fn sha1_digest(data: &[u8]) -> [u8; 20] {
    let mut h0: u32 = 0x67452301;
    let mut h1: u32 = 0xEFCDAB89;
    let mut h2: u32 = 0x98BADCFE;
    let mut h3: u32 = 0x10325476;
    let mut h4: u32 = 0xC3D2E1F0;

    let bit_len = (data.len() as u64).wrapping_mul(8);
    let mut padded = Vec::with_capacity((data.len() + 72) & !63);
    padded.extend_from_slice(data);
    padded.push(0x80);
    while (padded.len() % 64) != 56 {
        padded.push(0);
    }
    padded.extend_from_slice(&bit_len.to_be_bytes());

    let mut w = [0u32; 80];
    for chunk in padded.chunks_exact(64) {
        for i in 0..16 {
            let j = i * 4;
            w[i] = u32::from_be_bytes([chunk[j], chunk[j + 1], chunk[j + 2], chunk[j + 3]]);
        }
        for i in 16..80 {
            w[i] = (w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16]).rotate_left(1);
        }

        let mut a = h0;
        let mut b = h1;
        let mut c = h2;
        let mut d = h3;
        let mut e = h4;

        for i in 0..80 {
            let (f, k) = match i {
                0..=19 => ((b & c) | ((!b) & d), 0x5A827999u32),
                20..=39 => (b ^ c ^ d, 0x6ED9EBA1u32),
                40..=59 => ((b & c) | (b & d) | (c & d), 0x8F1BBCDCu32),
                _ => (b ^ c ^ d, 0xCA62C1D6u32),
            };
            let temp = a
                .rotate_left(5)
                .wrapping_add(f)
                .wrapping_add(e)
                .wrapping_add(k)
                .wrapping_add(w[i]);
            e = d;
            d = c;
            c = b.rotate_left(30);
            b = a;
            a = temp;
        }

        h0 = h0.wrapping_add(a);
        h1 = h1.wrapping_add(b);
        h2 = h2.wrapping_add(c);
        h3 = h3.wrapping_add(d);
        h4 = h4.wrapping_add(e);
    }

    let mut out = [0u8; 20];
    out[0..4].copy_from_slice(&h0.to_be_bytes());
    out[4..8].copy_from_slice(&h1.to_be_bytes());
    out[8..12].copy_from_slice(&h2.to_be_bytes());
    out[12..16].copy_from_slice(&h3.to_be_bytes());
    out[16..20].copy_from_slice(&h4.to_be_bytes());
    out
}

/// Pure-Rust RFC 1950 Adler-32 implementation for DEX header checksum recalculation.
pub fn adler32_checksum(data: &[u8]) -> u32 {
    const MOD_ADLER: u32 = 65521;
    let mut a: u32 = 1;
    let mut b: u32 = 0;
    for chunk in data.chunks(5552) {
        for &byte in chunk {
            a += byte as u32;
            b += a;
        }
        a %= MOD_ADLER;
        b %= MOD_ADLER;
    }
    (b << 16) | a
}

pub fn fix_dex_header(dex: &[u8]) -> Vec<u8> {
    if dex.len() < 32 || &dex[0..4] != b"dex\n" {
        return dex.to_vec();
    }
    let mut out = dex.to_vec();
    let sig = sha1_digest(&out[32..]);
    out[12..32].copy_from_slice(&sig);
    let checksum = adler32_checksum(&out[12..]);
    out[8..12].copy_from_slice(&checksum.to_le_bytes());
    out
}

pub fn crc32_ieee(data: &[u8]) -> u32 {
    let mut crc: u32 = 0xFFFF_FFFF;
    for &byte in data {
        let mut c = (crc ^ (byte as u32)) & 0xFF;
        for _ in 0..8 {
            if (c & 1) != 0 {
                c = 0xEDB8_8320 ^ (c >> 1);
            } else {
                c >>= 1;
            }
        }
        crc = (crc >> 8) ^ c;
    }
    !crc
}

pub fn sha256_digest(data: &[u8]) -> [u8; 32] {
    Sha256::digest(data).into()
}

pub fn compute_apk_v2_top_digest(section1: &[u8], section2: &[u8], eocd: &[u8]) -> [u8; 32] {
    let mut chunk_digests = Vec::new();
    let mut total_chunks: u32 = 0;

    for section in [section1, section2, eocd] {
        let mut offset = 0;
        while offset < section.len() {
            let len = (section.len() - offset).min(CHUNK_SIZE_BYTES);
            let mut hasher = Sha256::new();
            hasher.update([0xa5u8]);
            hasher.update((len as u32).to_le_bytes());
            hasher.update(&section[offset..offset + len]);
            chunk_digests.extend_from_slice(&hasher.finalize());
            total_chunks += 1;
            offset += len;
        }
    }

    let mut top_hasher = Sha256::new();
    top_hasher.update([0x5au8]);
    top_hasher.update(total_chunks.to_le_bytes());
    top_hasher.update(&chunk_digests);
    top_hasher.finalize().into()
}
