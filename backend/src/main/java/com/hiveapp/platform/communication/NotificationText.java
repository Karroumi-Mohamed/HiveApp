package com.hiveapp.platform.communication;

import java.util.Locale;

/** Code-owned, privacy-safe automatic templates. Manually written content is never translated. */
public final class NotificationText {
  private NotificationText() {}

  public record Content(String title, String body) {}

  public static Content forType(CoreNotification type, Locale locale) {
    boolean ar = "ar".equals(locale.getLanguage());
    return switch (type) {
      case INTERNAL_INFORMATION, OFFER_AVAILABLE -> null;
      case MEMBER_CREATED ->
          ar
              ? new Content(
                  "عضو جديد",
                  "تمت إضافة عضو إلى حسابك. افتح قائمة الأعضاء للاطلاع على التفاصيل المسموح بها.")
              : new Content(
                  "Nouveau membre",
                  "Un membre a été ajouté à votre compte. Consultez les détails autorisés dans la"
                      + " liste des membres.");
      case MEMBER_ACCESS_CHANGED ->
          ar
              ? new Content(
                  "تغيير صلاحية عضو", "تم تغيير وصول أحد أعضاء حسابك. راجع التفاصيل المسموح بها.")
              : new Content(
                  "Accès d’un membre modifié",
                  "L’accès d’un membre de votre compte a été modifié. Consultez les détails"
                      + " autorisés.");
      case B2B_REQUEST ->
          ar
              ? new Content(
                  "طلب تعاون وارد",
                  "تلقيت طلب تعاون. افتح الطلب لمراجعته واتخاذ الإجراء المسموح به.")
              : new Content(
                  "Demande de collaboration reçue",
                  "Une demande de collaboration attend votre examen. Ouvrez le dossier pour agir"
                      + " selon vos autorisations.");
      case B2B_REQUEST_SENT ->
          ar
              ? new Content(
                  "تم إرسال الطلب", "تم إرسال طلب التعاون الخاص بك. أنت في انتظار رد مقدم الخدمة.")
              : new Content(
                  "Demande envoyée",
                  "Votre demande de collaboration a été envoyée. La réponse du prestataire est en"
                      + " attente.");
      case B2B_CHANGED ->
          ar
              ? new Content(
                  "تحديث التعاون",
                  "تغيرت حالة التعاون. افتح الملف للاطلاع على الحالة الحالية والتفاصيل المسموح"
                      + " بها.")
              : new Content(
                  "Collaboration mise à jour",
                  "La collaboration a changé d’état. Consultez le dossier pour son état actuel et"
                      + " les détails autorisés.");
      case PAYMENT_RECEIVED ->
          ar
              ? new Content(
                  "تم تسجيل الدفع",
                  "تم تأكيد دفع فاتورتك. راجع المستند للاطلاع على المبالغ والشروط.")
              : new Content(
                  "Paiement enregistré",
                  "Le règlement de votre facture a été confirmé. Consultez le document pour les"
                      + " montants et conditions.");
      case PAYMENT_FAILED ->
          ar
              ? new Content(
                  "لم يكتمل الدفع", "لم يكتمل دفع فاتورتك. راجع الفوترة قبل المحاولة مجددا.")
              : new Content(
                  "Paiement non abouti",
                  "Le paiement de votre facture n’a pas abouti. Consultez la facturation avant de"
                      + " réessayer.");
      case BILLING_ATTENTION ->
          ar
              ? new Content(
                  "دفع يحتاج إلى المراجعة", "فشلت محاولة دفع. راجع الفاتورة والعمليات المسموح بها.")
              : new Content(
                  "Paiement à vérifier",
                  "Une tentative de paiement a échoué. Consultez la facture et ses opérations"
                      + " autorisées.");
    };
  }

  public static Content repricing(Locale locale) {
    return "ar".equals(locale.getLanguage())
        ? new Content("سعر اشتراكك", "تم إعداد تغيير في السعر. راجع حالته وشروطه.")
        : new Content(
            "Tarif de votre abonnement",
            "Un changement de tarif a été préparé. Consultez son état et ses conditions.");
  }

  public static String emailSubject(String language) {
    return "ar".equals(language) ? "HiveApp — إشعار" : "HiveApp — Notification";
  }

  public static String emailPrompt(String language) {
    return "ar".equals(language)
        ? "يوجد إشعار لك في HiveApp. سجل الدخول للاطلاع عليه."
        : "Une notification vous attend dans HiveApp. Connectez-vous pour la consulter.";
  }
}
