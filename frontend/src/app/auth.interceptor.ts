import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthSessionService } from './auth-session.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authSession = inject(AuthSessionService);
  const authHeader = authSession.authHeader();

  if (!authHeader) {
    return next(req);
  }

  return next(
    req.clone({
      setHeaders: {
        Authorization: authHeader
      }
    })
  );
};
