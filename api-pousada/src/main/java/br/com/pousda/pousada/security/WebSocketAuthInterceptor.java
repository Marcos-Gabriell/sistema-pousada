package br.com.pousda.pousada.security;


import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Intercepta a mensagem STOMP CONNECT para autenticar o usuário usando o token JWT
 * fornecido no cabeçalho 'Authorization' antes que o broker processe a mensagem.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    // Reutilizamos a lógica de autenticação do JwtFilter para obter o objeto Authentication
    private final JwtFilter jwtFilter;

    public WebSocketAuthInterceptor(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Apenas processamos a mensagem de conexão que inicia a sessão WS
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            // 1. Tenta obter o token do cabeçalho STOMP
            String rawAuthHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

            if (rawAuthHeader != null && rawAuthHeader.startsWith("Bearer ")) {

                String token = rawAuthHeader.substring(7);

                // 2. Cria a autenticação manualmente usando a lógica reusada do JwtFilter
                Authentication authentication = jwtFilter.resolveAuthentication(token);

                if (authentication != null && authentication.isAuthenticated()) {
                    // 3. Define a autenticação no cabeçalho da mensagem
                    // Isso permite que o Spring Security e o SimpMessagingTemplate (para /user/) identifiquem o usuário.
                    accessor.setUser(authentication);
                    return message;
                }
                // Se a autenticação falhar aqui, a SecurityConfig de canais (passo 3) vai bloquear com 403.
            }
        }
        return message;
    }
}
