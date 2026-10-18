package com.dmu.dshorts.domain.user.repository;

import com.dmu.dshorts.domain.user.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserPreferenceRepository extends JpaRepository<UserPreference,String> {}
