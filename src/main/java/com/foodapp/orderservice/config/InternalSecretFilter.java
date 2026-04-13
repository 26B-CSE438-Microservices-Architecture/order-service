package com.foodapp.orderservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /internal/** endpoint'lerini korur.
 * İstekte X-Internal-Secret header'ı yoksa veya yanlışsa 401 döner.
 * Sadece aynı internal network'teki servisler bu secret'ı bilmeli.
 */
@Component
@Slf4j
public class InternalSecretFilter extends OncePerRequestFilter {

    private static final String SECRET_HEADER = "X-Internal-Secret";

    @Value("${internal.secret}")
    private String internalSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(SECRET_HEADER);
        if (header == null || !header.equals(internalSecret)) {
            log.warn("Unauthorized /internal access attempt from ip={} uri={}",
                    request.getRemoteAddr(), request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
