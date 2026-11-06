package com.dmu.dshorts.domain.report.repository;

import com.dmu.dshorts.domain.report.entity.PostReport;
import com.dmu.dshorts.domain.report.entity.ReportStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    Optional<PostReport> findByUserIdAndPostId(Long userId, Long postId);

    // Admin list, newest first by (createdAt, id). First page / next page variants, with and without status.

    @Query("select r from PostReport r order by r.createdAt desc, r.id desc")
    List<PostReport> findLatest(Pageable page);

    @Query("select r from PostReport r where (r.createdAt < :time or (r.createdAt = :time and r.id < :id))"
            + " order by r.createdAt desc, r.id desc")
    List<PostReport> findLatestAfter(@Param("time") Instant time, @Param("id") Long id, Pageable page);

    @Query("select r from PostReport r where r.status = :status order by r.createdAt desc, r.id desc")
    List<PostReport> findLatestByStatus(@Param("status") ReportStatus status, Pageable page);

    @Query("select r from PostReport r where r.status = :status"
            + " and (r.createdAt < :time or (r.createdAt = :time and r.id < :id))"
            + " order by r.createdAt desc, r.id desc")
    List<PostReport> findLatestByStatusAfter(@Param("status") ReportStatus status, @Param("time") Instant time,
                                             @Param("id") Long id, Pageable page);
}
