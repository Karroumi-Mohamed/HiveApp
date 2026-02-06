# Cahier des Charges — HiveApp

## Système de Permissions et Entités Fondamentales

**Version :** 1.0
**Date :** 2026-02-06
**Statut :** Brouillon

---

## 1. Introduction

### 1.1 Objet du Document

Ce cahier des charges définit les spécifications fonctionnelles et techniques du système de permissions et des entités fondamentales de la plateforme HiveApp. Ce document se concentre exclusivement sur le socle du système — identité, comptes, rôles, permissions et collaboration — sans aborder le fonctionnement interne des modules métier (RH, Finance, etc.).

### 1.2 Périmètre

Le périmètre couvre :

- Le modèle d'identité (User)
- La plateforme d'administration HiveApp
- La plateforme client HiveApp
- Le moteur de permissions partagé
- Le système de collaboration inter-comptes
- Le registre des modules et fonctionnalités
- Le système de plans et abonnements

Hors périmètre :
- La logique métier des modules ERP
- Les interfaces utilisateur (UI/UX)
- L'infrastructure de déploiement
- Les intégrations tierces

---

## 2. Architecture Générale

### 2.1 Vue d'Ensemble

HiveApp est composé de deux plateformes distinctes partageant un socle commun :

| Composant | Description |
|-----------|-------------|
| **Couche d'identité partagée** | Entité User unique, authentification commune |
| **Moteur de permissions partagé** | Un seul moteur, deux contextes d'exécution |
| **Plateforme d'administration** | Gestion de la plateforme, plans, modules, supervision |
| **Plateforme client** | Comptes, entreprises, membres, permissions, collaboration |

### 2.2 Principe Fondamental

Le moteur de permissions est **unique et partagé**. Il opère de manière identique dans les deux contextes (administration et client). Seules les données diffèrent :

- **Contexte Admin** : AdminUser, AdminRole, AdminPermission
- **Contexte Client Propre** : Member, Role, Permission avec plafond du Plan
- **Contexte Collaboration** : Member, Role, Permission avec plafond défini par le client

---

## 3. Modèle d'Identité

### 3.1 User

L'entité **User** est le socle identitaire de toute personne interagissant avec la plateforme.

**Caractéristiques :**

- Chaque personne sur la plateforme est un User
- Un User possède un email unique et des identifiants d'authentification
- Un User peut assumer plusieurs rôles dans le système :
  - **Propriétaire de compte (Owner)** — un User ayant créé un Account
  - **Membre (Member)** — un User créé/ajouté au sein d'un Account
  - **Administrateur plateforme (AdminUser)** — un User avec accès à la plateforme d'administration

**Règles :**

- Un User peut posséder au maximum **un seul Account** en tant que propriétaire
- Un User peut être Member dans **plusieurs Accounts**
- Un Member **n'existe pas** en dehors du contexte de son Account
- Un Member ne possède **pas** de compte autonome, pas de tableau de bord indépendant — uniquement un tableau de bord personnalisé au sein de son Account
- Le Owner est un Member avec un flag `is_owner` — ce n'est pas une entité séparée

---

## 4. Plateforme d'Administration

### 4.1 AdminUser

Un AdminUser est un User ayant accès à la plateforme d'administration.

**Caractéristiques :**

- Lié à un User existant
- Possède un flag `is_super_admin` pour les opérations critiques
- Peut se voir attribuer des AdminRoles dynamiques

### 4.2 AdminRole

Les rôles administrateurs sont **entièrement dynamiques** — créés, modifiés, supprimés à volonté.

**Caractéristiques :**

- Chaque AdminRole est composé d'un ensemble d'AdminPermissions
- Pas de rôles codés en dur (aucun enum)
- Un AdminUser peut avoir plusieurs AdminRoles

### 4.3 AdminPermission

Les permissions administratives définissent les actions possibles au sein de la plateforme d'administration.

**Structure :**

- `code` : identifiant unique (ex: `admin.plans.create`, `admin.accounts.suspend`)
- `action` : type d'opération (create, read, update, delete, manage)
- `resource` : ressource cible (plans, accounts, modules, features)
- `module` : rattachement optionnel à un module du registre

### 4.4 Registre des Modules et Fonctionnalités

