//! Модуль сетевого подключения к дата-центрам Telegram по протоколу MTProto.
//! Использует асинхронный рантайм Tokio, TCP сокеты и буферы BytesMut.

use bytes::{Buf, BufMut, BytesMut};
use std::fmt;
use std::net::SocketAddr;
use std::time::Duration;
use thiserror::Error;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;
use tokio::time::timeout;

/// Типизированные сетевые ошибки
#[derive(Debug, Error)]
pub enum NetworkError {
    #[error("Таймаут подключения к серверу Telegram ({0:?})")]
    ConnectionTimeout(Duration),

    #[error("Ошибка сетевого сокета: {0}")]
    Io(#[from] std::io::Error),

    #[error("Сокет закрыт удаленной стороной (EOF)")]
    ConnectionReset,

    #[error("Сокет не подключен")]
    NotConnected,

    #[error("Некорректный MTProto фрейм: {0}")]
    InvalidFrame(String),

    #[error("Неизвестный дата-центр ID: {0}")]
    UnknownDataCenter(i32),
}

/// Конфигурация публичных дата-центров Telegram (IPv4:Port)
#[derive(Debug, Clone, Copy)]
pub struct DataCenterEndpoint {
    pub id: i32,
    pub ip: &'static str,
    pub port: u16,
    pub is_test: bool,
}

impl DataCenterEndpoint {
    pub const PROD_DC1: Self = Self { id: 1, ip: "149.154.167.40", port: 443, is_test: false };
    pub const PROD_DC2: Self = Self { id: 2, ip: "149.154.167.50", port: 443, is_test: false }; // Основной европейский DC
    pub const PROD_DC3: Self = Self { id: 3, ip: "149.154.167.51", port: 443, is_test: false };
    pub const PROD_DC4: Self = Self { id: 4, ip: "149.154.167.91", port: 443, is_test: false };
    pub const PROD_DC5: Self = Self { id: 5, ip: "91.108.56.165", port: 443, is_test: false };

    pub const TEST_DC2: Self = Self { id: 2, ip: "149.154.167.40", port: 443, is_test: true };

    pub fn lookup(id: i32, is_test: bool) -> Result<Self, NetworkError> {
        if is_test {
            return Ok(Self::TEST_DC2);
        }
        match id {
            1 => Ok(Self::PROD_DC1),
            2 => Ok(Self::PROD_DC2),
            3 => Ok(Self::PROD_DC3),
            4 => Ok(Self::PROD_DC4),
            5 => Ok(Self::PROD_DC5),
            _ => Err(NetworkError::UnknownDataCenter(id)),
        }
    }

    pub fn to_socket_addr(&self) -> Result<SocketAddr, NetworkError> {
        let addr_str = format!("{}:{}", self.ip, self.port);
        addr_str
            .parse()
            .map_err(|e| NetworkError::InvalidFrame(format!("Ошибка парсинга IP: {}", e)))
    }
}

/// Режимы транспорта MTProto:
/// - Abridged: самый компактный протокол (байт 0xef в начале), минимальный оверхед для мобильных сетей
/// - Intermediate: 4 байта длины (0xeeeeeeee в начале), удобен для отладки
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TransportKind {
    Abridged,
    Intermediate,
}

/// Управляющий объект асинхронного TCP-соединения с Telegram
pub struct TgConnection {
    endpoint: Option<DataCenterEndpoint>,
    stream: Option<TcpStream>,
    transport: TransportKind,
    read_buffer: BytesMut,
    write_buffer: BytesMut,
}

impl TgConnection {
    /// Создает новый экземпляр подключения (по умолчанию Abridged протокол)
    pub fn new(transport: TransportKind) -> Self {
        Self {
            endpoint: None,
            stream: None,
            transport,
            // Выделяем емкие буферы для снижения количества системных вызовов
            read_buffer: BytesMut::with_capacity(65536),
            write_buffer: BytesMut::with_capacity(65536),
        }
    }

    /// Асинхронное подключение к дата-центру Telegram с ограничением по таймауту
    pub async fn connect_dc(
        &mut self,
        dc_id: i32,
        is_test: bool,
        connect_timeout: Duration,
    ) -> Result<(), NetworkError> {
        let ep = DataCenterEndpoint::lookup(dc_id, is_test)?;
        let socket_addr = ep.to_socket_addr()?;

        // Ограничиваем время TCP-рукопожатия таймаутом
        let tcp_stream = timeout(connect_timeout, TcpStream::connect(socket_addr))
            .await
            .map_err(|_| NetworkError::ConnectionTimeout(connect_timeout))??;

        // КРИТИЧНО для мобильных ОС: отключаем алгоритм Nagle для мгновенной отправки пакетов без задержек
        tcp_stream.set_nodelay(true)?;

        let mut stream = tcp_stream;

        // Отправка стартового байта/маркера транспорта MTProto:
        // 0xEF для Abridged протокола
        match self.transport {
            TransportKind::Abridged => {
                stream.write_all(&[0xef]).await?;
            }
            TransportKind::Intermediate => {
                stream.write_all(&[0xee, 0xee, 0xee, 0xee]).await?;
            }
        }
        stream.flush().await?;

        self.endpoint = Some(ep);
        self.stream = Some(stream);
        self.read_buffer.clear();
        self.write_buffer.clear();

        Ok(())
    }

