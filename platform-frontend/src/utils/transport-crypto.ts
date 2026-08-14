import api from '@/api/http'

/**
 * 登录/改密敏感字段混合加密：RSA-OAEP(SHA-256) 包 AES 密钥 + AES-GCM 加密正文。
 * 账号与密码必须放入 payload，请求体中不得再出现明文。
 */

export type TransportEnvelope = {
  kid: string
  keyCipher: string
  iv: string
  cipherText: string
}

type PublicKeyInfo = {
  kid: string
  algorithm: string
  hash: string
  publicKey: string
}

let cachedKey: { info: PublicKeyInfo; cryptoKey: CryptoKey; loadedAt: number } | null = null
const KEY_TTL_MS = 5 * 60 * 1000

function b64Encode(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]!)
  }
  return btoa(binary)
}

function randomNonce(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return b64Encode(bytes).replace(/[+/=]/g, (c) => ({ '+': '-', '/': '_', '=': '' }[c] as string))
}

async function loadPublicKey(force = false): Promise<{ info: PublicKeyInfo; cryptoKey: CryptoKey }> {
  const now = Date.now()
  if (!force && cachedKey && now - cachedKey.loadedAt < KEY_TTL_MS) {
    return { info: cachedKey.info, cryptoKey: cachedKey.cryptoKey }
  }
  const res = await api.get('/auth/crypto/public-key')
  const info = res.data as PublicKeyInfo
  if (!info?.publicKey || !info.kid) {
    throw new Error('无法获取传输加密公钥')
  }
  const spki = Uint8Array.from(atob(info.publicKey), (c) => c.charCodeAt(0))
  const cryptoKey = await crypto.subtle.importKey(
    'spki',
    spki,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )
  cachedKey = { info, cryptoKey, loadedAt: now }
  return { info, cryptoKey }
}

/** 将敏感字段加密为传输信封；自动附加 ts、nonce。 */
export async function encryptTransportPayload(
  sensitive: Record<string, string | number>,
): Promise<TransportEnvelope> {
  const { info, cryptoKey: rsaKey } = await loadPublicKey()
  const aesKey = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt'])
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const plain = {
    ...sensitive,
    ts: Date.now(),
    nonce: randomNonce(),
  }
  const plainBytes = new TextEncoder().encode(JSON.stringify(plain))
  const cipherBuf = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, aesKey, plainBytes)
  const rawAes = await crypto.subtle.exportKey('raw', aesKey)
  const keyCipherBuf = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, rsaKey, rawAes)
  return {
    kid: info.kid,
    keyCipher: b64Encode(keyCipherBuf),
    iv: b64Encode(iv),
    cipherText: b64Encode(cipherBuf),
  }
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
}
