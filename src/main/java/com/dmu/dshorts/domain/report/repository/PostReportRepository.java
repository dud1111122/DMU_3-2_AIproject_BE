package com.dmu.dshorts.domain.report.repository;
import com.dmu.dshorts.domain.report.entity.PostReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PostReportRepository extends JpaRepository<PostReport,Long>{List<PostReport> findByStatusOrderByCreatedAtDesc(String status);}
