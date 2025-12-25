package br.com.pousda.pousada.reservas.domain;

import br.com.pousda.pousada.quartos.domain.Quarto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String telefone;

    @Column(nullable = false)
    private String cpf;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCliente tipoCliente;

    @ManyToOne
    @JoinColumn(name = "quarto_id", nullable = false)
    private Quarto quarto;

    @Column(nullable = false)
    private LocalDate dataEntrada;

    @Column(nullable = false)
    private Integer numeroDiarias;

    @Column(nullable = false, length = 20)
    private String formaPagamento;

    @Column(nullable = false)
    private LocalDate dataSaida;

    @Column(nullable = false)
    private Double valorDiaria;

    @Column(nullable = false)
    private Double valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusReserva status = StatusReserva.PENDENTE;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String observacoes;

    @Column(columnDefinition = "TEXT")
    private String observacoesCheckin;

    @Column(nullable = false)
    private LocalDate dataReserva;

    // AUDITORIA COMPLETA
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdEm;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedEm;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledEm;

    private String motivoCancelamento;

    @PrePersist
    public void prePersist() {
        // 1) gerar código se não veio do serviço
        if (this.codigo == null || this.codigo.isBlank()) {
            this.codigo = gerarCodigoReserva();
        }

        // 2) calcular data de saída
        if (dataEntrada != null && numeroDiarias != null && numeroDiarias > 0) {
            this.dataSaida = dataEntrada.plusDays(numeroDiarias);
        }

        // 3) data da reserva
        if (dataReserva == null) {
            this.dataReserva = LocalDate.now();
        }

        // 4) createdAt
        if (createdEm == null) {
            this.createdEm = LocalDateTime.now();
        }

        // 5) calcular valor total
        if (valorDiaria != null && numeroDiarias != null) {
            this.valorTotal = valorDiaria * numeroDiarias;
        }

        // 6) garantir observações
        if (this.observacoes == null) {
            this.observacoes = "";
        }
    }

    @PreUpdate
    public void preUpdate() {
        if (dataEntrada != null && numeroDiarias != null && numeroDiarias > 0) {
            this.dataSaida = dataEntrada.plusDays(numeroDiarias);
        }

        if (valorDiaria != null && numeroDiarias != null) {
            this.valorTotal = valorDiaria * numeroDiarias;
        }

        if (this.observacoes == null) {
            this.observacoes = "";
        }
    }

    /**
     * Gera código no formato: RSV-YYMMDD-XXXX
     * Ex: RSV-251101-3842
     */
    private String gerarCodigoReserva() {
        String data = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        int sufixo = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "RSV-" + data + "-" + sufixo;
    }
}
