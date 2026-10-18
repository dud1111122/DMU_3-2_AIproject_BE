package com.dmu.dshorts.domain.quiz.service;

import com.dmu.dshorts.domain.learning.entity.QuizAttempt;
import com.dmu.dshorts.domain.learning.entity.ReviewItem;
import com.dmu.dshorts.domain.learning.repository.QuizAttemptRepository;
import com.dmu.dshorts.domain.learning.repository.ReviewItemRepository;
import com.dmu.dshorts.domain.contentbatch.repository.ContentBatchRepository;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.domain.quiz.dto.QuizAttemptRequest;
import com.dmu.dshorts.domain.quiz.dto.QuizResponse;
import com.dmu.dshorts.domain.quiz.entity.Quiz;
import com.dmu.dshorts.domain.quiz.repository.QuizRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuizService {
    private final QuizRepository quizzes;private final QuizAttemptRepository attempts;private final ReviewItemRepository reviews;private final CatalogService catalog;private final ContentBatchRepository batches;
    public QuizService(QuizRepository quizzes,QuizAttemptRepository attempts,ReviewItemRepository reviews,CatalogService catalog,ContentBatchRepository batches){this.quizzes=quizzes;this.attempts=attempts;this.reviews=reviews;this.catalog=catalog;this.batches=batches;}
    @Transactional(readOnly=true) public QuizResponse byPost(String postId){catalog.requirePost(postId);return response(quizzes.findByPostId(postId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"퀴즈를 찾을 수 없습니다.")));}
    @Transactional(readOnly=true) public QuizResponse byId(String quizId){return response(require(quizId));}
    @Transactional(readOnly=true) public QuizResponse byBatch(String batchId){String postId=batches.findById(batchId).filter(b->b.getPostId()!=null).map(b->b.getPostId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"퀴즈가 준비되지 않았습니다."));return byPost(postId);}
    @Transactional public Map<String,Object> submit(String quizId,String user,QuizAttemptRequest request){
        Quiz quiz=require(quizId);Post post=quiz.getPost();List<Map<String,Object>> results=new ArrayList<>();int score=0;
        for(var answer:request.answers()){
            int order=answer.questionOrder();if(order<0||order>=quiz.getQuestions().size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"questionOrder가 범위를 벗어났습니다.");
            var question=quiz.getQuestions().get(order);int selected=answer.selectedOption();if(selected<0||selected>=question.getOptions().size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"selectedOption이 범위를 벗어났습니다.");
            boolean correct=selected==question.getCorrectOption();if(correct)score++;
            attempts.save(new QuizAttempt(user,quizId,order,selected,correct));
            if(!correct){int point=question.getKeyPointOrder()==null?order:question.getKeyPointOrder();ReviewItem item=reviews.findByUserIdAndPostIdAndKeyPointOrder(user,post.getId(),point).orElseGet(()->new ReviewItem(user,post.getId(),point,"QUIZ_MISSED"));reviews.save(item);}
            results.add(Map.of("questionOrder",order,"correct",correct,"selectedOption",selected,"correctOption",question.getCorrectOption(),"explanation",question.getExplanation()));
        }
        return Map.of("quizId",quizId,"score",score,"total",request.answers().size(),"results",results);
    }
    private QuizResponse response(Quiz quiz){List<QuizResponse.Question> qs=new ArrayList<>();for(int i=0;i<quiz.getQuestions().size();i++){var q=quiz.getQuestions().get(i);qs.add(new QuizResponse.Question(i,q.getPrompt(),q.getKeyPointOrder(),List.copyOf(q.getOptions())));}return new QuizResponse(quiz.getId(),quiz.getPost().getId(),qs);}
    private Quiz require(String id){return quizzes.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"퀴즈를 찾을 수 없습니다."));}
}
