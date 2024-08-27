package io.u11.skytrainsim.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig {
    // Security is currently a no-op while there's no writing / sensitive reads

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it.requestMatchers("/actuator").authenticated().anyRequest().permitAll()
            }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .cors {}
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        // TODO proper cors config
        val conf = CorsConfiguration()
        conf.allowedOrigins = listOf("*")
        conf.allowedMethods = listOf("*")
        conf.allowedHeaders = listOf("*")
        val src = UrlBasedCorsConfigurationSource()
        src.registerCorsConfiguration("/**", conf)
        return src
    }

    @Bean
    fun userDetailsService(): UserDetailsService {
        return InMemoryUserDetailsManager()
    }
}
