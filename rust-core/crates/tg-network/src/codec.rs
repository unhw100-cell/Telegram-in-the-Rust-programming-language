//! MTProto Abridged and Intermediate framing codecs.

use bytes::{Buf, BufMut, BytesMut};
use std::io;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TransportProtocol {
    Abridged,            // Initial byte 0xef
    Intermediate,        // Initial byte 0xeeeeeeee
    PaddedIntermediate,  // Initial byte 0xdddddddd
}

pub struct Codec {
    protocol: TransportProtocol,
}

impl Codec {
    pub fn new(protocol: TransportProtocol) -> Self {
        Self { protocol }
    }

    /// Returns the initial transport handshake byte(s)
    pub fn handshake_bytes(&self) -> &'static [u8] {
        match self.protocol {
            TransportProtocol::Abridged => &[0xef],
            TransportProtocol::Intermediate => &[0xee, 0xee, 0xee, 0xee],
            TransportProtocol::PaddedIntermediate => &[0xdd, 0xdd, 0xdd, 0xdd],
        }
    }

    /// Encodes a payload into the wire format
    pub fn encode_frame(&self, payload: &[u8], dst: &mut BytesMut) {
        match self.protocol {
            TransportProtocol::Abridged => {
                let payload_len = payload.len();
                assert!(payload_len % 4 == 0, "MTProto payload must be 4-byte aligned");
                let word_len = payload_len / 4;
                if word_len < 0x7f {
                    dst.put_u8(word_len as u8);
                } else {
                    dst.put_u8(0x7f);
                    dst.put_u8((word_len & 0xff) as u8);
                    dst.put_u8(((word_len >> 8) & 0xff) as u8);
                    dst.put_u8(((word_len >> 16) & 0xff) as u8);
                }
                dst.put_slice(payload);
            }
            TransportProtocol::Intermediate | TransportProtocol::PaddedIntermediate => {
                dst.put_u32_le(payload.len() as u32);
                dst.put_slice(payload);
            }
        }
    }

    /// Attempts to decode one full frame from the buffer
    pub fn decode_frame(&self, src: &mut BytesMut) -> io::Result<Option<Vec<u8>>> {
        match self.protocol {
            TransportProtocol::Abridged => {
                if src.is_empty() {
                    return Ok(None);
                }
                let first = src[0];
                let (header_len, payload_len) = if first < 0x7f {
                    (1, (first as usize) * 4)
                } else {
                    if src.len() < 4 {
                        return Ok(None);
                    }
                    let len = ((src[1] as usize)
                        | ((src[2] as usize) << 8)
                        | ((src[3] as usize) << 16))
                        * 4;
                    (4, len)
                };

                let total_len = header_len + payload_len;
                if src.len() < total_len {
                    return Ok(None);
                }

                src.advance(header_len);
                let frame = src.split_to(payload_len).to_vec();
                Ok(Some(frame))
            }
            TransportProtocol::Intermediate | TransportProtocol::PaddedIntermediate => {
                if src.len() < 4 {
                    return Ok(None);
                }
                let payload_len = ((src[0] as usize)
                    | ((src[1] as usize) << 8)
                    | ((src[2] as usize) << 16)
                    | ((src[3] as usize) << 24));

                let total_len = 4 + payload_len;
                if src.len() < total_len {
                    return Ok(None);
                }

                src.advance(4);
                let frame = src.split_to(payload_len).to_vec();
                Ok(Some(frame))
            }
        }
    }
}
