package br.com.feedflow.reports.publishing;

public final class ReportPublicationException extends RuntimeException {

    public ReportPublicationException(String message) {
        super(message);
    }

    public ReportPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
