package cl.colegiosaas.identity.web;

import cl.colegiosaas.identity.Role;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
public class RolesForm {

    private Set<Role> roles = EnumSet.noneOf(Role.class);
}
