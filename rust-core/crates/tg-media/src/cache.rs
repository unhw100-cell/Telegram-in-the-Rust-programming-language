//! Двухуровневый кэш медиафайлов:
//! L1: Оперативная память (RAM LRU-кэш для мгновенной отрисовки превью)
//! L2: Дисковый накопитель устройства (постоянное хранение видео, аудио, голосовых сообщений)

use parking_lot::RwLock;
use sha2::{Digest, Sha256};
use std::collections::{HashMap, VecDeque};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum CacheError {
    #[error("Ошибка файлового ввода-вывода кэша: {0}")]
    Io(#[from] std::io::Error),

    #[error("Файл не найден в кэше: {0}")]
    NotFound(String),
}

/// Элемент L1 RAM кэша
#[derive(Clone)]
struct RamEntry {
    data: Vec<u8>,
    byte_size: usize,
}

/// Потокобезопасный L1 LRU-кэш в ОЗУ с жестким лимитом памяти (byte quota)
struct L1RamCache {
    entries: HashMap<String, RamEntry>,
    order: VecDeque<String>,
    current_bytes: usize,
    max_bytes: usize,
}

impl L1RamCache {
    fn new(max_bytes: usize) -> Self {
        Self {
            entries: HashMap::new(),
            order: VecDeque::new(),
            current_bytes: 0,
            max_bytes,
        }
    }

    fn get(&mut self, key: &str) -> Option<Vec<u8>> {
        if let Some(entry) = self.entries.get(key) {
            // Перемещаем ключ в конец очереди (most recently used)
            if let Some(pos) = self.order.iter().position(|k| k == key) {
                self.order.remove(pos);
            }
            self.order.push_back(key.to_string());
            Some(entry.data.clone())
        } else {
            None
        }
    }

    fn put(&mut self, key: String, data: Vec<u8>) {
        let size = data.len();
        if size > self.max_bytes {
            return; // Файл больше всего бюджета L1 кэша
        }

        // Вытесняем старые элементы (LRU eviction), пока не освободим место
        while self.current_bytes + size > self.max_bytes {
            if let Some(oldest_key) = self.order.pop_front() {
                if let Some(removed) = self.entries.remove(&oldest_key) {
                    self.current_bytes = self.current_bytes.saturating_sub(removed.byte_size);
                }
            } else {
                break;
            }
        }

        self.current_bytes += size;
        self.entries.insert(key.clone(), RamEntry { data, byte_size: size });
        self.order.push_back(key);
    }

    fn clear(&mut self) {
        self.entries.clear();
        self.order.clear();
        self.current_bytes = 0;
    }
}

/// Фасад двухуровневого кэша (L1 RAM + L2 Disk)
#[derive(Clone)]
pub struct TwoTierMediaCache {
    l1_ram: Arc<RwLock<L1RamCache>>,
    disk_dir: PathBuf,
    max_disk_bytes: u64,
}

impl TwoTierMediaCache {
    /// Создает кэш:
    /// - l1_max_bytes: лимит ОЗУ (например, 16 МБ для превью и аватарок)
    /// - disk_dir: папка на накопителе устройства Android (`context.cacheDir.resolve("media")`)
    /// - max_disk_bytes: дисковая квота (например, 256 МБ или 1 ГБ)
    pub fn new<P: AsRef<Path>>(disk_dir: P, l1_max_bytes: usize, max_disk_bytes: u64) -> Result<Self, CacheError> {
        let path = disk_dir.as_ref().to_path_buf();
        if !path.exists() {
            fs::create_dir_all(&path)?;
        }

        Ok(Self {
            l1_ram: Arc::new(RwLock::new(L1RamCache::new(l1_max_bytes))),
            disk_dir: path,
            max_disk_bytes,
        })
    }

    /// Генерация детерминированного безопасного имени файла через SHA-256 хэш
    fn get_disk_path(&self, media_id: &str) -> PathBuf {
        let mut hasher = Sha256::new();
        hasher.update(media_id.as_bytes());
        let hash_hex = format!("{:x}", hasher.finalize());
        self.disk_dir.join(format!("{}.tgmedia", hash_hex))
    }

    /// Извлечение медиафайла:
    /// 1. Проверяем L1 RAM кэш -> если найден, мгновенный возврат (0 мс)
    /// 2. Если промах в L1 -> читаем из L2 Disk кэша -> подгружаем превью в L1
    pub fn get(&self, media_id: &str) -> Option<Vec<u8>> {
        // 1. Попытка чтения из L1 (RAM)
        {
            let mut l1 = self.l1_ram.write();
            if let Some(data) = l1.get(media_id) {
                return Some(data);
            }
        }

        // 2. Чтение из L2 (Disk)
        let disk_path = self.get_disk_path(media_id);
        if disk_path.exists() {
            if let Ok(data) = fs::read(&disk_path) {
                // Если файл небольшой (<= 256 КБ), повышаем его в L1 RAM
                if data.len() <= 256 * 1024 {
                    let mut l1 = self.l1_ram.write();
                    l1.put(media_id.to_string(), data.clone());
                }
                return Some(data);
            }
        }

        None
    }

    /// Сохранение медиафайла в оба уровня кэша
    pub fn put(&self, media_id: &str, data: Vec<u8>) -> Result<(), CacheError> {
        // 1. Запись в L1 RAM (если файл небольшой, для быстрой отрисовки в UI)
        if data.len() <= 512 * 1024 {
            let mut l1 = self.l1_ram.write();
            l1.put(media_id.to_string(), data.clone());
        }

        // 2. Асинхронная или синхронная запись в L2 Disk
        let disk_path = self.get_disk_path(media_id);
        fs::write(disk_path, data)?;

        Ok(())
    }

    /// Полная очистка кэша (по запросу пользователя в настройках хранилища)
    pub fn clear(&self) -> Result<(), CacheError> {
        {
            let mut l1 = self.l1_ram.write();
            l1.clear();
        }

        if self.disk_dir.exists() {
            for entry in fs::read_dir(&self.disk_dir)? {
                let entry = entry?;
                let path = entry.path();
                if path.is_file() {
                    let _ = fs::remove_file(path);
                }
            }
        }
        Ok(())
    }

    /// Получение текущего размера дискового кэша в байтах
    pub fn get_disk_usage_bytes(&self) -> u64 {
        let mut total = 0u64;
        if let Ok(entries) = fs::read_dir(&self.disk_dir) {
            for entry in entries.flatten() {
                if let Ok(meta) = entry.metadata() {
                    if meta.is_file() {
                        total += meta.len();
                    }
                }
            }
        }
        total
    }
}
