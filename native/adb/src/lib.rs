pub mod module_io;
pub mod protocol;
pub mod stealth;

use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes128Gcm, Nonce};
use curve25519_dalek::edwards::{CompressedEdwardsY, EdwardsPoint};
use curve25519_dalek::scalar::Scalar;
use hkdf::Hkdf;
use jni::objects::{JByteArray, JString};
use jni::sys::{
    jboolean, jbyteArray, jclass, jint, jlong, jobject, jstring, JNIEnv, JNINativeMethod, JNI_ERR,
    JNI_FALSE, JNI_TRUE, JNI_VERSION_1_6,
};
use rand::RngCore;
use sha2::{Digest, Sha256, Sha512};
use std::ffi::CString;
use std::os::raw::c_void;

const M_BYTES: [u8; 32] = [
    0x5a, 0xda, 0x7e, 0x4b, 0xf6, 0xdd, 0xd9, 0xad, 0xb6, 0x62, 0x6d, 0x32, 0x13, 0x1c, 0x6b,
    0x5c, 0x51, 0xa1, 0xe3, 0x47, 0xa3, 0x47, 0x8f, 0x53, 0xcf, 0xcf, 0x44, 0x1b, 0x88, 0xee,
    0xd1, 0x2e,
];
const N_BYTES: [u8; 32] = [
    0x10, 0xe3, 0xdf, 0x0a, 0xe3, 0x7d, 0x8e, 0x7a, 0x99, 0xb5, 0xfe, 0x74, 0xb4, 0x46, 0x72,
    0x10, 0x3d, 0xbd, 0xdc, 0xbd, 0x06, 0xaf, 0x68, 0x0d, 0x71, 0x32, 0x9a, 0x11, 0x69, 0x3b,
    0xc7, 0x78,
];

const CLIENT_NAME: &[u8] = b"adb pair client";
const SERVER_NAME: &[u8] = b"adb pair server";
const HKDF_INFO: &[u8] = b"adb pairing_auth aes-128-gcm key";

#[inline]
fn zeroize_slice(buf: &mut [u8]) {
    for b in buf.iter_mut() {
        // Safety: b is a valid mutable reference to an initialized u8 within buf.
        unsafe {
            std::ptr::write_volatile(b, 0);
        }
    }
    std::sync::atomic::compiler_fence(std::sync::atomic::Ordering::SeqCst);
}

pub struct PairingContext {
    is_client: bool,
    my_msg: [u8; 32],
    private_scalar: Scalar,
    password_hash: [u8; 64],
    password_scalar: Scalar,
    cipher: Option<Aes128Gcm>,
    enc_sequence: u64,
    dec_sequence: u64,
}

impl Drop for PairingContext {
    fn drop(&mut self) {
        zeroize_slice(&mut self.password_hash);
        self.private_scalar = Scalar::ZERO;
        self.password_scalar = Scalar::ZERO;
        self.cipher = None;
    }
}

impl PairingContext {
    pub fn new(is_client: bool, password: &[u8]) -> Option<Self> {
        let point_m = CompressedEdwardsY(M_BYTES).decompress()?;
        let point_n = CompressedEdwardsY(N_BYTES).decompress()?;

        let mut private_tmp = [0u8; 64];
        rand::thread_rng().fill_bytes(&mut private_tmp);
        let private_scalar = Scalar::from_bytes_mod_order_wide(&private_tmp) * Scalar::from(8u64);
        zeroize_slice(&mut private_tmp);
        let p_point = EdwardsPoint::mul_base(&private_scalar);

        let password_hash: [u8; 64] = Sha512::digest(password).into();
        let password_scalar = Scalar::from_bytes_mod_order_wide(&password_hash);

        let inv8 = Scalar::from(8u64).invert();
        let mask_point = if is_client { point_m } else { point_n };
        let mask = mask_point.mul_by_cofactor() * (password_scalar * inv8);

        let my_msg_point = p_point + mask;
        let my_msg = *my_msg_point.compress().as_bytes();

        Some(PairingContext {
            is_client,
            my_msg,
            private_scalar,
            password_hash,
            password_scalar,
            cipher: None,
            enc_sequence: 0,
            dec_sequence: 0,
        })
    }

