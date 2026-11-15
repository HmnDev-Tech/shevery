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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checksum_adb_data_sums_bytes_wrapping() {
        assert_eq!(checksum_adb_data(&[]), 0);
        assert_eq!(checksum_adb_data(b"hello"), 532);
        assert_eq!(checksum_adb_data(&[0xFF, 0xFF, 0xFF, 0xFF]), 1020);
    }

    #[test]
    fn encode_and_validate_adb_message_roundtrip() {
        const CMD_CNXN: i32 = 0x4E584E43;
        let payload = b"host::features=shell_v2";
        let encoded = encode_adb_message(CMD_CNXN, 0x0100_0000, 1024 * 1024, Some(payload));

        assert_eq!(encoded.len(), HEADER_LENGTH + payload.len());
        let cmd = i32::from_le_bytes(encoded[0..4].try_into().unwrap());
        let arg0 = i32::from_le_bytes(encoded[4..8].try_into().unwrap());
        let arg1 = i32::from_le_bytes(encoded[8..12].try_into().unwrap());
        let len = i32::from_le_bytes(encoded[12..16].try_into().unwrap());
        let crc = i32::from_le_bytes(encoded[16..20].try_into().unwrap());
        let magic = i32::from_le_bytes(encoded[20..24].try_into().unwrap());

        assert_eq!(cmd, CMD_CNXN);
        assert_eq!(arg0, 0x0100_0000);
        assert_eq!(arg1, 1024 * 1024);
        assert_eq!(len, payload.len() as i32);
        assert!(validate_adb_message(cmd, len, crc, magic, Some(&encoded[HEADER_LENGTH..])));

        assert!(!validate_adb_message(cmd, len, crc, magic ^ 1, Some(&encoded[HEADER_LENGTH..])));
        assert!(!validate_adb_message(cmd, -1, crc, magic, Some(&encoded[HEADER_LENGTH..])));
        assert!(!validate_adb_message(cmd, len, crc ^ 1, magic, Some(&encoded[HEADER_LENGTH..])));
        assert!(!validate_adb_message(cmd, len, crc, magic, None));

        let empty = encode_adb_message(CMD_CNXN, 0, 0, None);
        assert_eq!(empty.len(), HEADER_LENGTH);
        assert!(validate_adb_message(CMD_CNXN, 0, 0, CMD_CNXN ^ -1, None));
    }

    #[test]
    fn encode_adb_rsa_pubkey_validates_modulus_and_formats_b64_line() {
        assert!(encode_adb_rsa_pubkey(&[0u8; 256], 65537, b"host").is_none());
        assert!(encode_adb_rsa_pubkey(&[0xABu8; 257], 65537, b"host").is_none());

        let mut even_mod = vec![0x80u8; 256];
        even_mod[255] = 0x10;
        assert!(encode_adb_rsa_pubkey(&even_mod, 65537, b"host").is_none());

        let mut odd_mod = vec![0u8, 0u8];
        odd_mod.extend(std::iter::repeat(0xA5u8).take(255));
        odd_mod.push(0xB7u8);
        let encoded = encode_adb_rsa_pubkey(&odd_mod, 65537, b"shevery@device").unwrap();

        assert!(encoded.ends_with(b" shevery@device\0"));
        let b64_len = ((RSA_PUBLIC_KEY_SIZE + 2) / 3) * 4;
        assert_eq!(encoded.len(), b64_len + 1 + b"shevery@device".len() + 1);
    }
}
