import { useI18n, type Locale } from '../lib/i18n-context'

export function LanguageToggle() {
  const { locale, setLocale } = useI18n()

  const options: Array<{ locale: Locale; label: string }> = [
    { locale: 'fr', label: 'FR' },
    { locale: 'ar', label: 'AR' },
  ]

  return (
    <div className="btn-group" role="group" aria-label="Language">
      {options.map((option) => (
        <button
          key={option.locale}
          type="button"
          className={`btn btn-sm ${locale === option.locale ? 'btn-dark' : 'btn-outline-secondary'}`}
          onClick={() => setLocale(option.locale)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
