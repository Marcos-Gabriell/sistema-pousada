package br.com.pousda.pousada.quartos.domain;

import br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto;
import br.com.pousda.pousada.quartos.domain.enuns.TipoQuarto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quarto",
        uniqueConstraints = @UniqueConstraint(name = "uk_quarto_numero", columnNames = "numero"))
public class Quarto {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(length = 60)
    private String nome;

    @Column(name = "valor_diaria", precision = 10, scale = 2)
    private BigDecimal valorDiaria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoQuarto tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusQuarto status;

    @Column(name = "capacidade")
    private Integer capacidade;

    @Column(name = "descricao", length = 500)
    private String descricao;

    /** Data em que o quarto entrou em MANUTENÇÃO */
    @Column(name = "data_manutencao_desde")
    private LocalDate dataManutencaoDesde;

    @Column(name = "ultima_alteracao_status")
    private LocalDateTime ultimaAlteracaoStatus;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        if (status == null) status = StatusQuarto.DISPONIVEL;
        if (criadoEm == null) criadoEm = LocalDateTime.now(SP);
        if (ultimaAlteracaoStatus == null) ultimaAlteracaoStatus = LocalDateTime.now(SP);
        atualizadoEm = LocalDateTime.now(SP);
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = LocalDateTime.now(SP);
    }

    // ===== Regras de domínio simples =====

    public boolean estaDisponivel() { return status == StatusQuarto.DISPONIVEL; }
    public boolean estaOcupado()    { return status == StatusQuarto.OCUPADO; }
    public boolean estaEmManutencao(){ return status == StatusQuarto.MANUTENCAO; }

    public void entrarEmManutencao() {
        this.status = StatusQuarto.MANUTENCAO;
        this.dataManutencaoDesde = LocalDate.now(SP);
        this.ultimaAlteracaoStatus = LocalDateTime.now(SP);
    }

    public void liberarManutencao() {
        this.status = StatusQuarto.DISPONIVEL;
        this.dataManutencaoDesde = null;
        this.ultimaAlteracaoStatus = LocalDateTime.now(SP);
    }

    public void ocupar() {
        this.status = StatusQuarto.OCUPADO;
        this.ultimaAlteracaoStatus = LocalDateTime.now(SP);
    }

    public void desocupar() {
        this.status = StatusQuarto.DISPONIVEL;
        this.ultimaAlteracaoStatus = LocalDateTime.now(SP);
    }

    public boolean emManutencaoHáMaisDe(int dias) {
        if (dataManutencaoDesde == null) return false;
        return dataManutencaoDesde.plusDays(dias).isBefore(LocalDate.now(SP));
    }

    @Override
    public String toString() {
        return numero + (nome != null ? " - " + nome : "");
    }
}
