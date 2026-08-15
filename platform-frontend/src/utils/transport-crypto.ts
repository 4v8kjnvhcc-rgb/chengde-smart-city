import forge from 'node-forge'
import api from '@/api/http'

/**
 * 登录/改密敏感字段混合加密：RSA-OAEP(SHA-256) 包 AES 密钥 + AES-GCM 加密正文。
 * 账号与密码必须放入 payload，请求体中不得再出现明文。
 *
 * 生产门户若为 HTTP（非 localhost），浏览器不提供 crypto.subtle，回退 node-forge。
 */

export type TransportEnvelope = {
  kid: string
  keyCipher: string
  iv: string
  cipherText: string
}

/** 请求加密会话：AES 密钥仅留内存，用于解开登录回包。 */
export type TransportSession = {
  envelope: TransportEnvelope
  aesKey: Uint8Array
}

type PublicKeyInfo = {
  kid: string
  algorithm: string
  hash: string
  publicKey: string
}

let cachedKey: { info: PublicKeyInfo; cryptoKey: CryptoKey; loadedAt: number } | null = null
let cachedForgeKey: { info: PublicKeyInfo; publicKey: forge.pki.rsa.PublicKey; loadedAt: number } | null = null
const KEY_TTL_MS = 5 * 60 * 1000

function subtleAvailable(): boolean {
  return typeof globalThis.crypto !== 'undefined' && typeof globalThis.crypto.subtle !== 'undefined'
}

function b64Encode(buf: ArrayBuffer | Uint8Array | string): string {
  if (typeof buf === 'string') {
    return forge.util.encode64(buf)
  }
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]!)
  }
  return btoa(binary)
}

function randomBytes(length: number): Uint8Array {
  if (globalThis.crypto?.getRandomValues) {
    const buf = new Uint8Array(length)
    globalThis.crypto.getRandomValues(buf)
    return buf
  }
  const raw = forge.random.getBytesSync(length)
  return Uint8Array.from(raw, (c) => c.charCodeAt(0))
}

function randomNonce(): string {
  const bytes = randomBytes(16)
  return b64Encode(bytes).replace(/[+/=]/g, (c) => ({ '+': '-', '/': '_', '=': '' }[c] as string))
}

async function fetchPublicKeyInfo(force = false): Promise<PublicKeyInfo> {
  const now = Date.now()
  if (!force && cachedKey && now - cachedKey.loadedAt < KEY_TTL_MS) {
    return cachedKey.info
  }
  if (!force && cachedForgeKey && now - cachedForgeKey.loadedAt < KEY_TTL_MS) {
    return cachedForgeKey.info
  }
  const res = await api.get('/auth/crypto/public-key')
  const info = res.data as PublicKeyInfo
  if (!info?.publicKey || !info.kid) {
    throw new Error('无法获取传输加密公钥')
  }
  return info
}

async function loadWebCryptoPublicKey(force = false): Promise<{ info: PublicKeyInfo; cryptoKey: CryptoKey }> {
  const now = Date.now()
  if (!force && cachedKey && now - cachedKey.loadedAt < KEY_TTL_MS) {
    return { info: cachedKey.info, cryptoKey: cachedKey.cryptoKey }
  }
  const info = await fetchPublicKeyInfo(force)
  const spki = Uint8Array.from(atob(info.publicKey), (c) => c.charCodeAt(0))
  const cryptoKey = await globalThis.crypto.subtle.importKey(
    'spki',
    spki,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )
  cachedKey = { info, cryptoKey, loadedAt: now }
  return { info, cryptoKey }
}

async function loadForgePublicKey(force = false): Promise<{ info: PublicKeyInfo; publicKey: forge.pki.rsa.PublicKey }> {
  const now = Date.now()
  if (!force && cachedForgeKey && now - cachedForgeKey.loadedAt < KEY_TTL_MS) {
    return { info: cachedForgeKey.info, publicKey: cachedForgeKey.publicKey }
  }
  const info = await fetchPublicKeyInfo(force)
  const asn1 = forge.asn1.fromDer(forge.util.decode64(info.publicKey))
  const publicKey = forge.pki.publicKeyFromAsn1(asn1) as forge.pki.rsa.PublicKey
  cachedForgeKey = { info, publicKey, loadedAt: now }
  return { info, publicKey }
}

function bytesToBinary(buf: Uint8Array): string {
  let s = ''
  for (let i = 0; i < buf.length; i++) {
    s += String.fromCharCode(buf[i]!)
  }
  return s
}

function b64DecodeToBytes(s: string): Uint8Array {
  const bin = atob(s)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) {
    out[i] = bin.charCodeAt(i)
  }
  return out
}

