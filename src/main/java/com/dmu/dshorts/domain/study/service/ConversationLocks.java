package com.dmu.dshorts.domain.study.service;

import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Single-process, per-conversation guard so one room handles one chat turn at a time (DB_설계 "단일 프로세스의
 * 대화방별 잠금"). A second request for the same room while one is running gets 409 REQUEST_IN_PROGRESS instead
 * of waiting up to the AI timeout. The guard is held across the AI call, outside any DB transaction.
 */
@Component
public class ConversationLocks {

    private final Set<Long> inProgress = ConcurrentHashMap.newKeySet();

    /** Acquires the room or throws 409 REQUEST_IN_PROGRESS. Release with {@link #release(Long)} in finally. */
    public void acquire(Long conversationId) {
        if (!inProgress.add(conversationId)) {
            throw new ApiException(ErrorCode.REQUEST_IN_PROGRESS);
        }
    }

    public void release(Long conversationId) {
        inProgress.remove(conversationId);
    }

    public boolean isBusy(Long conversationId) {
        return inProgress.contains(conversationId);
    }
}
