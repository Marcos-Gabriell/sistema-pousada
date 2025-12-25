import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { HospedagensComponent } from './pages/hospedagens/hospedagens.component';
import { QuartosComponent } from './pages/quartos/quartos.component';
import { FinanceiroComponent } from './pages/financeiro/financeiro.component';
import { ReservasComponent } from './pages/reservas/reservas.component';
import { RelatoriosComponent } from './pages/relatorios/relatorios.component';
import { UsersComponent } from './pages/users/users.component';
import { AcessoNegadoComponent } from './pages/acesso-negado/acesso-negado.component';
import { PaginaNaoEncontradaComponent } from './pages/pagina-nao-encontrada/pagina-nao-encontrada.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },

  { path: 'login', component: LoginComponent, data: { hideChrome: true } },
  { path: 'acesso-negado', component: AcessoNegadoComponent, data: { hideChrome: true } },

  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'hospedagens', component: HospedagensComponent, canActivate: [authGuard] },
  { path: 'quartos', component: QuartosComponent, canActivate: [authGuard] },
  { path: 'financeiro', component: FinanceiroComponent, canActivate: [authGuard] },
  { path: 'reservas', component: ReservasComponent, canActivate: [authGuard] },
  { path: 'relatorios', component: RelatoriosComponent, canActivate: [authGuard] },
  { path: 'usuarios', component: UsersComponent, canActivate: [authGuard], data: { roles: ['ADMIN','DEV'] } },

  { path: 'nao-encontrada', component: PaginaNaoEncontradaComponent, data: { hideChrome: true } },
  { path: '**', component: PaginaNaoEncontradaComponent, data: { hideChrome: true } },
];
