package br.com.pousda.pousada.notificacoes.core.domain.dto;

import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationOrigin;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationStatus;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    public Long id;
    public NotificationType type;
    public String title;
    public String body;
    public String bodyFormatted;
    public String link;
    public String action;
    public Long itemId;
    public String date;
    public NotificationOrigin origin;
    public NotificationStatus status;
    public Set<Long> recipients;
    public int recipientsCount;
    public String recipientsLabel;

    public Instant createdAt;
    public Instant expiresAt;

    private Integer expiresInDays;
    public String expiresInLabel;
}
