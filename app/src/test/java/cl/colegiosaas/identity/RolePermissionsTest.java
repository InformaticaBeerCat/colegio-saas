package cl.colegiosaas.identity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionsTest {

    @Test
    void onlyTheProviderHasPlatformPermission() {
        assertThat(Role.SUPER_ADMIN.permissions()).contains(Permission.PLATFORM);
        assertThat(Role.SCHOOL_ADMIN.permissions()).doesNotContain(Permission.PLATFORM).contains(Permission.USERS, Permission.NEWS_PUBLISH);
    }

    @Test
    void editorsNeedApprovalAndCannotManageUsers() {
        assertThat(Role.EDITOR.permissions()).contains(Permission.NEWS_EDIT).doesNotContain(Permission.NEWS_PUBLISH, Permission.USERS);
        assertThat(Role.PARENTS_CENTER_EDITOR.permissions()).contains(Permission.SECTION_PARENTS_CENTER)
                .doesNotContain(Permission.SECTION_STUDENT_COUNCIL);
    }

    @Test
    void familiesDoNotEnterThePanel() {
        assertThat(Role.GUARDIAN.isStaff()).isFalse();
        assertThat(Role.STUDENT.isStaff()).isFalse();
        assertThat(Arrays.stream(Role.values()).filter(Role::isStaff))
                .allSatisfy(role -> assertThat(role.permissions()).contains(Permission.PANEL_ACCESS));
    }

    @Test
    void mfaIsRecommendedForAdminsAndForWhoeverSeesMinorsData() {
        assertThat(Arrays.stream(Role.values()).filter(Role::mfaRecommended))
                .containsExactlyInAnyOrder(Role.SUPER_ADMIN, Role.SCHOOL_ADMIN, Role.CONSENT_MANAGER);
    }

    @Test
    void accountPermissionsAreTheUnionOfItsRoles() {
        UserAccount account = new UserAccount("x@colegio.cl", "X", Role.EDITOR, Role.CONSENT_MANAGER);

        assertThat(account.can(Permission.MEDIA_REVIEW)).isTrue();
        assertThat(account.can(Permission.INQUIRIES)).isTrue();
        assertThat(account.can(Permission.USERS)).isFalse();
    }
}
