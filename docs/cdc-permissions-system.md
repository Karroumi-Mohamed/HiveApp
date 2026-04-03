# Cahier des Charges — HiveApp

## Système de Permissions et Entités Fondamentales

**Version :** 1.2
**Date :** 2026-04-02
**Statut :** Brouillon

---

## 1. Introduction

### 1.1 Objet du Document

Ce cahier des charges définit les spécifications fonctionnelles et techniques du système de permissions et des entités fondamentales de la plateforme HiveApp. Ce document se concentre exclusivement sur le socle du système — identité, comptes, rôles, permissions et collaboration — sans aborder le fonctionnement interne des modules métier (RH, Finance, etc.).

### 1.2 Périmètre

Le périmètre couvre :

- Le modèle d'''identité (User)
- La plateforme d'''administration HiveApp
- La plateforme client HiveApp
- Le moteur de permissions partagé
- Le système de collaboration inter-comptes
- Le registre des modules et fonctionnalités
- Le système de plans et abonnements

---

## 2. Architecture Générale

### 2.1 Vue d'''Ensemble

HiveApp est composé de deux plateformes distinctes partageant un socle commun :

| Composant | Description |
|-----------|-------------|
| **Couche d'''identité partagée** | Entité User unique, authentification commune |
| **Moteur de permissions partagé** | Un seul moteur, deux contextes d'''exécution |
| **Plateforme d'''administration** | Gestion de la plateforme, plans, modules, supervision |
| **Plateforme client** | Comptes, entreprises, membres, permissions, collaboration |

### 2.2 Principe Fondamental

Le moteur de permissions est **unique et partagé**. Il opère de manière identique dans les deux contextes (administration et client). Seules les données diffèrent :

- **Contexte Admin** : AdminUser, AdminRole, AdminPermission
- **Contexte Client Propre** : Member, Role, Permission avec plafond du Plan
- **Contexte Collaboration** : Member, Role, Permission avec plafond défini par le client

---

## 3. Modèle d'''Identité

### 3.1 User

L'''entité **User** est le socle identitaire de toute personne interagissant avec la plateforme.

**Règles :**
- Un User peut posséder au maximum **un seul Account** en tant que propriétaire.
- Un User peut être Member dans **plusieurs Accounts**.
- Le Owner est un Member avec un flag `is_owner` — ce n'''est pas une entité séparée.

---

## 4. Plateforme d'''Administration

### 4.1 AdminUser

Un AdminUser est un User ayant accès à la plateforme d'''administration.

### 4.2 AdminRole

Les rôles administrateurs sont **entièrement dynamiques**. Chaque AdminRole est composé d'''un ensemble d'''AdminPermissions.

### 4.3 AdminPermission

**Structure :** `code`, `action`, `resource`.

**Règle fondamentale — Séparation stricte :**
Les AdminPermissions sont un espace de nommage **entièrement distinct** des Permissions du registre ERP. Ils ne sont **jamais mélangés**. Il n'''existe aucune relation entre les deux entités.

### 4.4 Registre des Modules et Fonctionnalités

Le registre est la **source de vérité** de tout ce que HiveApp propose.
- **Module** : Domaine fonctionnel (RH, Finance).
- **Feature** : Sous-ensemble fonctionnel (Gestion des employés).
- **Permission** : Action atomique (`hr.employees.create`).

### 4.5 Plans (Modèle de Templates)

Un Plan est un **Template (blueprint)** de fonctionnalités et de quotas.

**Caractéristiques :**
- Défini par les Admins via le registre.
- Un Plan possède un code unique (ex: `FREE`, `PRO`).
- **Cycle FOREVER** : Le cycle de facturation `FOREVER` est utilisé pour les plans qui n'''expirent jamais (ex: le plan gratuit).
- **Attribution automatique** : Le plan identifié par le code `FREE` est attribué par défaut à tout nouvel Account.

---

## 5. Plateforme Client

### 5.1 Account

L'''entité racine côté client. Lié à un Plan via une **Subscription**.

### 5.2 Company (Entreprise)

Entité organisationnelle au sein d'''un Account. Le nombre de Companies est limité par le Plan.

### 5.3 Department (Département)

Structure organisationnelle **purement informative**.
- Nestable (parent_id).
- Manager obligatoire : doit être un **Member du même Account**.
- Zéro impact sur les permissions.

### 5.4 Équipes (Teams) — Hors périmètre fondation

Les équipes sont des concepts **spécifiques aux modules métier** (CRM, Projet). Elles n'''existent pas dans la couche fondation et n'''ont aucun impact sur le moteur de permissions global.

### 5.5 Member (Membre)

Représentation d'''un User au sein d'''un Account. Accède aux ressources via ses Roles.

### 5.6 Système de Rôles et Permissions

- **Role** : Ensemble de permissions **scopé à une Company**.
- **MemberRole** : Pivot (Member + Role + Company). Le scope Company peut être `NULL` (Account-wide).
- **MemberPermissionOverride** : Trio (Member + Permission + Company) pour un `ALLOW/DENY` explicite.

### 5.7 Calcul des Permissions Effectives (Le Sieve)

Le calcul suit une cascade de filtres (Sieve) :
1. **Collaboration** : Si contexte B2B, le plafond est défini par le client.
2. **Plan** : Le plafond est défini par le Plan + les Overrides de la Subscription.
3. **User** : Union des Roles + Overrides directs.

---

## 6. Système de Collaboration (B2B)

Lie un Account client à un Account prestataire sur une **Company spécifique**. Le plafond de permissions est défini par le client via `CollaborationPermission`.

---

## 7. Moteur de Permissions

Service partagé qui résout les `PermissionSet` en appliquant les intersections : `Roles ∩ Plafond (Plan ou Collab)`.

---

## 8. Plans et Abonnements

### 8.1 Subscription (L'''Instance)

La Subscription lie un Account à un Plan Template.
- **Custom Overrides (JSONB)** : Permet d'''ajouter des permissions ou de modifier des quotas pour un compte spécifique sans créer de nouveau Plan Template.

---

## 9. Glossaire

| Terme | Définition |
|-------|------------|
| **Plan Template** | Blueprint global définissant un ensemble de fonctionnalités (ex: FREE, PRO). |
| **Subscription** | Instance d'''abonnement d'''un Account à un Plan, avec overrides possibles. |
| **FOREVER** | Cycle de facturation pour les plans sans expiration. |
| **Department** | Structure purement informative au sein d'''une Company. |
| **AdminPermission** | Namespace distinct pour les opérations de plateforme. |
