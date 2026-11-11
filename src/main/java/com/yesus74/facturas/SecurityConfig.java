package com.yesus74.facturas;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Desactivamos CSRF para que funcione la consola H2 y los formularios simples
            .csrf(csrf -> csrf.disable()) 
            .authorizeHttpRequests(auth -> auth
                // Permitir acceso público al login y a los recursos estáticos (CSS, JS)
                .requestMatchers("/login", "/css/**", "/js/**").permitAll()
                // Permitir acceso a la consola H2 (opcional, para desarrollo)
                .requestMatchers("/h2-console/**").permitAll()
                // Cualquier otra petición requiere iniciar sesión
                .anyRequest().authenticated()
            )
            // Configurar el formulario de login
            .formLogin(form -> form
                .loginPage("/login") // Nuestra página personalizada
                .defaultSuccessUrl("/", true) // A dónde ir si el login es exitoso
                .permitAll()
            )
            // Configurar el logout
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout") // A dónde ir después de salir
                .permitAll()
            )
            // Permitir que la consola H2 se muestre en un iframe (frames)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}