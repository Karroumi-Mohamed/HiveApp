import { api } from '@/lib/api'

export interface AccountDto {
  id: string
  name: string
  slug: string
  isActive: boolean
}

export const adminAccountsApi = {
  listAccounts: async () => {
    return api.get('accounts').json<AccountDto[]>()
  }
}
