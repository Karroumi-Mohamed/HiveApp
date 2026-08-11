package com.hiveapp.platform.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.company.domain.entity.Company;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.company.domain.entity.GroupMembership;
import com.hiveapp.platform.client.company.domain.entity.GroupStructureTemplate;
import com.hiveapp.platform.client.company.domain.entity.GroupTemplateNode;
import com.hiveapp.platform.client.company.domain.entity.OrganizationGroup;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.role.domain.entity.Role;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

class TenantEntityInvariantTest {

    @Test
    void everyCrossTenantAggregateRegistersPersistenceCallbacks() throws Exception {
        for (Class<?> entity : new Class<?>[] {
                Member.class,
                Role.class,
                MemberRole.class,
                MemberPermissionOverride.class,
                Collaboration.class
        }) {
            var callback = entity.getDeclaredMethod("validateTenantInvariant");
            assertThat(callback.getAnnotation(PrePersist.class)).as(entity.getSimpleName()).isNotNull();
            assertThat(callback.getAnnotation(PreUpdate.class)).as(entity.getSimpleName()).isNotNull();
        }
        for (Class<?> entity : new Class<?>[] {
                OrganizationGroup.class,
                GroupMembership.class,
                GroupStructureTemplate.class,
                GroupTemplateNode.class
        }) {
            var callback = entity.getDeclaredMethod("validateInvariant");
            assertThat(callback.getAnnotation(PrePersist.class)).as(entity.getSimpleName()).isNotNull();
            assertThat(callback.getAnnotation(PreUpdate.class)).as(entity.getSimpleName()).isNotNull();
        }
    }

    @Test
    void accountOwnerIsARequiredUserRelationshipWithDatabaseForeignKeyMetadata() throws Exception {
        Field owner = Account.class.getDeclaredField("owner");
        OneToOne relationship = owner.getAnnotation(OneToOne.class);
        JoinColumn join = owner.getAnnotation(JoinColumn.class);

        assertThat(relationship).isNotNull();
        assertThat(relationship.optional()).isFalse();
        assertThat(join).isNotNull();
        assertThat(join.name()).isEqualTo("owner_id");
        assertThat(join.nullable()).isFalse();
        assertThat(join.unique()).isTrue();
        assertThat(join.foreignKey().name()).isEqualTo("fk_accounts_owner");
    }

    @Test
    void ownerMemberMustReferenceTheAccountOwner() {
        Account account = account(user());
        Member member = member(account, user());
        member.setOwner(true);

        assertInvalid(member, "An owner member must reference the account owner");
    }

    @Test
    void roleCompanyMustBelongToRoleAccount() {
        Role role = role(account(user()), company(account(user())));

        assertInvalid(role, "Role boundary company must belong to the role account");
    }

    @Test
    void memberRoleMustNotCombineDifferentAccounts() {
        MemberRole assignment = new MemberRole();
        assignment.setMember(member(account(user()), user()));
        assignment.setRole(role(account(user()), null));

        assertInvalid(assignment, "Member and role must belong to the same account");
    }

    @Test
    void companyRoleTemplateRequiresItsBoundaryCompany() {
        Role role = role(account(user()), null);
        role.setTemplateBoundary(com.hiveapp.platform.client.role.domain.constant.RoleTemplateBoundary.COMPANY);

        assertInvalid(role, "Company role templates require a boundary company");
    }

    @Test
    void companyAssignmentRequiresItsScopeCompany() {
        Account account = account(user());
        MemberRole assignment = new MemberRole();
        assignment.setMember(member(account, user()));
        assignment.setRole(role(account, null));
        assignment.setEffectScope(com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope.COMPANY);

        assertInvalid(assignment, "Company role assignments require a scope company");
    }

