package cl.colegiosaas.analytics;

import cl.colegiosaas.platform.SchoolTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resumen de visitas para el panel (REP-01): totales por día, páginas más vistas y de dónde llegan. */
@Service
public class AnalyticsService {

    private final JdbcTemplate jdbc;
    private final PageViewCounter counter;
    private final SchoolTime time;

    AnalyticsService(JdbcTemplate jdbc, PageViewCounter counter, SchoolTime time) {
        this.jdbc = jdbc;
        this.counter = counter;
        this.time = time;
    }

    public record Day(LocalDate date, long views) {
    }

    public record Count(String label, long count) {
    }

    /** @param peak el día con más visitas, para escalar las barras */
    public record Report(LocalDate from, LocalDate to, long total, long peak, List<Day> days, List<Count> pages,
                         List<Count> sources) {
    }

    @Transactional
    public Report report(int days) {
        counter.flush();
        LocalDate to = time.today();
        LocalDate from = to.minusDays(days - 1L);
        Map<LocalDate, Long> perDay = new HashMap<>();
        jdbc.query("select view_date, sum(views) from page_view_daily where view_date between ? and ? group by view_date",
                rs -> {
                    perDay.put(rs.getDate(1).toLocalDate(), rs.getLong(2));
                }, from, to);
        List<Day> series = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            series.add(new Day(day, perDay.getOrDefault(day, 0L)));
        }
        long total = series.stream().mapToLong(Day::views).sum();
        long peak = series.stream().mapToLong(Day::views).max().orElse(0);
        List<Count> pages = jdbc.query("""
                select path, sum(views) as total from page_view_daily where view_date between ? and ?
                group by path order by total desc, path limit 20
                """, (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), from, to);
        List<Count> sources = jdbc.query("""
                select source, sum(visits) as total from referrer_daily where view_date between ? and ?
                group by source order by total desc, source limit 10
                """, (rs, i) -> new Count(rs.getString(1), rs.getLong(2)), from, to);
        return new Report(from, to, total, peak, series, pages, sources);
    }
}