Le registre est la **source de vérité** de tout ce que HiveApp propose.

#### Module

Un module représente un domaine fonctionnel de l'ERP.

**Exemples :** RH, Finance, Comptabilité, Gestion de Projet, CRM

**Caractéristiques :**

- Défini et géré exclusivement via la plateforme d'administration
- Possède un code unique, un nom, une description, un ordre d'affichage
- Peut être activé/désactivé globalement

#### Feature (Fonctionnalité)

Une fonctionnalité est un sous-ensemble d'un module.

**Exemple :** Le module RH contient les fonctionnalités : Gestion des employés, Gestion des congés, Paie

**Caractéristiques :**

- Appartient à un et un seul Module
- Chaque Feature définit un ensemble de Permissions
- Granularité fine : c'est à ce niveau que les permissions sont définies

### 4.5 Plans

Un Plan définit le **plafond de permissions** d'un Account.

**Caractéristiques :**

- Composé dynamiquement à partir du registre de fonctionnalités
- Un Plan sélectionne un sous-ensemble de Features (via PlanFeature)
- Chaque PlanFeature peut avoir une configuration spécifique (limites, quotas)
- Définit également des limites structurelles : nombre max d'entreprises, nombre max de membres

**Règle fondamentale :** Un Account ne peut **jamais** dépasser les permissions définies par son Plan. Le Plan est le plafond absolu pour les opérations en contexte propre.

---

## 5. Plateforme Client

### 5.1 Account

L'Account est l'entité racine côté client.

**Caractéristiques :**

- Créé par un User (qui devient le Owner)
- Lié à un Plan (via Subscription)
- Contient des Companies, des Members et des Roles
- Possède un slug unique pour l'identification

**Règles :**

- Un User ne peut créer qu'un seul Account
- L'Account est le périmètre de facturation
- La suppression d'un Account entraîne la suppression de toutes les entités liées

### 5.2 Company (Entreprise)

Une Company représente une entité juridique ou organisationnelle gérée au sein d'un Account.

**Caractéristiques :**

- Appartient à un seul Account
- Possède ses propres informations légales (raison sociale, numéro fiscal, adresse)
- Active un sous-ensemble de Modules disponibles dans le Plan (via CompanyModule)

**Règles :**

- Le nombre de Companies est limité par le Plan
- Une Company ne peut activer que des Modules inclus dans le Plan de l'Account
- Chaque Company peut activer des modules différents

### 5.3 Member (Membre)

Un Member est la représentation d'un User au sein d'un Account.

**Caractéristiques :**

- Lié à un User (identité) et à un Account (contexte)
- Possède un `display_name` propre au contexte de l'Account
- Le flag `is_owner` identifie le propriétaire du compte
- Se voit attribuer des Roles (via MemberRole)

**Règles :**

- Un Member n'existe que dans le contexte de son Account
- Le même User peut être Member dans plusieurs Accounts (memberships multiples)
- Un Member accède aux Companies via ses Roles — pas d'accès direct
- La désactivation d'un Member révoque tous ses accès sans supprimer l'historique

### 5.4 Système de Rôles et Permissions

#### Permission

Entité atomique représentant une action sur une ressource dans le contexte d'une fonctionnalité.

**Structure :**

- `code` : identifiant unique (ex: `hr.employees.create`, `finance.invoices.read`)
- `feature` : rattachement à une Feature du registre
- `action` : opération (create, read, update, delete, export, approve, etc.)
- `resource` : ressource cible

**Règles :**

- Les Permissions sont définies par les Features du registre
- Elles sont **globales** — non spécifiques à un Account
- Un Account n'a pas la capacité de créer des Permissions — il ne peut que les utiliser via les Roles

#### Role

Ensemble nommé de Permissions, défini au niveau de l'Account.

**Caractéristiques :**

- Créé, modifié, supprimé par le Owner ou un Member ayant la permission appropriée
- Composé d'un ensemble de Permissions (via RolePermission)
- Flag `is_system_role` pour les rôles générés automatiquement (ex: rôle Owner par défaut)
- Dynamique — aucun rôle codé en dur

**Règles :**

- Un Role ne peut contenir que des Permissions **couvertes par le Plan** de l'Account
- Si un Plan est rétrogradé, les Roles existants doivent être recalculés
- Un Role appartient à un et un seul Account

