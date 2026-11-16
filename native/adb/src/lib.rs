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

// AOSP/BoringSSL pass these names with sizeof(), i.e. including the trailing NUL
// (16 bytes). Omitting the NUL changes the SPAKE2 transcript hash, derives a
// different AES key, and makes every correct pairing code fail to decrypt.
const CLIENT_NAME: &[u8] = b"adb pair client\0";
const SERVER_NAME: &[u8] = b"adb pair server\0";
const HKDF_INFO: &[u8] = b"adb pairing_auth aes-128-gcm key";

FIX_MARKER_PART1