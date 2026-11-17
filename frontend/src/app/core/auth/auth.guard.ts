import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Keeps signed-out visitors out of every page except sign-in. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  // Remember where the visitor was going so sign-in can take them back there.
  return router.createUrlTree(['/auth'], { queryParams: { returnUrl: state.url } });
};
