package com.folhetosmart.config;

import com.folhetosmart.auth.Role;
import com.folhetosmart.auth.User;
import com.folhetosmart.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante a existência de uma conta de administrador no arranque, a partir de
 * variáveis de ambiente. É necessário porque o registo normal cria sempre
 * contas {@code USER} e os endpoints de processamento ({@code /sync/trigger},
 * {@code /sync/upload}) são só-ADMIN.
 *
 * <ul>
 *   <li>{@code FOLHETO_ADMIN_EMAIL} definido + utilizador já existe → promove a ADMIN;</li>
 *   <li>{@code FOLHETO_ADMIN_EMAIL} + {@code FOLHETO_ADMIN_PASSWORD} + não existe → cria a conta ADMIN;</li>
 *   <li>sem {@code FOLHETO_ADMIN_EMAIL} → não faz nada (dev local).</li>
 * </ul>
 *
 * O email tem de ser exatamente o mesmo usado no registo / no login do cron.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(UserRepository users,
                          PasswordEncoder passwordEncoder,
                          @Value("${folheto.admin.email:}") String adminEmail,
                          @Value("${folheto.admin.password:}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;   // sem admin configurado (dev local) — nada a fazer
        }
        final String email = adminEmail.trim();

        users.findByEmail(email).ifPresentOrElse(user -> {
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                users.save(user);
                log.info("Bootstrap: conta {} promovida a ADMIN.", mask(email));
            } else {
                log.info("Bootstrap: conta {} já é ADMIN.", mask(email));
            }
        }, () -> {
            if (adminPassword == null || adminPassword.isBlank()) {
                log.warn("Bootstrap: {} ainda não está registado e FOLHETO_ADMIN_PASSWORD "
                        + "não está definido — admin não criado.", mask(email));
                return;
            }
            User admin = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .build();
            users.save(admin);
            log.info("Bootstrap: conta ADMIN {} criada.", mask(email));
        });
    }

    /** "ruben@exemplo.pt" -> "r***@exemplo.pt" (para não registar o email em claro). */
    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