    /// Отправка пакета MTProto с правильным расчетом длины и выравниванием
    pub async fn send_packet(&mut self, payload: &[u8]) -> Result<(), NetworkError> {
        let stream = self.stream.as_mut().ok_or(NetworkError::NotConnected)?;

        self.write_buffer.clear();

        match self.transport {
            TransportKind::Abridged => {
                // В MTProto Abridged длина пакета указывается в 4-байтовых словах (payload.len() / 4)
                if payload.len() % 4 != 0 {
                    return Err(NetworkError::InvalidFrame(
                        "Размер MTProto полезной нагрузки должен быть кратен 4 байтам".to_string(),
                    ));
                }
                let word_len = payload.len() / 4;

                if word_len < 0x7f {
                    // Если длина влезает в 1 байт (< 127 слов / 508 байт)
                    self.write_buffer.put_u8(word_len as u8);
                } else {
                    // Длина >= 127: маркер 0x7f + 3 байта длины (little-endian)
                    self.write_buffer.put_u8(0x7f);
                    self.write_buffer.put_u8((word_len & 0xff) as u8);
                    self.write_buffer.put_u8(((word_len >> 8) & 0xff) as u8);
                    self.write_buffer.put_u8(((word_len >> 16) & 0xff) as u8);
                }
                self.write_buffer.put_slice(payload);
            }
            TransportKind::Intermediate => {
                // 4 байта длины little-endian
                self.write_buffer.put_u32_le(payload.len() as u32);
                self.write_buffer.put_slice(payload);
            }
        }

        // Запись всего буфера в сокет и сброс
        stream.write_all(&self.write_buffer).await?;
        stream.flush().await?;
        Ok(())
    }

    /// Асинхронное чтение очередного полного MTProto фрейма из потока
    pub async fn receive_packet(&mut self) -> Result<Vec<u8>, NetworkError> {
        let stream = self.stream.as_mut().ok_or(NetworkError::NotConnected)?;

        loop {
            // 1. Пытаемся декодировать фрейм из уже имеющихся в буфере байт
            if let Some(frame) = self.try_decode_frame()? {
                return Ok(frame);
            }

            // 2. Если данных недостаточно, читаем новую порцию из сокета
            let bytes_read = stream.read_buf(&mut self.read_buffer).await?;
            if bytes_read == 0 {
                // Удаленная сторона закрыла соединение
                return Err(NetworkError::ConnectionReset);
            }
        }
    }

    /// Попытка извлечь фрейм из read_buffer без блокировки
    fn try_decode_frame(&mut self) -> Result<Option<Vec<u8>>, NetworkError> {
        if self.read_buffer.is_empty() {
            return Ok(None);
        }

        match self.transport {
            TransportKind::Abridged => {
                let first_byte = self.read_buffer[0];
                let (header_len, payload_len) = if first_byte < 0x7f {
                    (1usize, (first_byte as usize) * 4)
                } else {
                    if self.read_buffer.len() < 4 {
                        return Ok(None); // Ждем остаток 4-байтного заголовка
                    }
                    let len = ((self.read_buffer[1] as usize)
                        | ((self.read_buffer[2] as usize) << 8)
                        | ((self.read_buffer[3] as usize) << 16))
                        * 4;
                    (4usize, len)
                };

                let total_frame_len = header_len + payload_len;
                if self.read_buffer.len() < total_frame_len {
                    return Ok(None); // Ждем оставшиеся байты тела пакета
                }

                // Извлекаем заголовок и тело фрейма
                self.read_buffer.advance(header_len);
                let payload = self.read_buffer.split_to(payload_len).to_vec();
                Ok(Some(payload))
            }
            TransportKind::Intermediate => {
                if self.read_buffer.len() < 4 {
                    return Ok(None);
                }
                let payload_len = (self.read_buffer[0] as usize)
                    | ((self.read_buffer[1] as usize) << 8)
                    | ((self.read_buffer[2] as usize) << 16)
                    | ((self.read_buffer[3] as usize) << 24);

                let total_len = 4 + payload_len;
                if self.read_buffer.len() < total_len {
                    return Ok(None);
                }

                self.read_buffer.advance(4);
                let payload = self.read_buffer.split_to(payload_len).to_vec();
                Ok(Some(payload))
            }
        }
    }

    /// Проверка активности подключения
    pub fn is_connected(&self) -> bool {
        self.stream.is_some()
    }

    /// Закрытие соединения и сброс внутренних буферов
    pub fn disconnect(&mut self) {
        self.stream = None;
        self.endpoint = None;
        self.read_buffer.clear();
        self.write_buffer.clear();
    }
}
