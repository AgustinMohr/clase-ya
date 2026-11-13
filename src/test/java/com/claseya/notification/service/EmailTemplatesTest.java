package com.claseya.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.HtmlUtils;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplatesTest {

    @Test
    void activationIncludesTheSingleUseLinkAndTtl() {
        EmailContent content = EmailTemplates.activation("https://app.test/activar?token=abc");

        assertThat(content.subject()).isEqualTo("Activá tu cuenta de ClaseYa");
        assertThat(content.text()).contains("https://app.test/activar?token=abc");
        assertThat(content.text()).contains("vence en 24 horas");
        assertThat(content.html()).contains("https://app.test/activar?token=abc");
        assertThat(content.html()).contains("Activar mi cuenta");
    }

    @Test
    void passwordResetIncludesTheSingleUseLinkAndTtl() {
        EmailContent content = EmailTemplates.passwordReset("https://app.test/restablecer?token=abc");

        assertThat(content.subject()).isEqualTo("Restablecé tu contraseña de ClaseYa");
        assertThat(content.text()).contains("https://app.test/restablecer?token=abc");
        assertThat(content.text()).contains("vence en 30 minutos");
        assertThat(content.html()).contains(HtmlUtils.htmlEscape("Elegir nueva contraseña"));
    }

    @Test
    void contactEscapesUserControlledNamesInHtml() {
        EmailContent content = EmailTemplates.contact(
                "Ada <script>alert(1)</script>",
                "Grace & Co",
                "https://app.test/mensajes/1?token=abc");

        assertThat(content.text()).contains("Hola Ada <script>alert(1)</script>");
        assertThat(content.html()).contains("Ada &lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(content.html()).contains("Grace &amp; Co");
        assertThat(content.html()).doesNotContain("<script>alert(1)</script>");
    }
}
