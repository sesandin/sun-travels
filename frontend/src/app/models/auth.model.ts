export type UserRole = 'ADMIN' | 'AGENT';
export interface AuthenticatedUser {
  userId: number;
  username: string;
  fullName: string;
  role: UserRole;
}
export interface LoginRequest { username: string; password: string; }
