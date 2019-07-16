package org.springframework.integration.http.inbound.continuation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.messaging.Message;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

class AsyncContextContinuation implements Continuation, AsyncListener {
    private Log logger = LogFactory.getLog(getClass());
    private final Object lock = new Object();
    private final long timeout;
    private AsyncContext context;
    private volatile boolean expired = false;
    private Integer id;
    private Message<?> reply;

    AsyncContextContinuation(Integer id, long timeout) {
        this.id = id;
        this.timeout = timeout;
    }

    @Override
    public Integer getId() {
        return this.id;
    }

    public boolean setReply(Message<?> message) {
        if (isExpired()) {
            return false;
        }
        synchronized (lock) {
            this.reply = message;
            resume();
        }
        return true;
    }

    @Override
    public Message<?> dispatch(HttpServletRequest request) {
        synchronized (this.lock) {
            if (this.reply != null) {
                dispose();
                return this.reply;
            }
            if (isExpired()) {
                // resuming because the context expired.
                dispose();
                return null;
            }
            // initial request
            context = request.startAsync();
            context.setTimeout(timeout);
            context.addListener(this);
            return null;
        }
    }

    private void dispose() {
        Continuations.dispose(this);
    }

    public boolean isExpired() {
        return expired;
    }

    private void resume() {
        synchronized (lock) {
            if (logger.isDebugEnabled()) {
                logger.debug("Resuming request with " + this.reply + " for " + this.context.getRequest().getRemoteAddr());
            }
            AsyncContext context = this.context;
            if (context == null) {
                throw new IllegalStateException();
            }
            this.context.dispatch();
        }
    }

    @Override
    public void onComplete(AsyncEvent asyncEvent) throws IOException {

    }

    @Override
    public void onTimeout(AsyncEvent asyncEvent) throws IOException {
        expired = true;
        if (asyncEvent.getSuppliedRequest().isAsyncStarted()) asyncEvent.getAsyncContext().dispatch();
        if (logger.isDebugEnabled()) {
            logger.debug("Timed out for " + asyncEvent.getSuppliedRequest().getRemoteAddr() + " after " + timeout + " ms");
        }
    }

    @Override
    public void onError(AsyncEvent asyncEvent) throws IOException {

    }

    @Override
    public void onStartAsync(AsyncEvent asyncEvent) throws IOException {

    }
}
