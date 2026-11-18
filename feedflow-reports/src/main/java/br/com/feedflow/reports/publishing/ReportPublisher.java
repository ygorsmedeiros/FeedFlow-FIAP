package br.com.feedflow.reports.publishing;

@FunctionalInterface
public interface ReportPublisher {

    void publish(String report);
}
