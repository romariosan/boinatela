package br.com.appcompregado.config;

import br.com.appcompregado.security.JwtAuthFilter;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, WhatsappProperties.class})
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, CorsProperties corsProperties) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/verificar-status").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/webhook/whatsapp",
                                "/api/webhook/evolution",
                                "/api/webhook/whatsapp-scraper").permitAll()
                        // Só existe com envio-no-log ligado e só atende a própria máquina (WhatsappSimuladorController).
                        .requestMatchers("/dev/whatsapp", "/dev/whatsapp/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/anuncios/meus").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/anuncios", "/api/anuncios/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categorias", "/api/cidades").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/anuncios/*/reivindicar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/alertas").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/anuncios/*/marcar-vendido").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/anuncios").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/anuncios/*/contato").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/anuncios/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/anuncios/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/anuncios/*/status").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/usuarios/alterar-senha").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
