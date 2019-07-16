package org.springframework.integration.http.inbound.continuation;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.springframework.integration.http.inbound.continuation.Continuation.ID_ATTRIBUTE;

public abstract class Continuations {

    private static final ConcurrentMap<Integer, Continuation> continuations = new ConcurrentHashMap<>();

    public static Continuation getContinuation(HttpServletRequest request, long timeout) {
        Integer id = (Integer) request.getAttribute(ID_ATTRIBUTE);
        Continuation continuation = null;
        if (id != null) {
            continuation = continuations.get(id);
        }
        synchronized (Continuations.class) {
            if (continuation == null) {
                id = System.identityHashCode(request);
                request.setAttribute(ID_ATTRIBUTE, id);
                continuation = new AsyncContextContinuation(id, timeout);
                continuations.put(id, continuation);
            }
            return continuation;
        }
    }

    public static Continuation getContinuation(Integer id) {
        return continuations.get(id);
    }

    static void dispose(Continuation continuation) {
        continuations.remove(continuation.getId());
    }

}
