use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes128Gcm, Nonce};
use curve25519_dalek::edwards::{CompressedEdwardsY, EdwardsPoint};
use curve25519_dalek::scalar::Scalar;
use hkdf::Hkdf;
use jni::objects::JByteArray;
use jni::sys::{
    jboolean, jbyteArray, jclass, jint, jlong, jobject, JNIEnv, JNINativeMethod, JNI_ERR,
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

impl PairingContext {
    pub fn new(is_client: bool, password: &[u8]) -> Option<Self> {
        let point_m = CompressedEdwardsY(M_BYTES).decompress()?;
        let point_n = CompressedEdwardsY(N_BYTES).decompress()?;

        let mut private_tmp = [0u8; 64];
        rand::thread_rng().fill_bytes(&mut private_tmp);
        let private_scalar = Scalar::from_bytes_mod_order_wide(&private_tmp) * Scalar::from(8u64);
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
        let dh_shared_encoded = *dh_shared.compress().as_bytes();

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

        let key_material = hasher.finalize();

        let hk = Hkdf::<Sha256>::new(None, &key_material);
        let mut key = [0u8; 16];
        if hk.expand(HKDF_INFO, &mut key).is_err() {
            return false;
        }

        self.cipher = Some(Aes128Gcm::new_from_slice(&key).unwrap());
        true
    }

    pub fn encrypt(&mut self, plaintext: &[u8]) -> Option<Vec<u8>> {
        let cipher = self.cipher.as_ref()?;
        let mut nonce_bytes = [0u8; 12];
        nonce_bytes[..8].copy_from_slice(&self.enc_sequence.to_le_bytes());
        let nonce = Nonce::from_slice(&nonce_bytes);

        let ciphertext = cipher.encrypt(nonce, plaintext).ok()?;
        self.enc_sequence += 1;
        Some(ciphertext)
    }

    pub fn decrypt(&mut self, ciphertext: &[u8]) -> Option<Vec<u8>> {
        let cipher = self.cipher.as_ref()?;
        let mut nonce_bytes = [0u8; 12];
        nonce_bytes[..8].copy_from_slice(&self.dec_sequence.to_le_bytes());
        let nonce = Nonce::from_slice(&nonce_bytes);

        let plaintext = cipher.decrypt(nonce, ciphertext).ok()?;
        self.dec_sequence += 1;
        Some(plaintext)
    }
}

// ---------------- JNI Implementation ----------------

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
    let password = match env.convert_byte_array(&pswd_arr) {
        Ok(p) => p,
        Err(_) => return 0,
    };

    match PairingContext::new(is_client != 0, &password) {
        Some(ctx) => Box::into_raw(Box::new(ctx)) as jlong,
        None => 0,
    }
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
        Some(ct) => env.byte_array_from_slice(&ct).map(|a| a.into_raw()).unwrap_or(std::ptr::null_mut()),
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
        Some(pt) => env.byte_array_from_slice(&pt).map(|a| a.into_raw()).unwrap_or(std::ptr::null_mut()),
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
}
