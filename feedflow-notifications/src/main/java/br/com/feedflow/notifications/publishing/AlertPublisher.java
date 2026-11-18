package br.com.feedflow.notifications.publishing;

import br.com.feedflow.notifications.domain.CriticalFeedback;

@FunctionalInterface
public interface AlertPublisher {

    void publish(CriticalFeedback feedback) throws AlertPublicationException;
}
