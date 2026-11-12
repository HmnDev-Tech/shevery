const HEADER_LENGTH: usize = 24;
const ANDROID_PUBKEY_MODULUS_SIZE: usize = 2048 / 8;
const ANDROID_PUBKEY_MODULUS_SIZE_WORDS: usize = ANDROID_PUBKEY_MODULUS_SIZE / 4;
const RSA_PUBLIC_KEY_SIZE: usize = 524;

const B64_TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

#[inline]
pub fn checksum_adb_data(data: &[u8]) -> i32 {
    let mut sum: u32 = 0;
    for &b in data {
        sum = sum.wrapping_add(b as u32);
    }
    sum as i32
}

pub fn encode_adb_message(command: i32, arg0: i32, arg1: i32, data: Option<&[u8]>) -> Vec<u8> {
    let payload = data.unwrap_or(&[]);
    let data_length = payload.len() as i32;
    let data_crc32 = checksum_adb_data(payload);
    let magic = command ^ -1;

    let mut out = Vec::with_capacity(HEADER_LENGTH + payload.len());
    out.extend_from_slice(&command.to_le_bytes());
    out.extend_from_slice(&arg0.to_le_bytes());
    out.extend_from_slice(&arg1.to_le_bytes());
    out.extend_from_slice(&data_length.to_le_bytes());
    out.extend_from_slice(&data_crc32.to_le_bytes());
    out.extend_from_slice(&magic.to_le_bytes());
    out.extend_from_slice(payload);
    out
}

pub fn validate_adb_message(
    command: i32,
    data_length: i32,
    data_crc32: i32,
    magic: i32,
    data: Option<&[u8]>,
) -> bool {
    if command != (magic ^ -1) {
        return false;
    }
    if data_length < 0 {
        return false;
    }
    if data_length != 0 {
        let Some(buf) = data else {
            return false;
        };
        if buf.len() != data_length as usize || checksum_adb_data(buf) != data_crc32 {
            return false;
        }
    }
    true
}

fn base64_encode_nowrap(input: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(((input.len() + 2) / 3) * 4);
    let mut i = 0;
    while i + 3 <= input.len() {
        let n = ((input[i] as u32) << 16) | ((input[i + 1] as u32) << 8) | (input[i + 2] as u32);
        out.push(B64_TABLE[((n >> 18) & 0x3F) as usize]);
        out.push(B64_TABLE[((n >> 12) & 0x3F) as usize]);
        out.push(B64_TABLE[((n >> 6) & 0x3F) as usize]);
        out.push(B64_TABLE[(n & 0x3F) as usize]);
        i += 3;
    }
    let rem = input.len() - i;
    if rem == 1 {
        let n = (input[i] as u32) << 16;
        out.push(B64_TABLE[((n >> 18) & 0x3F) as usize]);
        out.push(B64_TABLE[((n >> 12) & 0x3F) as usize]);
        out.push(b'=');
        out.push(b'=');
    } else if rem == 2 {
        let n = ((input[i] as u32) << 16) | ((input[i + 1] as u32) << 8);
        out.push(B64_TABLE[((n >> 18) & 0x3F) as usize]);
        out.push(B64_TABLE[((n >> 12) & 0x3F) as usize]);
        out.push(B64_TABLE[((n >> 6) & 0x3F) as usize]);
        out.push(b'=');
    }
    out
}

/// Encodes an RSA-2048 public key into Android's `RSAPublicKey` binary format
/// (`system/core/libcrypto_utils/android_pubkey.c`), Base64-encodes it, and appends `" <name>\0"`.
pub fn encode_adb_rsa_pubkey(modulus_be: &[u8], exponent: i32, name: &[u8]) -> Option<Vec<u8>> {
    let first_nonzero = modulus_be.iter().position(|&b| b != 0)?;
    let stripped = &modulus_be[first_nonzero..];
    if stripped.len() > ANDROID_PUBKEY_MODULUS_SIZE {
        return None;
    }

    let mut mod_be = [0u8; ANDROID_PUBKEY_MODULUS_SIZE];
    mod_be[ANDROID_PUBKEY_MODULUS_SIZE - stripped.len()..].copy_from_slice(stripped);

    let mut n_words = [0u32; ANDROID_PUBKEY_MODULUS_SIZE_WORDS];
    for i in 0..ANDROID_PUBKEY_MODULUS_SIZE_WORDS {
        let start = ANDROID_PUBKEY_MODULUS_SIZE - (i + 1) * 4;
        n_words[i] = u32::from_be_bytes([
            mod_be[start],
            mod_be[start + 1],
            mod_be[start + 2],
            mod_be[start + 3],
        ]);
    }

    // Odd RSA modulus required for Montgomery inverse mod 2^32
    if (n_words[0] & 1) == 0 {
        return None;
    }

    // Newton-Raphson modular inverse mod 2^32: n0 * inv == 1 (mod 2^32), n0inv = -inv (mod 2^32)
    let n0 = n_words[0];
    let mut inv: u32 = 1;
    for _ in 0..5 {
        inv = inv.wrapping_mul(2u32.wrapping_sub(n0.wrapping_mul(inv)));
    }
    let n0inv = inv.wrapping_neg();

    // Compute rr = (2^2048)^2 mod N = 2^4096 mod N via 4096 modular doublings
    let mut rr = [0u32; ANDROID_PUBKEY_MODULUS_SIZE_WORDS];
    rr[0] = 1;
    for _ in 0..4096 {
        let mut carry: u64 = 0;
        for i in 0..ANDROID_PUBKEY_MODULUS_SIZE_WORDS {
            let shifted = ((rr[i] as u64) << 1) | carry;
            rr[i] = shifted as u32;
            carry = shifted >> 32;
        }

        let mut ge = carry != 0;
        if !ge {
            ge = true;
            for i in (0..ANDROID_PUBKEY_MODULUS_SIZE_WORDS).rev() {
                if rr[i] != n_words[i] {
                    ge = rr[i] > n_words[i];
                    break;
                }
            }
        }

        if ge {
            let mut borrow: i64 = 0;
            for i in 0..ANDROID_PUBKEY_MODULUS_SIZE_WORDS {
                let diff = (rr[i] as i64) - (n_words[i] as i64) - borrow;
                if diff < 0 {
                    rr[i] = (diff + (1i64 << 32)) as u32;
                    borrow = 1;
                } else {
                    rr[i] = diff as u32;
                    borrow = 0;
                }
            }
        }
    }

    let mut struct_bytes = Vec::with_capacity(RSA_PUBLIC_KEY_SIZE);
    struct_bytes.extend_from_slice(&(ANDROID_PUBKEY_MODULUS_SIZE_WORDS as u32).to_le_bytes());
    struct_bytes.extend_from_slice(&n0inv.to_le_bytes());
    for &w in &n_words {
        struct_bytes.extend_from_slice(&w.to_le_bytes());
    }
    for &w in &rr {
        struct_bytes.extend_from_slice(&w.to_le_bytes());
    }
    struct_bytes.extend_from_slice(&(exponent as u32).to_le_bytes());

    let b64 = base64_encode_nowrap(&struct_bytes);
    let mut out = Vec::with_capacity(b64.len() + 2 + name.len());
    out.extend_from_slice(&b64);
    out.push(b' ');
    out.extend_from_slice(name);
    out.push(0);
    Some(out)
}
