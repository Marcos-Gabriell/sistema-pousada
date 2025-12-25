package br.com.pousda.pousada.notificacoes.application.recipients;

import br.com.pousda.pousada.notificacoes.application.UsersQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationRecipientsService {

    private final UsersQueryPort users;

    @Value("${notifier.master-admin-id:1}")
    private Long masterAdminId;

    public Set<Long> getOperationalRecipientsWithAuthor(Long autorId) {
        HashSet<Long> out = new HashSet<>();
        out.addAll(nz(users.adminIds()));
        out.addAll(nz(users.devIds()));
        out.addAll(nz(users.gerenteIds()));
        addIfValid(out, autorId);

        ensureAdminFallback(out);
        if (out.isEmpty()) addIfValid(out, autorId);

        return out;
    }

    public Set<Long> getOperationalRecipients() {
        HashSet<Long> out = new HashSet<>();
        out.addAll(nz(users.adminIds()));
        out.addAll(nz(users.devIds()));
        out.addAll(nz(users.gerenteIds()));

        ensureAdminFallback(out);
        return out;
    }

    public Set<Long> getUserRecipientsWithAuthor(Long autorId) {
        HashSet<Long> out = new HashSet<>();
        out.addAll(nz(users.adminIds()));
        out.addAll(nz(users.devIds()));
        addIfValid(out, autorId);

        ensureAdminFallback(out);
        if (out.isEmpty()) addIfValid(out, autorId);

        return out;
    }

    public Set<Long> getUserRecipients() {
        HashSet<Long> out = new HashSet<>();
        out.addAll(nz(users.adminIds()));
        out.addAll(nz(users.devIds()));

        ensureAdminFallback(out);
        return out;
    }

    private Set<Long> nz(Set<Long> s) {
        return (s == null) ? Collections.emptySet() : s;
    }

    private void addIfValid(Set<Long> out, Long id) {
        if (id != null && id > 0L) out.add(id);
    }

    private void ensureAdminFallback(Set<Long> out) {
        boolean temAdmins = !nz(users.adminIds()).isEmpty();
        if (!temAdmins && masterAdminId != null && masterAdminId > 0) {
            out.add(masterAdminId);
        }
    }
}