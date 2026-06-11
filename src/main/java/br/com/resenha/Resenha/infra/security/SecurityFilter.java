package br.com.resenha.Resenha.infra.security;

import br.com.resenha.Resenha.model.user.User;
import br.com.resenha.Resenha.model.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenConfig tokenConfig;
    private final UserRepository userRepository;

    public SecurityFilter(TokenConfig tokenConfig,
                          UserRepository userRepository) {
        this.tokenConfig = tokenConfig;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");
        if( Strings.isNotEmpty(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length());
            Optional<JWTUserData> optUser = tokenConfig.validateToken(token);
            if (optUser.isPresent()) {

                JWTUserData userData = optUser.get();

                User user = userRepository.findById(userData.userId())
                        .orElseThrow();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                user.getAuthorities()
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);

            }
            filterChain.doFilter(request, response);
        }
        else {
            filterChain.doFilter(request, response);
        }
    }

}
