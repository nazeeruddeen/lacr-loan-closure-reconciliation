import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../environments/environment';
import { OperatorCredentials, OperatorProfile } from './lacr.models';

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly storageKey = 'lacr-basic-auth';
  private readonly profileKey = 'lacr-operator-profile';

  constructor(private readonly http: HttpClient) {}

  login(credentials: OperatorCredentials): Observable<OperatorProfile> {
    const authHeader = this.toBasicAuth(credentials);
    return this.http
      .get<OperatorProfile>(`${environment.operatorApiBaseUrl}/me`, {
        headers: new HttpHeaders({ Authorization: authHeader })
      })
      .pipe(
        tap((profile) => {
          localStorage.setItem(this.storageKey, authHeader);
          localStorage.setItem(this.profileKey, JSON.stringify(profile));
        })
      );
  }

  restoreProfile(): OperatorProfile | null {
    const raw = localStorage.getItem(this.profileKey);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as OperatorProfile;
    } catch {
      this.clear();
      return null;
    }
  }

  authHeader(): string | null {
    return localStorage.getItem(this.storageKey);
  }

  hasSession(): boolean {
    return !!this.authHeader();
  }

  clear(): void {
    localStorage.removeItem(this.storageKey);
    localStorage.removeItem(this.profileKey);
  }

  private toBasicAuth(credentials: OperatorCredentials): string {
    return `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`;
  }
}
