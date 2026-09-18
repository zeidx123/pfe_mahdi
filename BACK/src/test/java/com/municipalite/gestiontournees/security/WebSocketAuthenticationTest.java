package com.municipalite.gestiontournees.security;

import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketAuthenticationTest {
    private final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    private final UserService users = mock(UserService.class);
    private final WebSocketAuthenticationInterceptor interceptor = new WebSocketAuthenticationInterceptor(tokens, users);

    @Test void anonymousConnectAndClientPublishAreDenied() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.CONNECT, null), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.SEND, Role.SUPERVISEUR), null));
    }

    @Test void positionsSubscriptionIsSupervisorOnly() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.SUBSCRIBE, Role.CHAUFFEUR), null));
        assertDoesNotThrow(() -> interceptor.preSend(message(StompCommand.SUBSCRIBE, Role.SUPERVISEUR), null));
    }

    @Test void connectSetsPrincipalFromValidatedToken() {
        var user = User.builder().id("s1").email("supervisor@test.invalid").role(Role.SUPERVISEUR).actif(true).build();
        when(tokens.extractUsername("test-token")).thenReturn(user.getEmail());
        when(users.loadUserByUsername(user.getEmail())).thenReturn(user);
        when(tokens.isTokenValid("test-token", user)).thenReturn(true);
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer test-token");
        headers.setLeaveMutable(true);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        interceptor.preSend(message, null);
        assertEquals(user.getEmail(), headers.getUser().getName());
    }

    private Message<?> message(StompCommand command, Role role) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination("/topic/positions");
        if (role != null) {
            var user = User.builder().id("u1").email("user@test.invalid").role(role).actif(true).build();
            headers.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        }
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