function encryptWithForge(
  info: PublicKeyInfo,
  publicKey: forge.pki.rsa.PublicKey,
  sensitive: Record<string, string | number>,
): TransportSession {
  const aesKey = forge.random.getBytesSync(32)
  const iv = forge.random.getBytesSync(12)
  const plain = {
    ...sensitive,
    ts: Date.now(),
    nonce: randomNonce(),
  }
  const plainBytes = new TextEncoder().encode(JSON.stringify(plain))
  const cipher = forge.cipher.createCipher('AES-GCM', forge.util.createBuffer(aesKey))
  cipher.start({ iv: forge.util.createBuffer(iv), tagLength: 128 })
  cipher.update(forge.util.createBuffer(plainBytes))
  cipher.finish()
  const cipherText = cipher.output.getBytes() + cipher.mode.tag.getBytes()
  const keyCipher = publicKey.encrypt(aesKey, 'RSA-OAEP', {
    md: forge.md.sha256.create(),
    mgf1: { md: forge.md.sha256.create() },
  })
  return {
    envelope: {
      kid: info.kid,
      keyCipher: b64Encode(keyCipher),
      iv: b64Encode(iv),
      cipherText: b64Encode(cipherText),
    },
    aesKey: Uint8Array.from(aesKey, (c) => c.charCodeAt(0)),
  }
}

async function encryptWithSubtle(
  sensitive: Record<string, string | number>,
): Promise<TransportSession> {
  const { info, cryptoKey: rsaKey } = await loadWebCryptoPublicKey()
  const subtle = globalThis.crypto.subtle
  const aesKey = await subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])
  const iv = randomBytes(12)
  const plain = {
    ...sensitive,
    ts: Date.now(),
    nonce: randomNonce(),
  }
  const plainBytes = new TextEncoder().encode(JSON.stringify(plain))
  const cipherBuf = await subtle.encrypt({ name: 'AES-GCM', iv }, aesKey, plainBytes)
  const rawAes = await subtle.exportKey('raw', aesKey)
  const keyCipherBuf = await subtle.encrypt({ name: 'RSA-OAEP' }, rsaKey, rawAes)
  return {
    envelope: {
      kid: info.kid,
      keyCipher: b64Encode(keyCipherBuf),
      iv: b64Encode(iv),
      cipherText: b64Encode(cipherBuf),
    },
    aesKey: new Uint8Array(rawAes),
  }
}

async function decryptWithSubtle(aesKey: Uint8Array, ivB64: string, cipherB64: string): Promise<unknown> {
  const key = await globalThis.crypto.subtle.importKey(
    'raw',
    aesKey,
    { name: 'AES-GCM' },
    false,
    ['decrypt'],
  )
  const iv = b64DecodeToBytes(ivB64)
  const data = b64DecodeToBytes(cipherB64)
  const plain = await globalThis.crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, data)
  return JSON.parse(new TextDecoder().decode(plain))
}

function decryptWithForge(aesKey: Uint8Array, ivB64: string, cipherB64: string): unknown {
  const iv = forge.util.decode64(ivB64)
  const raw = forge.util.decode64(cipherB64)
  const tag = raw.slice(raw.length - 16)
  const ct = raw.slice(0, raw.length - 16)
  const dec = forge.cipher.createDecipher('AES-GCM', forge.util.createBuffer(bytesToBinary(aesKey)))
  dec.start({
    iv: forge.util.createBuffer(iv),
    tag: forge.util.createBuffer(tag),
    tagLength: 128,
  })
  dec.update(forge.util.createBuffer(ct))
  if (!dec.finish()) {
    throw new Error('登录回包解密失败')
  }
  const outBytes = Uint8Array.from(dec.output.getBytes(), (c) => c.charCodeAt(0))
  return JSON.parse(new TextDecoder().decode(outBytes))
}

/** 将敏感字段加密为传输会话（信封 + 内存 AES 密钥）。 */
export async function encryptTransportSession(
  sensitive: Record<string, string | number>,
): Promise<TransportSession> {
  if (subtleAvailable()) {
    return encryptWithSubtle(sensitive)
  }
  const { info, publicKey } = await loadForgePublicKey()
  return encryptWithForge(info, publicKey, sensitive)
}

/** 将敏感字段加密为传输信封；自动附加 ts、nonce。 */
export async function encryptTransportPayload(
  sensitive: Record<string, string | number>,
): Promise<TransportEnvelope> {
  return (await encryptTransportSession(sensitive)).envelope
}

/** 用登录请求同一把 AES 密钥解开回包（新 IV）。 */
export async function decryptTransportCipher(
  aesKey: Uint8Array,
  ivB64: string,
  cipherB64: string,
): Promise<unknown> {
  if (!ivB64 || !cipherB64) {
    throw new Error('登录回包格式无效')
  }
  if (subtleAvailable()) {
    return decryptWithSubtle(aesKey, ivB64, cipherB64)
  }
  return decryptWithForge(aesKey, ivB64, cipherB64)
}

/** 有明文口令时挂到 passwordTransport，并去掉 body.password。 */
export async function withPasswordTransport(
  body: Record<string, unknown>,
  password?: string | null,
): Promise<Record<string, unknown>> {
  const next = { ...body }
  delete next.password
  if (password != null && String(password).length > 0) {
    next.passwordTransport = await encryptTransportPayload({ password: String(password) })
  }
  return next
}

export function clearTransportCryptoCache() {
  cachedKey = null
  cachedForgeKey = null
}
