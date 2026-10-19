# Sandors Mirrorcast wire protocol

Both apps speak this protocol. Change it in both `Wire.kt` files together.

## Discovery

- mDNS / DNS-SD service type: `_sandorscast._tcp.`
- Service name: `Sandors-XXXX` (last 4 of `ANDROID_ID` on the Fire Stick).
- TXT records are unused. The port is the TCP control port.

After a successful PIN, the Pixel stores host, port, and name. The next launch tries that socket before scanning again.

## Control channel

TCP, default port **47821**. One UTF-8 JSON object per line (newline terminated). `TCP_NODELAY` is on.

Sender → receiver:

| `t` | When | Fields |
| --- | --- | --- |
| `hello` | Right after connect | `v` (1), `name`, `id` |
| `pin` | User entered the 4-digit code | `pin` |
| `config` | Encoder produced SPS/PPS | `w`, `h`, `fps`, `sps` (base64), `pps` (base64) |
| `volume` | Notification volume changed | `v` (0–100) |
| `pause` / `resume` | Media notification | — |
| `ping` | Every 2 seconds | — |
| `stop` | User stopped, or capture was revoked | — |

Receiver → sender:

| `t` | When | Fields |
| --- | --- | --- |
| `pin_required` | Hello accepted, code is on the TV | — |
| `auth_ok` | PIN matched | `videoPort`, `audioPort` |
| `auth_fail` | PIN rejected | `left` (tries remaining) |
| `pong` | Reply to ping | — |
| `busy` | Another phone already holds the stick | — |
| `stop` | Receiver is closing | — |

The PIN is 4 digits, generated with `SecureRandom`, and expires with the socket (180 second read timeout during pairing, 3 tries).

## Media datagrams

Separate UDP sockets. Video default **47822**, audio default **47823**. The `auth_ok` message carries the ports the receiver actually bound.

20-byte big-endian header, then payload. Payload max **1200** bytes so a datagram stays under a typical Wi-Fi MTU. Larger access units are fragmented. Every fragment of one access unit shares `sequence`.

| Offset | Type | Field |
| --- | --- | --- |
| 0 | u32 | Magic `0x534E4452` (`SNDR`) |
| 4 | u8 | Version `1` |
| 5 | u8 | Kind: `1` video, `2` audio, `3` codec config |
| 6 | u8 | Flags: bit 0 keyframe, bit 1 config |
| 7 | u8 | Reserved `0` |
| 8 | u32 | Frame sequence |
| 12 | u32 | Timestamp, milliseconds |
| 16 | u16 | Fragment index |
| 18 | u16 | Fragment count |

Video payload is Annex-B H.264 (Baseline, no B-frames). SPS and PPS are also delivered on the control socket before the first IDR, and prepended to each keyframe. Audio payload is PCM s16le, stereo, 48000 Hz, in ~20 ms chunks. Incomplete video frames are dropped after 50 ms rather than delaying the picture.

## Why this shape

Fire OS 7 is Android 9. It can hardware-decode H.264 on a `Surface`, and it has no Play Services. A containerized player such as ExoPlayer adds a demuxer and a jitter buffer measured in hundreds of milliseconds, so the receiver renders with `MediaCodec` directly. PCM avoids an AAC encoder delay on the Pixel. mDNS plus a one-time PIN is enough on a home LAN; the phones and sticks are not exposed to the internet.
