package br.com.pousda.pousada.notificacoes.core.infra.adapter;

import br.com.pousda.pousada.notificacoes.application.NotificationPushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class SseNotificationPushService implements NotificationPushService {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private static final long TIMEOUT_MS = 30 * 60 * 1000;

    @Override
    public SseEmitter subscribe(long userId) {
        var emitter = new SseEmitter(TIMEOUT_MS);

        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        // envio inicial (keepalive)
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .id(String.valueOf(Instant.now().toEpochMilli()))
                    .name("connected")
                    .data("connected");
            emitter.send(event);
        } catch (IOException e) {
            remove(userId, emitter);
        }

        return emitter;
    }

    @Override
    public void publish(long userId, Object payload) {
        var list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : List.copyOf(list)) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(Instant.now().toEpochMilli()))
                        .name("notification")
                        .data(payload));
            } catch (IOException e) {
                remove(userId, emitter);
            }
        }
    }

    @Override
    public void publishAll(Object payload) {
        emitters.forEach((uid, list) -> publish(uid, payload));
    }

    private void remove(long userId, SseEmitter emitter) {
        var list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(userId);
        }
    }
}
