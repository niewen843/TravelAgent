export type Account = {
  id: number
  username: string
  nickname: string
  role: 'USER' | 'ADMIN'
  status: 'ACTIVE' | 'DISABLED'
  mustChangePassword: boolean
  createdAt: string | null
  lastLoginAt: string | null
}
export type RegisterInput = {
  username: string
  nickname: string
  password: string
}