    pub fn msg(&self) -> &[u8] {
        &self.my_msg
    }

    pub fn init_cipher(&mut self, their_msg: &[u8]) -> bool {
        if their_msg.len() != 32 {
            return false;
        }

        let mut their_msg_bytes = [0u8; 32];
        their_msg_bytes.copy_from_slice(their_msg);
        let q_star = match CompressedEdwardsY(their_msg_bytes).decompress() {
            Some(p) => p,
            None => return false,
        };

        let point_m = match CompressedEdwardsY(M_BYTES).decompress() {
            Some(p) => p,
            None => return false,
        };
        let point_n = match CompressedEdwardsY(N_BYTES).decompress() {
            Some(p) => p,
            None => return false,
        };

        let inv8 = Scalar::from(8u64).invert();
        let peer_mask_point = if self.is_client { point_n } else { point_m };
        let peer_mask = peer_mask_point.mul_by_cofactor() * (self.password_scalar * inv8);

        let q_ext = q_star - peer_mask;
        let dh_shared = q_ext * self.private_scalar;
        let mut dh_shared_encoded = *dh_shared.compress().as_bytes();

        let mut hasher = Sha512::new();
        let update_with_prefix = |hasher: &mut Sha512, data: &[u8]| {
            let len_le = (data.len() as u64).to_le_bytes();
            hasher.update(&len_le);
            hasher.update(data);
        };

        if self.is_client {
            update_with_prefix(&mut hasher, CLIENT_NAME);
            update_with_prefix(&mut hasher, SERVER_NAME);
            update_with_prefix(&mut hasher, &self.my_msg);
            update_with_prefix(&mut hasher, their_msg);
        } else {
            update_with_prefix(&mut hasher, CLIENT_NAME);
            update_with_prefix(&mut hasher, SERVER_NAME);
            update_with_prefix(&mut hasher, their_msg);
            update_with_prefix(&mut hasher, &self.my_msg);
        }
        update_with_prefix(&mut hasher, &dh_shared_encoded);
        update_with_prefix(&mut hasher, &self.password_hash);
        zeroize_slice(&mut dh_shared_encoded);

        let mut key_material = hasher.finalize();

        let hk = Hkdf::<Sha256>::new(None, &key_material);
        zeroize_slice(key_material.as_mut_slice());
        let mut key = [0u8; 16];
        if hk.expand(HKDF_INFO, &mut key).is_err() {
            zeroize_slice(&mut key);
            return false;
        }

        let cipher = Aes128Gcm::new_from_slice(&key).ok();
        zeroize_slice(&mut key);
        self.cipher = match cipher {
            Some(c) => Some(c),
            None => return false,
        };
        true
    }

    pub fn encrypt(&mut self, plaintext: &[u8]) -> Option<Vec<u8>> {
        let cipher = self.cipher.as_ref()?;
        let seq = self.enc_sequence;
        let next_seq = seq.checked_add(1)?;
        let mut nonce_bytes = [0u8; 12];
        nonce_bytes[..8].copy_from_slice(&seq.to_le_bytes());
        let nonce = Nonce::from_slice(&nonce_bytes);

        let ciphertext = cipher.encrypt(nonce, plaintext).ok()?;
        self.enc_sequence = next_seq;
        Some(ciphertext)
    }

    pub fn decrypt(&mut self, ciphertext: &[u8]) -> Option<Vec<u8>> {
        let cipher = self.cipher.as_ref()?;
        let seq = self.dec_sequence;
        let next_seq = seq.checked_add(1)?;
        let mut nonce_bytes = [0u8; 12];
        nonce_bytes[..8].copy_from_slice(&seq.to_le_bytes());
        let nonce = Nonce::from_slice(&nonce_bytes);

        let plaintext = cipher.decrypt(nonce, ciphertext).ok()?;
        self.dec_sequence = next_seq;
        Some(plaintext)
    }
}

