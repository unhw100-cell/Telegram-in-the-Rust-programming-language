//! Модуль потоковой загрузки медиафайлов частями (Chunked Download).
//! Реализует паттерн MTProto upload.getFile: скачивание крупного файла блоками по 128 КБ
//! для оптимизации сетевого трафика и предотвращения OOM (Out Of Memory) на Android.

use crate::cache::TwoTierMediaCache;
use std::sync::Arc;
use std::time::Duration;
use thiserror::Error;
use tokio::sync::Mutex as TokioMutex;

pub const CHUNK_SIZE_128K: usize = 131072; // 128 КБ — оптимальный размер блока MTProto

#[derive(Debug, Error)]
pub enum DownloadError {
    #[error("Ошибка кэша: {0}")]
    Cache(#[from] crate::cache::CacheError),

    #[error("Сетевая ошибка при скачивании: {0}")]
    Network(String),

    #[error("Хэш контрольной суммы поврежден")]
    ChecksumMismatch,
}

/// Метаданные загружаемого медиафайла
#[derive(Debug, Clone)]
pub struct MediaLocation {
    pub file_id: i64,
    pub access_hash: i64,
    pub file_reference: Vec<u8>,
    pub total_size_bytes: usize,
    pub mime_type: String,
}

/// Асинхронный загрузчик медиафайлов частями
pub struct ChunkedDownloader {
    cache: Arc<TwoTierMediaCache>,
}

impl ChunkedDownloader {
    pub fn new(cache: Arc<TwoTierMediaCache>) -> Self {
        Self { cache }
    }

    /// Потоковое скачивание файла блоками по 128 КБ с вызовом progress callback
    pub async fn download_file<F>(
        &self,
        media_id: &str,
        location: &MediaLocation,
        mut on_progress: F,
    ) -> Result<Vec<u8>, DownloadError>
    where
        F: FnMut(usize, usize) + Send + 'static,
    {
        // 1. Проверяем кэш (L1 RAM -> L2 Disk)
        if let Some(cached_data) = self.cache.get(media_id) {
            on_progress(cached_data.len(), cached_data.len());
            return Ok(cached_data);
        }

        // 2. Если файла нет в кэше — скачиваем частями (MTProto chunked transfer)
        let total_size = location.total_size_bytes;
        let mut assembled_data = Vec::with_capacity(total_size);
        let mut current_offset: usize = 0;

        while current_offset < total_size {
            let chunk_limit = std::cmp::min(CHUNK_SIZE_128K, total_size - current_offset);

            // Симуляция MTProto RPC-запроса upload.getFile(location, offset, limit)
            let chunk = self
                .fetch_mtproto_chunk(location.file_id, current_offset, chunk_limit)
                .await?;

            assembled_data.extend_from_slice(&chunk);
            current_offset += chunk.len();

            // Уведомление UI о прогрессе (байт загружено / всего байт)
            on_progress(current_offset, total_size);

            // Короткая уступка планировщику Tokio для предотвращения зависания потока
            tokio::task::yield_now().await;
        }

        // 3. Сохраняем собранный файл в двухуровневый кэш
        self.cache.put(media_id, assembled_data.clone())?;

        Ok(assembled_data)
    }

    /// Имитация выполнения RPC MTProto upload.getFile для демонстрации архитектуры
    async fn fetch_mtproto_chunk(
        &self,
        _file_id: i64,
        offset: usize,
        limit: usize,
    ) -> Result<Vec<u8>, DownloadError> {
        // Небольшая задержка, имитирующая реальную сеть (20 мс на чанк 128 КБ)
        tokio::time::sleep(Duration::from_millis(20)).await;

        // Генерация валидных байт чанка с детерминированным заполнением
        let mut chunk = vec![0u8; limit];
        for (i, b) in chunk.iter_mut().enumerate() {
            *b = ((offset + i) % 256) as u8;
        }

        Ok(chunk)
    }
}
