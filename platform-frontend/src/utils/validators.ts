import type { FormItemRule } from 'element-plus'

/** 中国大陆手机号：1[3-9] 开头共 11 位。空串视为通过（必填由调用方单独校验）。 */
const MOBILE_RE = /^1[3-9]\d{9}$/

/**
 * 国内座机：区号 0 开头 2～3 位 + 本地号 7～8 位，中间可带 `-`。
 * 例：010-12345678、0314-1234567、03141234567
 */
const LANDLINE_RE = /^0\d{2,3}-?\d{7,8}$/

/** 常见邮箱格式（非空时校验）。 */
const EMAIL_RE = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/

function normalize(value: unknown): string {
  return String(value ?? '').trim()
}

/** 多个值按分号/逗号（含中文）拆分。 */
export function splitContactList(value: string): string[] {
  return normalize(value)
    .split(/[;；,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

export function isMobilePhone(value: string): boolean {
  const v = normalize(value)
  if (!v) return true
  return MOBILE_RE.test(v)
}

export function isLandlinePhone(value: string): boolean {
  const v = normalize(value)
  if (!v) return true
  return LANDLINE_RE.test(v)
}

/** 手机号或座机（组织用户「联系方式」等）。 */
export function isContactPhone(value: string): boolean {
  const v = normalize(value)
  if (!v) return true
  return MOBILE_RE.test(v) || LANDLINE_RE.test(v)
}

export function isEmail(value: string): boolean {
  const v = normalize(value)
  if (!v) return true
  return EMAIL_RE.test(v)
}

/** 分号/逗号分隔的多个邮箱；空串通过。 */
export function isEmailList(value: string): boolean {
  const parts = splitContactList(value)
  if (!parts.length) return true
  return parts.every((p) => EMAIL_RE.test(p))
}

/** 分号/逗号分隔的多个手机号；空串通过。 */
export function isMobilePhoneList(value: string): boolean {
  const parts = splitContactList(value)
  if (!parts.length) return true
  return parts.every((p) => MOBILE_RE.test(p))
}

export type PhoneRuleOptions = {
  /** 默认 false；为 true 时空值报错 */
  required?: boolean
  /** 允许座机（组织联系方式等），默认 false */
  allowLandline?: boolean
  /** 覆盖默认错误文案 */
  message?: string
  trigger?: FormItemRule['trigger']
}

export type EmailRuleOptions = {
  required?: boolean
  message?: string
  /** 允许多个邮箱（分号/逗号分隔） */
  multiple?: boolean
  trigger?: FormItemRule['trigger']
}

/**
 * Element Plus 表单规则：手机号（可选座机）。
 * 默认文案「手机号格式不对」；允许座机时默认「联系电话格式不对」。
 */
export function phoneRule(options: PhoneRuleOptions = {}): FormItemRule {
  const allowLandline = !!options.allowLandline
  const message =
    options.message || (allowLandline ? '联系电话格式不对' : '手机号格式不对')
  const check = allowLandline ? isContactPhone : isMobilePhone
  return {
    validator: (_rule, value, callback) => {
      const v = normalize(value)
      if (!v) {
        if (options.required) callback(new Error(allowLandline ? '请填写联系电话' : '请填写手机号'))
        else callback()
        return
      }
      if (!check(v)) callback(new Error(message))
      else callback()
    },
    trigger: options.trigger ?? 'blur',
  }
}

/** Element Plus 表单规则：邮箱。默认文案「邮箱格式不对」。 */
export function emailRule(options: EmailRuleOptions = {}): FormItemRule {
  const message = options.message || '邮箱格式不对'
  return {
    validator: (_rule, value, callback) => {
      const v = normalize(value)
      if (!v) {
        if (options.required) callback(new Error('请填写邮箱'))
        else callback()
        return
      }
      const ok = options.multiple ? isEmailList(v) : isEmail(v)
      if (!ok) callback(new Error(message))
      else callback()
    },
    trigger: options.trigger ?? 'blur',
  }
}
