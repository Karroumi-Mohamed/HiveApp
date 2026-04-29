import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ShieldCheck, UserPlus, KeyRound, Loader2, ShieldAlert, RefreshCw } from "lucide-react"
import { toast } from "sonner"
import { adminUsersApi } from "@/features/admins/api"
import { CanDo } from "@/components/auth/CanDo"
import { P } from "@/lib/permissions"

export function AdminUsers() {
  const queryClient = useQueryClient()

  const { data: users = [], isLoading, isError, refetch, isRefetching } = useQuery({
    queryKey: ["admin-users"],
    queryFn: adminUsersApi.listUsers,
  })

  const toggleActiveMutation = useMutation({
    mutationFn: (id: string) => adminUsersApi.toggleUserActive(id),
    onSuccess: () => {
      toast.success("User status updated")
      queryClient.invalidateQueries({ queryKey: ["admin-users"] })
    },
    onError: () => {
      toast.error("Failed to update user status")
    }
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
        <p>Failed to load admin users.</p>
        <Button variant="outline" onClick={() => refetch()} className="mt-4">Try Again</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between gap-4 items-start sm:items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Admin Users & Roles</h2>
          <p className="text-muted-foreground">Platform governance security layer.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => refetch()} disabled={isRefetching}>
            <RefreshCw className={`mr-2 h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <CanDo perm={P.ADMIN_ROLES_READ} fallback="hide">
            <Button variant="outline"><KeyRound className="mr-2 h-4 w-4" /> Manage Roles</Button>
          </CanDo>
          <CanDo perm={P.ADMIN_USERS_CREATE} fallback="hide">
            <Button><UserPlus className="mr-2 h-4 w-4" /> Invite Admin</Button>
          </CanDo>
        </div>
      </div>

      <div className="bg-destructive/10 border border-destructive/20 p-4 rounded-lg flex items-start gap-3">
        <ShieldCheck className="w-5 h-5 text-destructive mt-0.5 shrink-0" />
        <div className="text-sm text-destructive-foreground">
          <p className="font-semibold">Strict Isolation</p>
          <p>Admin roles and permissions live in a completely separate namespace from the client ERP. Admins cannot interact with client business data directly.</p>
        </div>
      </div>

      <Card className="border-border/50 shadow-sm">
        <CardHeader>
          <CardTitle>Active Platform Administrators</CardTitle>
          <CardDescription>Users with access to this governance dashboard.</CardDescription>
        </CardHeader>
        <CardContent>
          {users.length === 0 ? (
            <div className="text-center p-8 border rounded-lg bg-muted/10 text-muted-foreground">
              No admins found.
            </div>
          ) : (
            <div className="space-y-4">
              {users.map((admin) => (
                <div key={admin.id} className="flex items-center justify-between p-4 border border-border/50 rounded-lg bg-card hover:bg-muted/10 transition-colors">
                  <div className="flex items-center gap-4">
                    <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center font-bold text-primary">
                      A
                    </div>
                    <div>
                      <p className="font-medium font-mono text-sm">{admin.id}</p>
                      <p className="text-xs text-muted-foreground mt-0.5">User ID: {admin.userId}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <Badge variant={admin.isSuperAdmin ? "default" : "secondary"} className={admin.isSuperAdmin ? "bg-primary/10 text-primary border-primary/20 shadow-none hover:bg-primary/20" : "shadow-none"}>
                      {admin.isSuperAdmin ? "Super Admin" : "Admin"}
                    </Badge>
                    <Badge variant={admin.isActive ? "outline" : "destructive"} className="shadow-none">
                      {admin.isActive ? "Active" : "Inactive"}
                    </Badge>
                    <CanDo perm={P.ADMIN_USERS_TOGGLE} fallback="disable" tooltip="You need permission to toggle user status">
                      <Button 
                        variant="ghost" 
                        size="sm" 
                        className="text-xs"
                        onClick={() => toggleActiveMutation.mutate(admin.id)}
                        disabled={toggleActiveMutation.isPending}
                      >
                        {admin.isActive ? "Deactivate" : "Activate"}
                      </Button>
                    </CanDo>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
