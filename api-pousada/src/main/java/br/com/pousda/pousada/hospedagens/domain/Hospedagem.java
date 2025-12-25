package br.com.pousda.pousada.hospedagens.domain;

import br.com.pousda.pousada.hospedagens.domain.enuns.TipoHospedagem;
import br.com.pousda.pousada.quartos.domain.Quarto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "hospedagem")
// REMOVIDO: @EntityListeners(AuditingEntityListener.class)
public class Hospedagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TipoHospedagem tipo;

    private String nome;
    private String cpf;

    // ✅ Novo campo de e-mail (opcional)
    @Column(length = 150)
    private String email;

    private LocalDate dataEntrada;
    private LocalDate dataSaida;

    private Double valorDiaria;
    private Double valorTotal;

    private String formaPagamento;

    @Column(length = 1000)
    private String observacoes;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "quarto_id")
    private Quarto quarto;

    @Column(nullable = false)
    private Boolean cancelada = false;

    private String motivoCancelamento;

    @Column(name = "codigo_hospedagem", unique = true, nullable = false, length = 20)
    private String codigoHospedagem;

    // REMOVIDO: @CreatedBy / @CreatedDate
    @Column(name = "criado_por", length = 100, updatable = false)
    private String criadoPor;

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    // ✅ NOVOS CAMPOS: criador (ID + Nome)
    @Column(name = "criado_por_id")
    private Long criadoPorId;

    @Column(name = "criado_por_nome", length = 120)
    private String criadoPorNome;

    /* ====== cancelamento (soft delete) ====== */
    @Column(name = "cancelado", nullable = false)
    private Boolean cancelado = false;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    @Column(name = "cancelado_por_id")
    private Long canceladoPorId;

    @Column(name = "cancelado_por_nome", length = 120)
    private String canceladoPorNome;

    /** Perfil que cancelou: ADMIN ou DEV */
    @Column(name = "cancelado_por_perfil", length = 20)
    private String canceladoPorPerfil;

    @Column(name = "cancelado_motivo", length = 255)
    private String canceladoMotivo;
}
