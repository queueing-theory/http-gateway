package org.springframework.integration.http.inbound.continuation;

import org.springframework.messaging.Message;

import javax.servlet.http.HttpServletRequest;

public interface Continuation {
    String ID_ATTRIBUTE = Continuations.class.getName() + ".ID";

    boolean setReply(Message<?> message);

    Message<?> dispatch(HttpServletRequest request);

    Integer getId();

    boolean isExpired();

}