    @Test
    void companyBoundTemplateCannotBeAssignedInAnotherCompany() {
        Account account = account(user());
        Company boundary = company(account);
        MemberRole assignment = new MemberRole();
        assignment.setMember(member(account, user()));
        assignment.setRole(role(account, boundary));
        assignment.setEffectScope(com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope.COMPANY);
        assignment.setScopeCompany(company(account));

        assertInvalid(assignment, "A Company-bound role template must be assigned inside its boundary company");
    }

    @Test
    void permissionOverrideCompanyMustBelongToMemberAccount() {
        MemberPermissionOverride override = new MemberPermissionOverride();
        Account memberAccount = account(user());
        override.setMember(member(memberAccount, user()));
        override.setCreatedBy(member(memberAccount, user()));
        override.setScope(PermissionOverrideScope.COMPANY);
        override.setScopeCompany(company(account(user())));
        override.setDecision(PermissionOverrideDecision.DENY);
        override.setReason("Security exception");

        assertInvalid(override, "Member permission exception company must belong to the member account");
    }

    @Test
    void groupParentMustBelongToTheSameCompany() {
        OrganizationGroup group = new OrganizationGroup();
        group.setCompany(company(account(user())));
        OrganizationGroup parent = new OrganizationGroup();
        parent.setCompany(company(account(user())));
        group.setParent(parent);

        assertInvalid(group, "Group parent must belong to the same company");
    }

    @Test
    void groupMembershipMemberMustBelongToTheCompanyAccount() {
        OrganizationGroup group = new OrganizationGroup();
        group.setCompany(company(account(user())));
        GroupMembership membership = new GroupMembership();
        membership.setGroup(group);
        membership.setMember(member(account(user()), user()));

        assertInvalid(membership, "Group member must belong to the company account");
    }

    @Test
    void collaborationCompanyMustBelongToProviderAccount() {
        Collaboration collaboration = new Collaboration();
        collaboration.setClientAccount(account(user()));
        collaboration.setProviderAccount(account(user()));
        collaboration.setCompany(company(account(user())));

        assertInvalid(collaboration, "Collaboration company must belong to the provider account");
    }

    @Test
    void validTenantRelationshipsPassEveryCallback() {
        User owner = user();
        Account account = account(owner);
        Company company = company(account);
        Member member = member(account, owner);
        member.setOwner(true);
        Role role = role(account, company);
        MemberRole assignment = new MemberRole();
        assignment.setMember(member);
        assignment.setRole(role);
        assignment.setEffectScope(com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope.COMPANY);
        assignment.setScopeCompany(company);
        Collaboration collaboration = new Collaboration();
        collaboration.setClientAccount(account(user()));
        collaboration.setProviderAccount(account);
        collaboration.setCompany(company);

        assertThatCode(() -> validate(member)).doesNotThrowAnyException();
        assertThatCode(() -> validate(role)).doesNotThrowAnyException();
        assertThatCode(() -> validate(assignment)).doesNotThrowAnyException();
        assertThatCode(() -> validate(collaboration)).doesNotThrowAnyException();
    }

    private static void assertInvalid(Object entity, String message) {
        assertThatThrownBy(() -> validate(entity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
    }

    private static void validate(Object entity) {
        if (ReflectionUtils.findMethod(entity.getClass(), "validateTenantInvariant") != null) {
            ReflectionTestUtils.invokeMethod(entity, "validateTenantInvariant");
        } else {
            ReflectionTestUtils.invokeMethod(entity, "validateInvariant");
        }
    }

    private static User user() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static Account account(User owner) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        account.setOwner(owner);
        return account;
    }

    private static Company company(Account account) {
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", UUID.randomUUID());
        company.setAccount(account);
        return company;
    }

    private static Member member(Account account, User user) {
        Member member = new Member();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        member.setAccount(account);
        member.setUser(user);
        return member;
    }

    private static Role role(Account account, Company company) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        role.setAccount(account);
        role.setTemplateBoundary(com.hiveapp.platform.client.role.domain.constant.RoleTemplateBoundary.COMPANY);
        role.setBoundaryCompany(company);
        return role;
    }
}
