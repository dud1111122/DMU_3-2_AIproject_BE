package com.dmu.dshorts.repository;
import com.dmu.dshorts.model.SharedCard; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface SharedCardRepository extends JpaRepository<SharedCard,Long>{List<SharedCard> findTop3ByPublishedTrueAndHiddenFalseOrderByIdAsc();}
