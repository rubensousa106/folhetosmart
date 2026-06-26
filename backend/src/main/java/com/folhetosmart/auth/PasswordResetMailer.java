package com.folhetosmart.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Envia a palavra-passe temporária por email através de um <b>webhook do n8n</b>
 * (é o n8n que trata do envio real do email). Mantém o backend agnóstico ao
 * fornecedor de email.
 *
 * <p>Se o webhook não estiver configurado (dev), <b>regista a palavra-passe no
 * log</b> em vez de a enviar — assim o fluxo é testável localmente sem n8n.
 */
@Component
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final String webhookUrl;
    private final RestClient http = RestClient.create();

    public PasswordResetMailer(
            @Value("${folheto.password-reset.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * Pede ao n8n para enviar o email com a {@code tempPassword} para {@code email}.
     * <b>Nunca lança:</b> uma falha de envio não deve rebentar o pedido (o
     * utilizador recebe sempre a mesma resposta neutra).
     */
    public void sendTempPassword(String email, String name, String tempPassword, long validityMinutes) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("N8N_PASSWORD_RESET_WEBHOOK não definido — palavra-passe temporária (DEV) "
                    + "para {}: {}", email, tempPassword);
            return;
        }
        try {
            http.post()
                    .uri(webhookUrl)
                    .body(Map.of(
                            "email", email,
                            "name", name == null ? "" : name,
                            "temp_password", tempPassword,
                            "validity_minutes", validityMinutes))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Pedido de envio de palavra-passe temporária entregue ao n8n.");
        } catch (Exception e) {
            log.error("Falha ao contactar o webhook n8n para envio de email: {}", e.getMessage());
        }
    }
}