#### MemberRole (Attribution de Rôle)

Liaison entre un Member, un Role et optionnellement une Company.

**Caractéristiques :**

- `company` nullable :
  - **Si null** → le rôle s'applique à **tout l'Account** (toutes les Companies)
  - **Si défini** → le rôle s'applique uniquement à **cette Company spécifique**

**Règles :**

- Un Member peut avoir plusieurs MemberRoles
- Les permissions effectives = union de tous les rôles applicables au contexte
- Un même Role peut être attribué à un Member à l'échelle de l'Account ET à l'échelle d'une Company spécifique

### 5.5 Calcul des Permissions Effectives

Le calcul des permissions effectives d'un Member suit cette logique :

#### En contexte propre (Account du Member)

```
Permissions du Plan (plafond)
    ∩
Union des Permissions de tous les Roles du Member (au niveau Account + Company)
    ∩
Modules actifs de la Company (si contexte Company)
    =
Permissions Effectives
```

**Étapes :**

1. Récupérer le plafond du Plan de l'Account
2. Récupérer tous les MemberRoles du Member (account-wide + company-scoped)
3. Calculer l'union de toutes les Permissions des Roles
4. Appliquer l'intersection avec le plafond du Plan
5. Si contexte Company : filtrer par les Modules actifs de la Company
6. Résultat = Permissions Effectives

#### En contexte collaboration

```
Permissions accordées par le Client (plafond collaboration)
    ∩
Union des Permissions de tous les Roles du Member (côté Provider)
    ∩
Modules actifs de la Company partagée
    =
Permissions Effectives en Collaboration
```

**Étapes :**

1. Récupérer les CollaborationPermissions (le plafond défini par le client)
2. Récupérer les Roles du Member côté Provider
3. Calculer l'union des Permissions des Roles
4. Appliquer l'intersection avec le plafond collaboration (remplace le plafond Plan)
5. Filtrer par les Modules actifs de la Company partagée
6. Résultat = Permissions Effectives en Collaboration

---

## 6. Système de Collaboration

### 6.1 Concept

La collaboration permet à un Account (Client) d'accorder un accès contrôlé à un autre Account (Prestataire/Provider) sur une Company spécifique.

### 6.2 Collaboration

**Caractéristiques :**

