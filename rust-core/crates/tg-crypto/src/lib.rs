//! Модуль безопасной криптографии MTProto 2.0 для Telegram Client.
//! Защищает авторизационные и сессионные ключи от извлечения из оперативной памяти Android
//! благодаря типажам Zeroize и ZeroizeOnDrop.

use byteorder::{ByteOrder, LittleEndian};
use sha2::{Digest, Sha256};
use zeroize::{Zeroize, ZeroizeOnDrop};

/// 2048-битный (256-байтный) мастер-ключ авторизации MTProto (auth_key).
/// `ZeroizeOnDrop` гарантирует физическую перезапись буфера нулями при deallocation
/// (выходе из области видимости, очистке сессии или падении потока).
#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct AuthKey {
    /// Секретные байты ключа, полученные в процессе Diffie-Hellman обмена
    raw_key: [u8; 256],
    /// Младшие 64 бита SHA-256 хэша ключа (auth_key_id), открытый идентификатор
    auth_key_id: u64,
}

impl AuthKey {
    /// Создает AuthKey из сырых 256 байт и вычисляет его публичный `auth_key_id`
    pub fn from_raw(raw: [u8; 256]) -> Self {
        let mut hasher = Sha256::new();
        hasher.update(&raw);
        let digest = hasher.finalize();

        // По спецификации MTProto 2.0 auth_key_id — это 64 младших бита SHA256(auth_key)
        let auth_key_id = LittleEndian::read_u64(&digest[digest.len() - 8..]);

        Self {
            raw_key: raw,
            auth_key_id,
        }
    }

    /// Публичный 64-битный ID ключа, отправляемый в заголовке каждого зашифрованного пакета
    pub fn key_id(&self) -> u64 {
        self.auth_key_id
    }

    /// Безопасный доступ к сырым байтам только для расчетов AES-IGE
    #[inline]
    pub fn as_bytes(&self) -> &[u8; 256] {
        &self.raw_key
    }

    /// Вычисляет 128-битный msg_key для пакета:
    /// msg_key = substr(SHA256(auth_key[88..120] + plaintext), 0, 16)
    pub fn compute_msg_key(&self, plaintext: &[u8]) -> [u8; 16] {
        let mut hasher = Sha256::new();
        hasher.update(&self.raw_key[88..120]);
        hasher.update(plaintext);
        let result = hasher.finalize();

        let mut msg_key = [0u8; 16];
        msg_key.copy_from_slice(&result[0..16]);
        msg_key
    }
}

/// Вычисленные сессионные ключи AES-256 IGE для шифрования одного сообщения.
/// Все внутренние массивы защищены ZeroizeOnDrop.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct AesKeys {
    pub key: [u8; 32],
    pub iv: [u8; 32],
}

impl AesKeys {
    /// Деривация ключей AES и вектора инициализации (IV) по спецификации MTProto 2.0:
    /// Для сообщений от клиента серверу (`is_client = true`):
    /// - sha256_a = SHA256(msg_key + auth_key[0..36])
    /// - sha256_b = SHA256(auth_key[40..76] + msg_key)
    /// - aes_key = sha256_a[0..8] + sha256_b[8..24] + sha256_a[24..32]
    /// - aes_iv  = sha256_b[0..8] + sha256_a[8..24] + sha256_b[24..32]
    pub fn derive(auth_key: &AuthKey, msg_key: &[u8; 16], is_client: bool) -> Self {
        let x = if is_client { 0 } else { 8 };
        let k = auth_key.as_bytes();

        let mut hasher_a = Sha256::new();
        hasher_a.update(msg_key);
        hasher_a.update(&k[x..x + 36]);
        let sha_a = hasher_a.finalize();

        let mut hasher_b = Sha256::new();
        hasher_b.update(&k[x + 40..x + 76]);
        hasher_b.update(msg_key);
        let sha_b = hasher_b.finalize();

        let mut aes_key = [0u8; 32];
        aes_key[0..8].copy_from_slice(&sha_a[0..8]);
        aes_key[8..24].copy_from_slice(&sha_b[8..24]);
        aes_key[24..32].copy_from_slice(&sha_a[24..32]);

        let mut aes_iv = [0u8; 32];
        aes_iv[0..8].copy_from_slice(&sha_b[0..8]);
        aes_iv[8..24].copy_from_slice(&sha_a[8..24]);
        aes_iv[24..32].copy_from_slice(&sha_b[24..32]);

        Self {
            key: aes_key,
            iv: aes_iv,
        }
    }
}

/// Активная сессия MTProto с хранением auth_key, server_salt и порядковых номеров пакетов.
/// Весь контейнер автоматически очищается при завершении сессии или выходе пользователя.
#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct Session {
    pub session_id: u64,
    pub server_salt: u64,
    pub seq_no: i32,
    pub auth_key: AuthKey,
}

impl Session {
    pub fn new(auth_key: AuthKey) -> Self {
        // Генерация случайного session_id
        let session_id = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos() as u64)
            .unwrap_or(0x1337_c0de_cafe_babe);

        Self {
            session_id,
            server_salt: 0,
            seq_no: 0,
            auth_key,
        }
    }

    /// Принудительная немедленная очистка сессии из памяти (например, при логауте)
    pub fn purge(&mut self) {
        self.session_id.zeroize();
        self.server_salt.zeroize();
        self.seq_no.zeroize();
        self.auth_key.zeroize();
    }
}

/// Генератор тестового сессионного ключа для инициализации
pub fn generate_ephemeral_key() -> AuthKey {
    let mut raw = [0u8; 256];
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(123456789);

    for (idx, chunk) in raw.chunks_mut(8).enumerate() {
        let val = (now as u64).wrapping_mul(6364136223846793005).wrapping_add(idx as u64);
        LittleEndian::write_u64(chunk, val);
    }
    AuthKey::from_raw(raw)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_zeroize_memory_sanitization() {
        let mut key = generate_ephemeral_key();
        assert_ne!(key.key_id(), 0);
        assert_ne!(key.as_bytes(), &[0u8; 256]);

        // Вызов очистки
        key.zeroize();

        // Проверяем, что в памяти остались только нули
        assert_eq!(key.as_bytes(), &[0u8; 256]);
    }
}
