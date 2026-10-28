package dev.proofpay.proofpay.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** HTTP Basic password on the seller pages. User "seller", password SELLER_PASSWORD. Off when it is empty. */
@Component
public class SellerAuthFilter extends OncePerRequestFilter {

    static final String USER = "seller";

    private final AppProperties props;

    public SellerAuthFilter(AppProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !props.passwordEnabled()
                || path.startsWith("/webhooks/")
                || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (authorized(request.getHeader("Authorization"))) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("WWW-Authenticate", "Basic realm=\"ProofPay\", charset=\"UTF-8\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private boolean authorized(String header) {
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String expected = USER + ":" + props.sellerPassword();
        // Constant-time comparison, so response timing does not leak the password.
        return MessageDigest.isEqual(decoded.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
