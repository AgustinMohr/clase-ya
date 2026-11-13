package com.claseya.notification.service;

import org.springframework.web.util.HtmlUtils;

public final class EmailTemplates {

    private EmailTemplates() {
    }

    public static EmailContent activation(String activationUrl) {
        String subject = "Activá tu cuenta de ClaseYa";
        String text = """
                Hola,

                Para activar tu cuenta de ClaseYa abrí este enlace:

                %s

                El enlace vence en 24 horas y se puede usar una sola vez.
                Si no creaste esta cuenta, podés ignorar este mensaje.
                """.formatted(activationUrl);
        String html = layout(
                "Activá tu cuenta",
                "<p>Hola,</p><p>Para activar tu cuenta de ClaseYa abrí este enlace:</p>",
                "Activar mi cuenta",
                activationUrl,
                "<p>El enlace vence en 24 horas y se puede usar una sola vez. Si no creaste esta cuenta, podés ignorar este mensaje.</p>");
        return new EmailContent(subject, text, html);
    }

    public static EmailContent passwordReset(String resetUrl) {
        String subject = "Restablecé tu contraseña de ClaseYa";
        String text = """
                Hola,

                Para elegir una nueva contraseña de ClaseYa abrí este enlace:

                %s

                El enlace vence en 30 minutos y se puede usar una sola vez.
                Si no lo pediste, podés ignorar este mensaje.
                """.formatted(resetUrl);
        String html = layout(
                "Restablecé tu contraseña",
                "<p>Hola,</p><p>Para elegir una nueva contraseña de ClaseYa abrí este enlace:</p>",
                "Elegir nueva contraseña",
                resetUrl,
                "<p>El enlace vence en 30 minutos y se puede usar una sola vez. Si no lo pediste, podés ignorar este mensaje.</p>");
        return new EmailContent(subject, text, html);
    }

    public static EmailContent contact(String teacherName, String studentName, String link) {
        String greeting = teacherName == null || teacherName.isBlank()
                ? "Hola"
                : "Hola " + teacherName;
        String student = studentName == null || studentName.isBlank() ? "Un estudiante" : studentName;
        String subject = "Tenés un nuevo contacto en ClaseYa";
        String text = """
                %s,

                %s quiere contactarte para coordinar clases. Podés abrir la conversación con este
                enlace:

                %s

                El enlace vence en 7 días y se puede usar una sola vez.
                Para responder, entrá a ClaseYa. No compartas este correo.
                """.formatted(greeting, student, link);
        String html = layout(
                "Tenés un nuevo contacto",
                "<p>%s,</p><p><strong>%s</strong> quiere contactarte para coordinar clases.</p>"
                        .formatted(HtmlUtils.htmlEscape(greeting), HtmlUtils.htmlEscape(student)),
                "Abrir conversación",
                link,
                "<p>El enlace vence en 7 días y se puede usar una sola vez. Para responder, entrá a ClaseYa. No compartas este correo.</p>");
        return new EmailContent(subject, text, html);
    }

    private static String layout(String title, String body, String cta, String link, String footer) {
        String safeLink = HtmlUtils.htmlEscape(link);
        return """
                <!doctype html>
                <html lang="es">
                  <body style="margin:0;padding:0;background:#f8fafc;font-family:Arial,sans-serif;color:#0f172a;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f8fafc;padding:24px 12px;">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:520px;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
                            <tr><td style="font-size:22px;font-weight:700;color:#1d4ed8;padding-bottom:20px;">ClaseYa</td></tr>
                            <tr><td style="font-size:22px;font-weight:700;padding-bottom:16px;">%s</td></tr>
                            <tr><td style="font-size:15px;line-height:1.6;color:#334155;padding-bottom:20px;">%s</td></tr>
                            <tr>
                              <td align="center" style="padding-bottom:20px;">
                                <a href="%s" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;font-weight:700;padding:12px 22px;border-radius:10px;">%s</a>
                              </td>
                            </tr>
                            <tr><td style="font-size:13px;line-height:1.6;color:#64748b;padding-bottom:16px;">Si el botón no funciona, copiá este enlace:<br><a href="%s" style="color:#2563eb;word-break:break-all;">%s</a></td></tr>
                            <tr><td style="font-size:13px;line-height:1.6;color:#64748b;">%s</td></tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(title),
                body,
                safeLink,
                HtmlUtils.htmlEscape(cta),
                safeLink,
                safeLink,
                footer);
    }
}
