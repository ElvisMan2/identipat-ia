import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from '../models/user.model';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/clients';

  findAll(): Observable<User[]> {
    return this.http.get<User[]>(this.endpoint);
  }

  findById(clientId: number): Observable<User> {
    return this.http.get<User>(`${this.endpoint}/${clientId}`);
  }

  create(user: User): Observable<User> {
    return this.http.post<User>(this.endpoint, user);
  }

  update(clientId: number, user: User): Observable<User> {
    return this.http.put<User>(`${this.endpoint}/${clientId}`, user);
  }

  delete(clientId: number): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${clientId}`);
  }
}
