import { Routes } from '@angular/router';
import { LacrWorkspaceComponent } from './workspace.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', component: LacrWorkspaceComponent, data: { tab: 'dashboard' } },
  { path: 'closures', component: LacrWorkspaceComponent, data: { tab: 'closures' } },
  { path: 'actions', component: LacrWorkspaceComponent, data: { tab: 'actions' } },
  { path: 'audit', component: LacrWorkspaceComponent, data: { tab: 'audit' } },
  { path: 'operations', component: LacrWorkspaceComponent, data: { tab: 'operations' } },
  { path: '**', redirectTo: 'dashboard' }
];
