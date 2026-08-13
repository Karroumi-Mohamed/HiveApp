import { type ReactNode, useEffect } from "react";
import { I18nextProvider, useTranslation } from "react-i18next";
import { DirectionProvider } from "@/components/ui/direction";
import { getLanguageDirection, i18n } from "./i18n";

function DirectionBoundary({ children }: { children: ReactNode }) {
  const { i18n: activeI18n } = useTranslation();
  const direction = getLanguageDirection(activeI18n.resolvedLanguage);

  useEffect(() => {
    const language = activeI18n.resolvedLanguage ?? "fr";
    document.documentElement.lang = language;
    document.documentElement.dir = direction;
    window.localStorage.setItem("hiveapp-language", language);
  }, [activeI18n.resolvedLanguage, direction]);

  return (
    <DirectionProvider dir={direction} direction={direction}>
      {children}
    </DirectionProvider>
  );
}

export function LocaleProvider({ children }: { children: ReactNode }) {
  return (
    <I18nextProvider i18n={i18n}>
      <DirectionBoundary>{children}</DirectionBoundary>
    </I18nextProvider>
  );
}
