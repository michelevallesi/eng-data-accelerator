package org.eda.simulator.teamcenter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Identity-propagation seam (FR-A07). Reads {@code X-Eda-User} and stores it on the
 * request as attribute {@code eda.user}. Not enforced in S05 — real OIDC lands in a
 * later increment.
 */
@Component
@Order(1)
public class IdentityPropagationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Eda-User";
    public static final String REQUEST_ATTR = "eda.user";
    public static final String ANONYMOUS = "anonymous";

    private static final Logger log = LoggerFactory.getLogger(IdentityPropagationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String user = request.getHeader(HEADER);
        if (user == null || user.isBlank()) {
            user = ANONYMOUS;
        }
        request.setAttribute(REQUEST_ATTR, user);
        log.debug("{} {} user={}", request.getMethod(), request.getRequestURI(), user);
        chain.doFilter(request, response);
    }
}