// ---------------- JNI Implementation ----------------

#[no_mangle]
pub unsafe extern "C" fn jni_AdbPairingClient_available(
    _raw_env: *mut JNIEnv,
    _class: jclass,
) -> jboolean {
    JNI_TRUE
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_adb_AdbPairingClient_available(
    _raw_env: *mut JNIEnv,
    _class: jclass,
) -> jboolean {
    JNI_TRUE
}

#[no_mangle]
pub unsafe extern "C" fn jni_PairingContext_Constructor(
    raw_env: *mut JNIEnv,
    _class: jclass,
    is_client: jboolean,
    j_password: jbyteArray,
) -> jlong {
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return 0,
    };

    let pswd_arr = JByteArray::from_raw(j_password);
    let mut password = match env.convert_byte_array(&pswd_arr) {
        Ok(p) => p,
        Err(_) => return 0,
    };

    let res = match PairingContext::new(is_client != 0, &password) {
        Some(ctx) => Box::into_raw(Box::new(ctx)) as jlong,
        None => 0,
    };
    zeroize_slice(&mut password);
    res
}

#[no_mangle]
pub unsafe extern "C" fn jni_PairingContext_Msg(
    raw_env: *mut JNIEnv,
    _obj: jobject,
    ptr: jlong,
) -> jbyteArray {
    if ptr == 0 {
        return std::ptr::null_mut();
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };

    // Safety: Transmuting non-zero pointer created by Box::into_raw in jni_PairingContext_Constructor.
    // Invariant: The pointer is valid and properly aligned until jni_PairingContext_Destroy is invoked.
    let ctx = &*(ptr as *mut PairingContext);
    match env.byte_array_from_slice(ctx.msg()) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub unsafe extern "C" fn jni_PairingContext_InitCipher(
    raw_env: *mut JNIEnv,
    _obj: jobject,
    ptr: jlong,
    j_their_msg: jbyteArray,
) -> jboolean {
    if ptr == 0 {
        return JNI_FALSE;
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return JNI_FALSE,
    };

    let their_msg_arr = JByteArray::from_raw(j_their_msg);
    let their_msg = match env.convert_byte_array(&their_msg_arr) {
        Ok(m) => m,
        Err(_) => return JNI_FALSE,
    };

    // Safety: Transmuting non-zero pointer created by Box::into_raw.
    let ctx = &mut *(ptr as *mut PairingContext);
    if ctx.init_cipher(&their_msg) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub unsafe extern "C" fn jni_PairingContext_Encrypt(
    raw_env: *mut JNIEnv,
    _obj: jobject,
    ptr: jlong,
    j_in: jbyteArray,
) -> jbyteArray {
    if ptr == 0 {
        return std::ptr::null_mut();
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };

    let in_arr = JByteArray::from_raw(j_in);
    let in_bytes = match env.convert_byte_array(&in_arr) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    // Safety: Transmuting non-zero pointer created by Box::into_raw.
    let ctx = &mut *(ptr as *mut PairingContext);
    match ctx.encrypt(&in_bytes) {
        Some(ct) => env
            .byte_array_from_slice(&ct)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub unsafe extern "C" fn jni_PairingContext_Decrypt(
    raw_env: *mut JNIEnv,
    _obj: jobject,
    ptr: jlong,
    j_in: jbyteArray,
) -> jbyteArray {
    if ptr == 0 {
        return std::ptr::null_mut();
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };

    let in_arr = JByteArray::from_raw(j_in);
    let in_bytes = match env.convert_byte_array(&in_arr) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    // Safety: Transmuting non-zero pointer created by Box::into_raw.
    let ctx = &mut *(ptr as *mut PairingContext);
    match ctx.decrypt(&in_bytes) {
        Some(pt) => env
            .byte_array_from_slice(&pt)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub unsafe extern "C" fn jni_PairingContext_Destroy(_env: *mut JNIEnv, _obj: jobject, ptr: jlong) {
    if ptr != 0 {
        // Safety: Reconstructing Box to deallocate PairingContext allocated in Constructor.
        // Invariant: Pointer was created by Box::into_raw and is now consumed and dropped.
        let _ = Box::from_raw(ptr as *mut PairingContext);
    }
}

// ---------------- AdbProtocolNative JNI ----------------

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_adb_AdbProtocolNative_nativeEncodeMessage(
    raw_env: *mut JNIEnv,
    _class: jclass,
    command: jint,
    arg0: jint,
    arg1: jint,
    j_data: jbyteArray,
) -> jbyteArray {
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };
    let data_vec = if j_data.is_null() {
        None
    } else {
        let arr = JByteArray::from_raw(j_data);
        match env.convert_byte_array(&arr) {
            Ok(v) => Some(v),
            Err(_) => return std::ptr::null_mut(),
        }
    };
    let encoded = protocol::encode_adb_message(command, arg0, arg1, data_vec.as_deref());
    env.byte_array_from_slice(&encoded)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_adb_AdbProtocolNative_nativeChecksum(
    raw_env: *mut JNIEnv,
    _class: jclass,
    j_data: jbyteArray,
) -> jint {
    if j_data.is_null() {
        return 0;
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return 0,
    };
    let arr = JByteArray::from_raw(j_data);
    match env.convert_byte_array(&arr) {
        Ok(v) => protocol::checksum_adb_data(&v),
        Err(_) => 0,
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_adb_AdbProtocolNative_nativeValidateMessage(
    raw_env: *mut JNIEnv,
    _class: jclass,
    command: jint,
    data_length: jint,
    data_crc32: jint,
    magic: jint,
    j_data: jbyteArray,
) -> jboolean {
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return JNI_FALSE,
    };
    let data_vec = if j_data.is_null() {
        None
    } else {
        let arr = JByteArray::from_raw(j_data);
        match env.convert_byte_array(&arr) {
            Ok(v) => Some(v),
            Err(_) => return JNI_FALSE,
        }
    };
    if protocol::validate_adb_message(command, data_length, data_crc32, magic, data_vec.as_deref())
    {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_adb_AdbProtocolNative_nativeEncodeRsaPublicKey(
    raw_env: *mut JNIEnv,
    _class: jclass,
    j_modulus_be: jbyteArray,
    exponent: jint,
    j_name: jbyteArray,
) -> jbyteArray {
    if j_modulus_be.is_null() || j_name.is_null() {
        return std::ptr::null_mut();
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };
    let mod_arr = JByteArray::from_raw(j_modulus_be);
    let name_arr = JByteArray::from_raw(j_name);
    let modulus = match env.convert_byte_array(&mod_arr) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let name = match env.convert_byte_array(&name_arr) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    match protocol::encode_adb_rsa_pubkey(&modulus, exponent, &name) {
        Some(encoded) => env
            .byte_array_from_slice(&encoded)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        None => std::ptr::null_mut(),
    }
}

// ---------------- StealthNative JNI ----------------

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_stealth_StealthNative_nativeSwapFiltered(
    raw_env: *mut JNIEnv,
    _class: jclass,
    j_data: jbyteArray,
    j_old: jbyteArray,
    j_repl: jbyteArray,
    is_utf16: jboolean,
) -> jbyteArray {
    if j_data.is_null() || j_old.is_null() || j_repl.is_null() {
        return std::ptr::null_mut();
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };
    let data = match env.convert_byte_array(&JByteArray::from_raw(j_data)) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let old = match env.convert_byte_array(&JByteArray::from_raw(j_old)) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let repl = match env.convert_byte_array(&JByteArray::from_raw(j_repl)) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let swapped = stealth::swap_filtered(&data, &old, &repl, is_utf16 != 0);
    env.byte_array_from_slice(&swapped)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_stealth_StealthNative_nativePatchManifestLabel(
    raw_env: *mut JNIEnv,
    _class: jclass,
    j_axml: jbyteArray,
    j_app_name: jstring,
) -> jbyteArray {
    if j_axml.is_null() || j_app_name.is_null() {
        return std::ptr::null_mut();
    }
    let mut env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };
    let axml = match env.convert_byte_array(&JByteArray::from_raw(j_axml)) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let jstr = JString::from_raw(j_app_name);
    let app_name: String = match env.get_string(&jstr) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let patched = stealth::patch_manifest_label(&axml, &app_name);
    env.byte_array_from_slice(&patched)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_stealth_StealthNative_nativeFixDex(
    raw_env: *mut JNIEnv,
    _class: jclass,
    j_dex: jbyteArray,
) -> jbyteArray {
    if j_dex.is_null() {
        return std::ptr::null_mut();
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };
    let dex = match env.convert_byte_array(&JByteArray::from_raw(j_dex)) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let fixed = stealth::fix_dex_header(&dex);
    env.byte_array_from_slice(&fixed)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_stealth_StealthNative_nativeCrc32(
    raw_env: *mut JNIEnv,
    _class: jclass,
    j_data: jbyteArray,
) -> jlong {
    if j_data.is_null() {
        return 0;
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return 0,
    };
    let data = match env.convert_byte_array(&JByteArray::from_raw(j_data)) {
        Ok(v) => v,
        Err(_) => return 0,
    };
    stealth::crc32_ieee(&data) as jlong
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_stealth_StealthNative_nativeSha256(
    raw_env: *mut JNIEnv,
    _class: jclass,
    j_data: jbyteArray,
) -> jbyteArray {
    if j_data.is_null() {
        return std::ptr::null_mut();
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };
    let data = match env.convert_byte_array(&JByteArray::from_raw(j_data)) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let digest = stealth::sha256_digest(&data);
    env.byte_array_from_slice(&digest)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_stealth_StealthNative_nativeComputeApkV2Digest(
    raw_env: *mut JNIEnv,
    _class: jclass,
    j_s1: jbyteArray,
    j_s2: jbyteArray,
    j_eocd: jbyteArray,
) -> jbyteArray {
    if j_s1.is_null() || j_s2.is_null() || j_eocd.is_null() {
        return std::ptr::null_mut();
    }
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };
    let s1 = match env.convert_byte_array(&JByteArray::from_raw(j_s1)) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let s2 = match env.convert_byte_array(&JByteArray::from_raw(j_s2)) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let eocd = match env.convert_byte_array(&JByteArray::from_raw(j_eocd)) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };
    let digest = stealth::compute_apk_v2_top_digest(&s1, &s2, &eocd);
    env.byte_array_from_slice(&digest)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

// ---------------- ModuleNative JNI ----------------

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_module_ModuleNative_nativeModuleFingerprint(
    raw_env: *mut JNIEnv,
    _class: jclass,
    j_dir_path: jstring,
) -> jstring {
    if j_dir_path.is_null() {
        return std::ptr::null_mut();
    }
    let mut env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => return std::ptr::null_mut(),
    };
    let jstr = JString::from_raw(j_dir_path);
    let dir_path: String = match env.get_string(&jstr) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    match module_io::compute_module_fingerprint(&dir_path) {
        Some(hex) => env
            .new_string(hex)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_moe_shizuku_manager_module_ModuleNative_nativeReadFdTail(
    raw_env: *mut JNIEnv,
    _class: jclass,
    fd: jint,
    max_chars: jint,
) -> jstring {
    let env = match jni::JNIEnv::from_raw(raw_env as *mut _) {
        Ok(e) => e,
        Err(_) => {
            if fd >= 0 {
                libc::close(fd);
            }
            return std::ptr::null_mut();
        }
    };
    let text = module_io::read_fd_tail(fd, max_chars.max(0) as usize);
    env.new_string(text)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

unsafe fn register_optional_class(
    env: *mut JNIEnv,
    class_name: &str,
    methods: &[JNINativeMethod],
) {
    let Ok(c_name) = CString::new(class_name) else {
        return;
    };
    let cls = (**env).FindClass.unwrap()(env, c_name.as_ptr());
    if cls.is_null() {
        if let Some(clear) = (**env).ExceptionClear {
            clear(env);
        }
        return;
    }
    if (**env).RegisterNatives.unwrap()(env, cls, methods.as_ptr(), methods.len() as jint) != 0 {
        if let Some(clear) = (**env).ExceptionClear {
            clear(env);
        }
    }
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

        let class_name = CString::new("moe/shizuku/manager/adb/PairingContext").unwrap();
        let class = (**env).FindClass.unwrap()(env, class_name.as_ptr());
        if class.is_null() {
            return JNI_ERR;
        }

        let methods = [
            JNINativeMethod {
                name: b"nativeConstructor\0".as_ptr() as *mut _,
                signature: b"(Z[B)J\0".as_ptr() as *mut _,
                fnPtr: jni_PairingContext_Constructor as *mut _,
            },
            JNINativeMethod {
                name: b"nativeMsg\0".as_ptr() as *mut _,
                signature: b"(J)[B\0".as_ptr() as *mut _,
                fnPtr: jni_PairingContext_Msg as *mut _,
            },
            JNINativeMethod {
                name: b"nativeInitCipher\0".as_ptr() as *mut _,
                signature: b"(J[B)Z\0".as_ptr() as *mut _,
                fnPtr: jni_PairingContext_InitCipher as *mut _,
            },
            JNINativeMethod {
                name: b"nativeEncrypt\0".as_ptr() as *mut _,
                signature: b"(J[B)[B\0".as_ptr() as *mut _,
                fnPtr: jni_PairingContext_Encrypt as *mut _,
            },
            JNINativeMethod {
                name: b"nativeDecrypt\0".as_ptr() as *mut _,
                signature: b"(J[B)[B\0".as_ptr() as *mut _,
                fnPtr: jni_PairingContext_Decrypt as *mut _,
            },
            JNINativeMethod {
                name: b"nativeDestroy\0".as_ptr() as *mut _,
                signature: b"(J)V\0".as_ptr() as *mut _,
                fnPtr: jni_PairingContext_Destroy as *mut _,
            },
        ];

        if (**env).RegisterNatives.unwrap()(
            env,
            class,
            methods.as_ptr(),
            methods.len() as jint,
        ) != 0
        {
            return JNI_ERR;
        }

        let client_methods = [JNINativeMethod {
            name: b"available\0".as_ptr() as *mut _,
            signature: b"()Z\0".as_ptr() as *mut _,
            fnPtr: jni_AdbPairingClient_available as *mut _,
        }];
        register_optional_class(
            env,
            "moe/shizuku/manager/adb/AdbPairingClient",
            &client_methods,
        );

        let protocol_methods = [
            JNINativeMethod {
                name: b"nativeEncodeMessage\0".as_ptr() as *mut _,
                signature: b"(III[B)[B\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_adb_AdbProtocolNative_nativeEncodeMessage
                    as *mut _,
            },
            JNINativeMethod {
                name: b"nativeChecksum\0".as_ptr() as *mut _,
                signature: b"([B)I\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_adb_AdbProtocolNative_nativeChecksum as *mut _,
            },
            JNINativeMethod {
                name: b"nativeValidateMessage\0".as_ptr() as *mut _,
                signature: b"(IIII[B)Z\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_adb_AdbProtocolNative_nativeValidateMessage
                    as *mut _,
            },
            JNINativeMethod {
                name: b"nativeEncodeRsaPublicKey\0".as_ptr() as *mut _,
                signature: b"([BI[B)[B\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_adb_AdbProtocolNative_nativeEncodeRsaPublicKey
                    as *mut _,
            },
        ];
        register_optional_class(
            env,
            "moe/shizuku/manager/adb/AdbProtocolNative",
            &protocol_methods,
        );

        let stealth_methods = [
            JNINativeMethod {
                name: b"nativeSwapFiltered\0".as_ptr() as *mut _,
                signature: b"([B[B[BZ)[B\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_stealth_StealthNative_nativeSwapFiltered as *mut _,
            },
            JNINativeMethod {
                name: b"nativePatchManifestLabel\0".as_ptr() as *mut _,
                signature: b"([BLjava/lang/String;)[B\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_stealth_StealthNative_nativePatchManifestLabel
                    as *mut _,
            },
            JNINativeMethod {
                name: b"nativeFixDex\0".as_ptr() as *mut _,
                signature: b"([B)[B\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_stealth_StealthNative_nativeFixDex as *mut _,
            },
            JNINativeMethod {
                name: b"nativeCrc32\0".as_ptr() as *mut _,
                signature: b"([B)J\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_stealth_StealthNative_nativeCrc32 as *mut _,
            },
            JNINativeMethod {
                name: b"nativeSha256\0".as_ptr() as *mut _,
                signature: b"([B)[B\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_stealth_StealthNative_nativeSha256 as *mut _,
            },
            JNINativeMethod {
                name: b"nativeComputeApkV2Digest\0".as_ptr() as *mut _,
                signature: b"([B[B[B)[B\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_stealth_StealthNative_nativeComputeApkV2Digest
                    as *mut _,
            },
        ];
        register_optional_class(
            env,
            "moe/shizuku/manager/stealth/StealthNative",
            &stealth_methods,
        );

        let module_methods = [
            JNINativeMethod {
                name: b"nativeModuleFingerprint\0".as_ptr() as *mut _,
                signature: b"(Ljava/lang/String;)Ljava/lang/String;\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_module_ModuleNative_nativeModuleFingerprint
                    as *mut _,
            },
            JNINativeMethod {
                name: b"nativeReadFdTail\0".as_ptr() as *mut _,
                signature: b"(II)Ljava/lang/String;\0".as_ptr() as *mut _,
                fnPtr: Java_moe_shizuku_manager_module_ModuleNative_nativeReadFdTail as *mut _,
            },
        ];
        register_optional_class(
            env,
            "moe/shizuku/manager/module/ModuleNative",
            &module_methods,
        );

        JNI_VERSION_1_6
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_adb_pairing_handshake() {
        let password = b"123456";

        let mut client = PairingContext::new(true, password).expect("client init");
        let mut server = PairingContext::new(false, password).expect("server init");

        let client_msg = client.msg().to_vec();
        let server_msg = server.msg().to_vec();

        assert!(client.init_cipher(&server_msg));
        assert!(server.init_cipher(&client_msg));

        let msg = b"Hello ADB Pairing from Client!";
        let enc = client.encrypt(msg).expect("client encrypt");
        let dec = server.decrypt(&enc).expect("server decrypt");
        assert_eq!(dec, msg);

        let smsg = b"Hello from Server!";
        let senc = server.encrypt(smsg).expect("server encrypt");
        let sdec = client.decrypt(&senc).expect("client decrypt");
        assert_eq!(sdec, smsg);
    }

    #[test]
    fn test_adb_protocol_framing() {
        let payload = b"host::\0";
        let pkt = protocol::encode_adb_message(0x4e584e43, 0x01000000, 4096, Some(payload));
        assert_eq!(pkt.len(), 24 + payload.len());
        let crc = protocol::checksum_adb_data(payload);
        assert!(protocol::validate_adb_message(
            0x4e584e43,
            payload.len() as i32,
            crc,
            0x4e584e43 ^ -1,
            Some(payload)
        ));
    }

    #[test]
    fn test_stealth_swap_and_dex() {
        let src = b"com.hamondev.shevery/com.hamondev.shevery.tasker.Plugin";
        let out = stealth::swap_filtered(src, b"com.hamondev.shevery", b"com.abcdefgh.ijklmno", false);
        assert_eq!(
            &out[..],
            b"com.abcdefgh.ijklmno/com.hamondev.shevery.tasker.Plugin"
        );

        let mut fake_dex = vec![0u8; 64];
        fake_dex[0..8].copy_from_slice(b"dex\n035\0");
        let fixed = stealth::fix_dex_header(&fake_dex);
        assert_eq!(fixed.len(), 64);
        assert_ne!(&fixed[12..32], &[0u8; 20]);
    }
}
