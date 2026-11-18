package br.com.feedflow.reports.repository;

import br.com.feedflow.reports.domain.Feedback;
import br.com.feedflow.reports.domain.WeeklyPeriod;

import java.util.List;

@FunctionalInterface
public interface FeedbackReportRepository {

    List<Feedback> findByWeek(WeeklyPeriod period);
}
