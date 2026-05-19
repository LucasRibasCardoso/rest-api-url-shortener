package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.domain.model.Role;

public interface RoleRepositoryPort {

  Role findDefaultRole();
}
