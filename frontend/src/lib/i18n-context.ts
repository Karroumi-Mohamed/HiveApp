import { createContext, useContext } from 'react'

export type Locale = 'fr' | 'ar'

export type I18nContextValue = {
  locale: Locale
  dir: 'ltr' | 'rtl'
  setLocale: (locale: Locale) => void
  t: (key: string, fallback?: string) => string
}

export const I18nContext = createContext<I18nContextValue | undefined>(undefined)

export function useI18n() {
  const context = useContext(I18nContext)
  if (!context) {
    throw new Error('useI18n must be used inside I18nProvider')
  }
  return context
}

export function featureTranslationKey(code: string) {
  return `feature_${code.replaceAll('.', '_')}`
}
