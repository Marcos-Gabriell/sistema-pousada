package br.com.pousda.pousada.notificacoes.usuarios.application;

import java.util.Set;

public interface UsuarioNotifier {
    void criado(Long usuarioId, String nome, String email, Long autorId, String autorJson, Set<Long> destinatarios);
    void atualizado(Long usuarioId, String nome, String mensagemRica, Long autorId, String autorJson, Set<Long> destinatarios);
    void statusAlterado(Long usuarioId, String nome, String novoStatus, Long autorId, String autorJson, Set<Long> destinatarios);
    void senhaResetada(Long usuarioId, String nome, Long autorId, String autorJson, Set<Long> destinatarios);
    void autoExcluido(Long usuarioId, String nome, Set<Long> destinatarios);
    void excluido(Long usuarioId, String nome, Long autorId, String autorJson, Set<Long> destinatarios);
    void atualizouProprioPerfil(Long usuarioId, String nome, String campo, String de, String para, Set<Long> destinatarios);
    void senhaAtualizadaPeloProprio(Long usuarioId, String nome, Set<Long> destinatarios);

    // ===== ALERTAS / AUTO EXCLUSÃO =====
    void exclusaoEmDias(Long usuarioId, String nome, int diasRestantes, Set<Long> destinatarios);
    void exclusaoEmDoisDias(Long usuarioId, String nome, Set<Long> destinatarios);
    void excluidoAutomaticamente(Long usuarioId, String nome, Set<Long> destinatarios);
    void senhaAtualizadaNoPrimeiroLogin(Long usuarioId, String nome, Set<Long> destinatarios);
    void seusDadosAtualizados(Long usuarioId, String nome, String mudancas, Long autorId, String autorJson);
}