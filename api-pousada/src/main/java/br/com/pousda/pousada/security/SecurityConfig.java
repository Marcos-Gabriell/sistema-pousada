package br.com.pousda.pousada.security;

import br.com.pousda.pousada.exception.CustomAccessDeniedHandler;
import br.com.pousda.pousada.exception.CustomAuthEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)  // 🔥 habilita @PreAuthorize
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        cfg.setAllowedOrigins(List.of(
                "https://sistema.pousada.marcosgabriell.com.br",   // FRONT PRODUÇÃO
                "https://pousada.marcosgabriell.com.br",           // SE FRONT CHAMAR DIRETO DR API
                "http://localhost:4200",                            // FRONT DEV
                "http://127.0.0.1:4200"
        ));

        cfg.setAllowCredentials(true);

        cfg.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Cache-Control",
                "Pragma",
                "Last-Event-ID"
        ));

        cfg.setExposedHeaders(List.of("Location"));

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .cors().and().csrf().disable()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(new CustomAuthEntryPoint())
                .accessDeniedHandler(new CustomAccessDeniedHandler())
                .and()
                .authorizeRequests()

                // PRE-FLIGHT (necessário para Angular)
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // LOGIN
                .antMatchers("/api/auth/login").permitAll()

                // WEBSOCKET (permitir handshake)
                .antMatchers("/ws/**").permitAll()

                // PÁGINAS ESTÁTICAS (Angular build)
                .antMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico").permitAll()

                // ============================================
                // 🔥 RESTRIÇÃO: SOMENTE ADMIN + DEV geram relatórios
                // ============================================
                .antMatchers("/api/admin/reports/**")
                .hasAnyRole("ADMIN", "DEV")
                // ============================================

                // QUALQUER OUTRA ROTA → precisa estar logado
                .anyRequest().authenticated();

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    }
}