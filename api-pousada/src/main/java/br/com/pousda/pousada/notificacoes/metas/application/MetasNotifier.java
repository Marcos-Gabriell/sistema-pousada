package br.com.pousda.pousada.notificacoes.metas.application;


import java.util.Set;

public interface MetasNotifier {
    void resumoSemanal(int total, Set<Long> destinatarios);
    void resumoMensal(int total, Set<Long> destinatarios);
}
