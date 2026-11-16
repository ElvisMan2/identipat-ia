import { Routes } from '@angular/router';

export const routes: Routes = [
	{ path: 'registro', children: [] },
	{ path: 'iniciar-sesion', children: [] },
	{ path: '**', children: [] }
];
