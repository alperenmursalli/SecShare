package org.example.secshare.vuln;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Zafiyetli config bean'leri. Hepsi ilgili vuln.* bayragi acikken devreye girer.
 * SADECE YETKILI PENTEST / EGITIM ORTAMI ICIN.
 */
@Configuration
public class VulnConfig {

    /**
     * CORS misconfiguration: gelen Origin header'ini oldugu gibi yansitir ve
     * Allow-Credentials=true doner. Bu, herhangi bir kaynaktan credential'li
     * cross-origin istegine izin verir (hesap ele gecirme).
     */
    @Component
    @ConditionalOnProperty(prefix = "vuln.cors", name = "wildcard", havingValue = "true")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class InsecureCorsFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain)
                throws ServletException, IOException {
            String origin = request.getHeader("Origin");
            if (origin != null) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Headers", "*");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            }
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }
            filterChain.doFilter(request, response);
        }
    }
}
