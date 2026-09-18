package com.municipalite.gestiontournees.security;

import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketAuthenticationInterceptor implements ChannelInterceptor {
    private final JwtTokenProvider tokens;
    private final UserService users;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message;
        if (StompCommand.CONNECT.equals(headers.getCommand())) {
            String authorization = headers.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new AccessDeniedException("Authentification WebSocket requise");
            }
            try {
                String token = authorization.substring(7);
                var user = users.loadUserByUsername(tokens.extractUsername(token));
                if (!tokens.isTokenValid(token, user)) throw new AccessDeniedException("JWT invalide");
                headers.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            } catch (RuntimeException error) {
                throw new AccessDeniedException("Authentification WebSocket refusée");
            }
        } else if (StompCommand.SUBSCRIBE.equals(headers.getCommand())) {
            if (!(headers.getUser() instanceof Authentication authentication)
                    || !(authentication.getPrincipal() instanceof User user)
                    || !user.isEnabled() || user.getRole() != Role.SUPERVISEUR
                    || !"/topic/positions".equals(headers.getDestination())) {
                throw new AccessDeniedException("Abonnement non autorisé");
            }
        } else if (StompCommand.SEND.equals(headers.getCommand())) {
            // All GPS writes use the authenticated REST endpoint and its tour checks.
            throw new AccessDeniedException("Utiliser l'API de suivi authentifiée");
        }
        return message;
    }
}
