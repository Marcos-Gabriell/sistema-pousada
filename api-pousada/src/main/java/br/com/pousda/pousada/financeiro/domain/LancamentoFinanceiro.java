package br.com.pousda.pousada.financeiro.domain;

import br.com.pousda.pousada.financeiro.domain.enuns.OrigemLancamento;
import br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "lancamento_financeiro",
        indexes = {
                @Index(name = "idx_lanc_fin_data", columnList = "data"),
                @Index(name = "idx_lanc_fin_codigo", columnList = "codigo", unique = true)
        })
public class LancamentoFinanceiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ex.: 202511001 (ano+mes + sequencial de 3 dígitos) */
    @Column(nullable = false, length = 16, unique = true)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoLancamento tipo; // ENTRADA | SAIDA

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemLancamento origem; // HOSPEDAGEM | MANUAL

    /** Se origem = HOSPEDAGEM, referenciaId = id da hospedagem. Para MANUAL pode ser null. */
    @Column(name = "referencia_id")
    private Long referenciaId;

    /** Mantido para filtros por dia */
    @Column(nullable = false, columnDefinition = "DATE")
    private LocalDate data;

    /** Momento exato do lançamento */
    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(nullable = false)
    private Double valor;

    @Column(length = 32)
    private String formaPagamento; // PIX, ESPECIE, CARTAO, TRANSFERENCIA, OUTROS...

    @Column(length = 255)
    private String descricao;

    // Auditoria criação
    @Column(name = "criado_por_id")
    private Long criadoPorId;

    /** Sempre "NOME | ID" */
    @Column(name = "criado_por_nome", length = 160)
    private String criadoPorNome;

    // Auditoria edição (limite 3)
    @Column(name = "edicoes", nullable = false)
    private Integer edicoes = 0;

    @Column(name = "editado_em")
    private LocalDateTime editadoEm;

    @Column(name = "editado_por_id")
    private Long editadoPorId;

    /** Sempre "NOME | ID" */
    @Column(name = "editado_por_nome", length = 160)
    private String editadoPorNome;

    // "Cancelamento" (soft delete)
    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;

    @Column(name = "excluido_por_id")
    private Long excluidoPorId;

    /** Sempre "NOME | ID" */
    @Column(name = "excluido_por_nome", length = 160)
    private String excluidoPorNome;

    @Column(name = "excluido_motivo", length = 255)
    private String excluidoMotivo;

    @Column(name = "prefeitura")
    private Boolean prefeitura;

}
