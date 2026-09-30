package com.gucardev.jwtauthrefreshtokenroles.user.repository;

import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserClaimEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserClaimRepository extends JpaRepository<UserClaimEntity, Long> {

    /** Stored claim names that appear in the given set; used to detect names that became reserved. */
    @Query("select distinct c.name from UserClaimEntity c where c.name in :names")
    List<String> findDistinctNamesIn(@Param("names") Collection<String> names);
}
