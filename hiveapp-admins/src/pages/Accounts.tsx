import { useQuery } from "@tanstack/react-query"
import { Card } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Building, Settings2, Loader2, ShieldAlert, RefreshCw } from "lucide-react"
import { adminAccountsApi } from "@/features/accounts/api"
import { CanDo } from "@/components/auth/CanDo"
import { P } from "@/lib/permissions"

export function Accounts() {
  const { data: accounts = [], isLoading, isError, refetch, isRefetching } = useQuery({
    queryKey: ["admin-accounts"],
    queryFn: adminAccountsApi.listAccounts,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] text-destructive">
        <ShieldAlert className="w-8 h-8 mb-2" />
        <p>Failed to load accounts.</p>
        <Button variant="outline" onClick={() => refetch()} className="mt-4">Try Again</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between gap-4 items-start sm:items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Client Accounts</h2>
          <p className="text-muted-foreground">Monitor and manage tenant workspaces.</p>
        </div>
        <Button variant="outline" onClick={() => refetch()} disabled={isRefetching}>
          <RefreshCw className={`mr-2 h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      <div className="grid gap-4">
        {accounts.length === 0 ? (
          <div className="text-center p-8 border rounded-lg bg-muted/10 text-muted-foreground">
            No accounts found.
          </div>
        ) : (
          accounts.map((acc) => (
            <Card key={acc.id} className="overflow-hidden">
              <div className="flex flex-col sm:flex-row items-start sm:items-center p-6 gap-6">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-3 mb-1">
                    <h3 className="text-lg font-bold truncate">{acc.name}</h3>
                    <Badge variant={acc.isActive ? "default" : "destructive"} className="shadow-none">
                      {acc.isActive ? "ACTIVE" : "INACTIVE"}
                    </Badge>
                  </div>
                  <p className="text-sm text-muted-foreground font-mono">/{acc.slug}</p>
                </div>
                <div className="flex gap-6 sm:gap-8 text-sm w-full sm:w-auto">
                  <div className="flex flex-col items-start sm:items-end">
                    <span className="text-muted-foreground flex items-center gap-1.5"><Building className="w-4 h-4"/> ID</span>
                    <span className="font-medium text-xs">{acc.id}</span>
                  </div>
                </div>
                <div className="flex w-full sm:w-auto mt-4 sm:mt-0 justify-end">
                  <CanDo perm={P.ADMIN_SUBS_OVERRIDES} fallback="hide">
                    <Button variant="outline" size="sm">
                      <Settings2 className="mr-2 h-4 w-4" /> Manage Overrides
                    </Button>
                  </CanDo>
                </div>
              </div>
            </Card>
          ))
        )}
      </div>
    </div>
  )
}
