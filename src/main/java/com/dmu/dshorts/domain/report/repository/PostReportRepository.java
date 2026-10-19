package com.dmu.dshorts.domain.report.repository;

import com.dmu.dshorts.domain.report.entity.PostReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    Optional<PostReport> findByUserIdAndPostId(Long userId, Long postId);
}
