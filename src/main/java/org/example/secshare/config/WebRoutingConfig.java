package org.example.secshare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the short public share URL {@code /s/<token>} by forwarding to the static
 * {@code share.html} page. The browser URL stays {@code /s/<token>}; share.html reads the
 * token from the path. Older {@code /share.html?t=<token>} links continue to work unchanged.
 */
@Configuration
public class WebRoutingConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/s/{token}").setViewName("forward:/share.html");
    }
}
