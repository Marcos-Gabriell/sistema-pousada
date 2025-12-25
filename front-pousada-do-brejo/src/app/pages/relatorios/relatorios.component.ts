import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators
} from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import {
  ReportsService,
  PeriodoFilter,
  FinanceiroFilter,
  HospedagensFilter,
  QuartosFilter,
  ReservasFilter
} from './reports.service';
import { ToastService } from '../../toast/toast.service';

type Aba = 'geral' | 'financeiro' | 'hospedagens' | 'quartos' | 'reservas';
type Preset = 'today' | '7d' | 'month';

@Component({
  selector: 'app-relatorios',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './relatorios.component.html',
  styleUrls: ['./relatorios.component.css']
})
export class RelatoriosComponent implements OnInit {

  private fb = inject(FormBuilder);
  private api = inject(ReportsService);
  private toast = inject(ToastService);

  abaAtual: Aba = 'geral';
  gerando = signal(false);

  periodo!: FormGroup;
  financeiro!: FormGroup;
  hospedagens!: FormGroup;
  quartos!: FormGroup;
  reservas!: FormGroup;

  presetSelecionado: Record<Aba, Preset | null> = {
    geral: null,
    financeiro: null,
    hospedagens: null,
    quartos: null,
    reservas: null
  };

  ngOnInit(): void {
    const hoje = new Date().toISOString().split('T')[0];

    this.periodo = this.fb.group({
      dataInicio: [hoje, Validators.required],
      dataFim: [hoje, Validators.required]
    });

    this.financeiro = this.fb.group({
      dataInicio: [hoje, Validators.required],
      dataFim: [hoje, Validators.required],
      tipo: ['TODAS']
    });

    this.hospedagens = this.fb.group({
      dataInicio: [hoje, Validators.required],
      dataFim: [hoje, Validators.required],
      status: ['TODAS'],
      tipo: ['TODOS']
    });

    this.quartos = this.fb.group({
      dataInicio: [hoje, Validators.required],
      dataFim: [hoje, Validators.required],
      status: ['TODOS'],
      tipo: ['TODOS']
    });

    this.reservas = this.fb.group({
      dataInicio: [hoje, Validators.required],
      dataFim: [hoje, Validators.required],
      status: ['TODAS']
    });
  }

  trocarAba(aba: Aba) {
    this.abaAtual = aba;
  }

  tabClasses(aba: Aba): string {
    const base =
      'px-4 py-2 rounded-full text-sm sm:text-base font-semibold transition ' +
      'min-h-[44px] cursor-pointer pointer-events-auto select-none';
    const inactive =
      'bg-[var(--bg-card)] text-[var(--text-color)] border border-[var(--border-color)] ' +
      'hover:bg-[var(--table-row-hover)]';
    const active =
      'bg-[var(--accent-yellow)] text-gray-900 shadow border border-transparent';

    return `${base} ${this.abaAtual === aba ? active : inactive}`;
  }

  chipClasses(formKey: Aba, preset: Preset): string {
    const base =
      'min-h-[44px] min-w-[96px] px-4 py-2 rounded-full text-sm font-medium border ' +
      'transition touch-manipulation cursor-pointer pointer-events-auto select-none flex-shrink-0';
    const active =
      'bg-[var(--accent-yellow)] text-gray-900 border-[var(--accent-yellow)] shadow-sm';
    const idle =
      'bg-[var(--bg-color)] text-[var(--text-color)] border-[var(--border-color)] ' +
      'hover:bg-[var(--table-row-hover)]';

    const isActive = this.presetSelecionado[formKey] === preset;
    return `${base} ${isActive ? active : idle}`;
  }

  onQuick(formKey: Aba, preset: Preset) {
    const fg = this.getForm(formKey);
    if (!fg) {
      this.toast.error('Formulário não encontrado');
      return;
    }

    this.presetSelecionado[formKey] = preset;

    const now = new Date();
    let start = new Date(now);
    let end = new Date(now);

    if (preset === '7d') {
      start.setDate(now.getDate() - 6);
    } else if (preset === 'month') {
      start = new Date(now.getFullYear(), now.getMonth(), 1);
      end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    }
    // 'today' fica com start/end = hoje mesmo

    const dataInicio = this.iso(start);
    const dataFim = this.iso(end);

    fg.patchValue({ dataInicio, dataFim });
  }

  async gerar() {
    if (!this.validarDatasDaAbaAtual()) return;

    this.gerando.set(true);
    try {
      const res = await this.dispararGeracao();
      const nome = this.suggestedFileName();
      this.api.downloadFromResponse(res, nome);
      this.toast.success('Relatório gerado com sucesso!');
    } catch (e) {
      this.toast.error('Não foi possível gerar o relatório. Tente novamente.');
    } finally {
      this.gerando.set(false);
    }
  }

  private getForm(key: Aba): FormGroup | null {
    const forms: Record<Aba, FormGroup> = {
      geral: this.periodo,
      financeiro: this.financeiro,
      hospedagens: this.hospedagens,
      quartos: this.quartos,
      reservas: this.reservas
    };

    return forms[key] || null;
  }

  private validarDatasDaAbaAtual(): boolean {
    const fg = this.getForm(this.abaAtual);
    if (!fg) {
      alert('Formulário da aba não encontrado.');
      return false;
    }

    const i = new Date(fg.value.dataInicio);
    const f = new Date(fg.value.dataFim);

    if (isNaN(+i) || isNaN(+f)) {
      alert('Selecione um período válido.');
      return false;
    }

    if (i > f) {
      alert('Data inicial não pode ser maior que a data final.');
      return false;
    }

    return true;
  }

  private async dispararGeracao(): Promise<HttpResponse<Blob>> {
    switch (this.abaAtual) {
      case 'geral':
        return firstValueFrom(
          this.api.exportGeral(this.periodo.value as PeriodoFilter)
        );
      case 'financeiro':
        return firstValueFrom(
          this.api.exportFinanceiro(this.financeiro.value as FinanceiroFilter)
        );
      case 'hospedagens':
        return firstValueFrom(
          this.api.exportHospedagens(this.hospedagens.value as HospedagensFilter)
        );
      case 'quartos':
        return firstValueFrom(
          this.api.exportQuartos(this.quartos.value as QuartosFilter)
        );
      case 'reservas':
        return firstValueFrom(
          this.api.exportReservas(this.reservas.value as ReservasFilter)
        );
    }
    throw new Error('Aba inválida para geração de relatório');
  }

  private suggestedFileName(): string {
    const fg = this.getForm(this.abaAtual)!;
    const inicio = fg.value?.dataInicio ?? '';
    const fim = fg.value?.dataFim ?? '';
    const stamp = `${inicio}_${fim}`.replaceAll('-', '');
    return `relatorio-${this.abaAtual}-${stamp}.pdf`;
  }

  private iso(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  
  bloquearScrollBody(ativo: boolean) {
  document.body.style.overflow = ativo ? 'hidden' : '';
}

}

