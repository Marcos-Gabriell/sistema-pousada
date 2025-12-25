package br.com.pousda.pousada.notificacoes.application;

import java.util.Set;



public interface UsersQueryPort {
    long currentUserId();

    Set<Long> adminIds();
    Set<Long> devIds();
    Set<Long> gerenteIds(); // <<< novo
}
