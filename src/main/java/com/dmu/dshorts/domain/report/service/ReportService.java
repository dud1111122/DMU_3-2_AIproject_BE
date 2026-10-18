package com.dmu.dshorts.domain.report.service;

import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.report.entity.PostReport;
import com.dmu.dshorts.domain.report.repository.PostReportRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
    private final PostRepository posts;private final PostReportRepository reports;
    public ReportService(PostRepository posts,PostReportRepository reports){this.posts=posts;this.reports=reports;}
    @Transactional public Map<String,Object> report(String user,String postId,String reason){if(!posts.existsById(postId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"게시물을 찾을 수 없습니다.");PostReport report=reports.save(new PostReport(user,postId,reason));return Map.of("reportId",report.getId(),"postId",postId,"status",report.getStatus());}
    @Transactional(readOnly=true) public List<Map<String,Object>> list(String status){return reports.findByStatusOrderByCreatedAtDesc(status==null?"OPEN":status.toUpperCase()).stream().map(r->Map.<String,Object>of("id",r.getId(),"postId",r.getPostId(),"reason",r.getReason(),"status",r.getStatus(),"createdAt",r.getCreatedAt())).toList();}
    @Transactional public Map<String,Object> hide(String postId){Post post=posts.findById(postId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"게시물을 찾을 수 없습니다."));post.hide();return Map.of("postId",postId,"hidden",true);}
}