- Lie un Account client à un Account prestataire
- Porte sur une **Company spécifique** (pas sur l'Account entier)
- Possède un cycle de vie : pending → active → suspended → revoked
- Définit un ensemble de permissions accordées (via CollaborationPermission)

### 6.3 CollaborationPermission

**Caractéristiques :**

- Sous-ensemble de Permissions que le Client accorde au Prestataire
- Constitue le **plafond** pour toute opération du Prestataire sur cette Company
- Le Client peut modifier ces permissions à tout moment

### 6.4 Scénarios

#### Scénario A — Le Prestataire gère en interne

Le prestataire crée une Company dans son propre Account pour le client. Aucune collaboration nécessaire — tout fonctionne sous le Plan du prestataire.

#### Scénario B — Le Client accorde l'accès

1. Le Client initie une Collaboration depuis son Account
2. Il sélectionne la Company à partager
3. Il sélectionne les Permissions à accorder (granulaire)
4. Le Prestataire accepte la Collaboration
5. Le Prestataire attribue des Roles à ses Members pour cette Collaboration
6. Les permissions des Members du Prestataire sont filtrées par le plafond Collaboration

### 6.5 Règles de Sécurité

- Le Prestataire ne peut **jamais** dépasser les permissions accordées par le Client
- Le Client peut **révoquer** ou **suspendre** la Collaboration à tout moment
- Le Client peut **modifier** les permissions en temps réel — les accès sont recalculés immédiatement
- Un Prestataire ne peut **pas** sous-déléguer l'accès à un tiers
- Les actions du Prestataire sur la Company du Client sont **auditables**

---

## 7. Moteur de Permissions

### 7.1 Architecture

Le moteur de permissions est un **service partagé** utilisé par les deux plateformes.

**Interface :**

- `resolvePermissions(actor, context)` → PermissionSet
- `checkAccess(actor, permission, context)` → Boolean
- `getEffectivePermissions(actor, context)` → PermissionSet

### 7.2 PermissionContext

Le contexte détermine comment le moteur calcule les permissions :

| Contexte | Plafond | Acteur |
|----------|---------|--------|
| `ADMIN_PLATFORM` | Toutes les AdminPermissions | AdminUser |
| `CLIENT_OWN_ACCOUNT` | Plan de l'Account | Member |
| `CLIENT_COLLABORATION` | CollaborationPermissions | Member (côté Provider) |

### 7.3 PermissionSet

Objet valeur immuable représentant un ensemble de permissions calculées.

**Opérations :**

- `intersect(other)` — intersection (utilisé pour appliquer les plafonds)
- `union(other)` — union (utilisé pour fusionner les rôles)
- `subtract(other)` — soustraction
- `has(code)` — vérification d'une permission spécifique

### 7.4 Flux de Résolution

```
1. Identifier l'acteur (IPermissionActor)
2. Déterminer le contexte (PermissionContext)
3. Récupérer les rôles de l'acteur pour ce contexte
4. Calculer l'union des permissions de tous les rôles
5. Déterminer le plafond applicable :
   - Admin → pas de plafond (ou plafond système)
   - Client propre → Plan
   - Collaboration → CollaborationPermissions
6. Appliquer l'intersection : permissions ∩ plafond
7. Retourner le PermissionSet effectif
```

---

## 8. Plans et Abonnements

### 8.1 Plan

**Caractéristiques :**

- Défini via la plateforme d'administration
- Composé de Features sélectionnées depuis le registre
- Chaque association Plan-Feature peut avoir une configuration (quotas, limites)
- Définit les limites structurelles (max Companies, max Members)

### 8.2 Subscription

**Caractéristiques :**

- Lie un Account à un Plan
- Gère le cycle de facturation
- Statuts : active, past_due, cancelled, trialing

### 8.3 Impact du Changement de Plan

**Upgrade :**

- Nouvelles Features/Permissions deviennent disponibles immédiatement
- Les Roles existants ne changent pas — de nouvelles permissions sont simplement disponibles pour attribution

**Downgrade :**

- Les Features retirées ne sont plus accessibles
- Les Roles contenant des Permissions hors-plan sont **recalculés**
- Les Members perdent l'accès aux fonctionnalités retirées immédiatement
- Les CompanyModules liés aux Modules retirés sont désactivés
- **Aucune donnée n'est supprimée** — les données restent accessibles en lecture seule ou après re-upgrade

---

## 9. Règles Transversales

### 9.1 Suppression et Désactivation

- La suppression d'un Account entraîne la suppression en cascade de toutes les entités liées
- La désactivation est toujours préférée à la suppression (soft delete)
- La désactivation d'un Member préserve l'historique

### 9.2 Audit

- Toute modification de permission est journalisée
- Les actions en contexte collaboration sont traçables
- L'historique des attributions de rôles est conservé

### 9.3 Performance

- Les permissions effectives doivent être **cachées** et recalculées uniquement lors de modifications
- Le calcul de permissions doit être optimisé pour ne pas impacter les temps de réponse
- Les plafonds (Plan, Collaboration) doivent être pré-calculés

---

## 10. Glossaire

| Terme | Définition |
|-------|------------|
| **User** | Entité identitaire de base — toute personne sur la plateforme |
| **Account** | Entité racine côté client, créée par un User (Owner) |
| **Owner** | Le User propriétaire d'un Account (Member avec flag is_owner) |
| **Member** | Représentation d'un User au sein d'un Account |
| **Company** | Entité juridique/organisationnelle gérée dans un Account |
| **Module** | Domaine fonctionnel de l'ERP (RH, Finance, etc.) |
| **Feature** | Sous-ensemble fonctionnel d'un Module |
| **Permission** | Action atomique sur une ressource dans une Feature |
| **Role** | Ensemble nommé de Permissions, défini par Account |
| **Plan** | Plafond de fonctionnalités lié à l'abonnement |
| **Collaboration** | Accès inter-comptes à une Company spécifique |
| **PermissionSet** | Ensemble calculé de permissions effectives |
| **Plafond (Ceiling)** | Limite maximale de permissions applicable |
| **Moteur de Permissions** | Service partagé de résolution des permissions |
