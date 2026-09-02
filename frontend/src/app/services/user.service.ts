import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from '../models/user.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/users`;

  findAll(): Observable<User[]> {
    return this.http.get<User[]>(this.endpoint);
  }

  findById(userId: number): Observable<User> {
    return this.http.get<User>(`${this.endpoint}/${userId}`);
  }

  create(user: User): Observable<User> {
    return this.http.post<User>(this.endpoint, user);
  }

  update(userId: number, user: User): Observable<User> {
    return this.http.put<User>(`${this.endpoint}/${userId}`, user);
  }

  delete(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${userId}`);
  }
}
