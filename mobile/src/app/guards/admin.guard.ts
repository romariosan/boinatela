import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Só conta com admin=true no JWT/sessão.
 * Sem login (ou sem role), manda para a Conta com returnUrl=/admin
 * para abrir o modal de entrar e voltar ao painel.
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const usuario = auth.snapshot;
  if (usuario?.token && usuario.admin === true) {
    return true;
  }
  return router.createUrlTree(['/tabs/tab3'], {
    queryParams: { returnUrl: '/admin' },
  });
};
