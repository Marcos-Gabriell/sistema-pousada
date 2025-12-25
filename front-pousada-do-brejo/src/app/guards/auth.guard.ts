// src/app/guards/auth.guard.ts
import { inject } from '@angular/core';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot, CanActivateFn, UrlTree } from '@angular/router';
import { AuthService } from '../services/login/auth.service';
import { ToastService } from '../toast/toast.service';
import { Role } from '../core/models/role.type';

let hasWarnedRole = false;

export const authGuard: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const token = auth.getToken?.() || '';
  const isValid = auth.isTokenValid ? !!auth.isTokenValid() : !!token;

  const userRoles = (auth.getRoles?.() ?? []) as Role[];
  const required = (route.data?.['roles'] ?? []) as Role[];

  // não logado → redireciona para acesso negado
  if (!isValid) {
    if (!hasWarnedRole) {
      toast.warning('🚫 Você precisa estar logado para acessar esta página.');
      hasWarnedRole = true;
      setTimeout(() => (hasWarnedRole = false), 3000);
    }
    return router.createUrlTree(['/acesso-negado'], { queryParams: { from: state.url } });
  }

  // verificação de papéis
  if (required.length > 0) {
    const userSet = new Set(userRoles.map(r => String(r).toUpperCase()));
    const need = required.map(r => String(r).toUpperCase());
    const ok = need.some(r => userSet.has(r as Role));

    if (!ok) {
      if (!hasWarnedRole) {
        toast.warning('🚫 Você não tem permissão para acessar esta página.');
        hasWarnedRole = true;
        setTimeout(() => (hasWarnedRole = false), 3000);
      }
      return router.createUrlTree(['/acesso-negado'], { queryParams: { from: state.url } });
    }
  }

  return true;
};
