import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', loadComponent: () => import('./workspace.component').then((m) => m.LacrWorkspaceComponent), data: { tab: 'dashboard' } },
  { path: 'closures', loadComponent: () => import('./workspace.component').then((m) => m.LacrWorkspaceComponent), data: { tab: 'closures' } },
  { path: 'actions', loadComponent: () => import('./workspace.component').then((m) => m.LacrWorkspaceComponent), data: { tab: 'actions' } },
  { path: 'audit', loadComponent: () => import('./workspace.component').then((m) => m.LacrWorkspaceComponent), data: { tab: 'audit' } },
  { path: 'operations', loadComponent: () => import('./workspace.component').then((m) => m.LacrWorkspaceComponent), data: { tab: 'operations' } },
  { path: '**', redirectTo: 'dashboard' }
];
