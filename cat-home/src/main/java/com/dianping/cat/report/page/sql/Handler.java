package com.dianping.cat.report.page.sql;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

import com.dianping.cat.Cat;
import com.dianping.cat.configuration.ServerConfigManager;
import com.dianping.cat.consumer.sql.model.entity.SqlReport;
import com.dianping.cat.consumer.sql.model.transform.DefaultSaxParser;
import com.dianping.cat.hadoop.dal.Dailyreport;
import com.dianping.cat.hadoop.dal.DailyreportDao;
import com.dianping.cat.hadoop.dal.DailyreportEntity;
import com.dianping.cat.hadoop.dal.Report;
import com.dianping.cat.hadoop.dal.ReportDao;
import com.dianping.cat.hadoop.dal.ReportEntity;
import com.dianping.cat.helper.CatString;
import com.dianping.cat.report.ReportPage;
import com.dianping.cat.report.page.model.sql.SqlReportMerger;
import com.dianping.cat.report.page.model.spi.ModelPeriod;
import com.dianping.cat.report.page.model.spi.ModelRequest;
import com.dianping.cat.report.page.model.spi.ModelResponse;
import com.dianping.cat.report.page.model.spi.ModelService;
import com.dianping.cat.report.task.TaskHelper;
import com.dianping.cat.report.task.sql.SqlMerger;
import com.site.dal.jdbc.DalException;
import com.site.lookup.annotation.Inject;
import com.site.lookup.util.StringUtils;
import com.site.web.mvc.PageHandler;
import com.site.web.mvc.annotation.InboundActionMeta;
import com.site.web.mvc.annotation.OutboundActionMeta;
import com.site.web.mvc.annotation.PayloadMeta;

/**
 * @author youyong
 */
public class Handler implements PageHandler<Context> {

	public static final long ONE_HOUR = 3600 * 1000L;

	@Inject
	protected ReportDao m_reportDao;

	@Inject
	private SqlMerger m_sqlMerger;

	@Inject
	private DailyreportDao m_dailyreportDao;

	@Inject
	private JspViewer m_jspViewer;

	@Inject
	private ServerConfigManager m_manager;

	@Inject(type = ModelService.class, value = "sql")
	private ModelService<SqlReport> m_service;

	private SqlReport getHourlyReport(Payload payload) {
		String domain = payload.getDomain();
		String date = String.valueOf(payload.getDate());
		ModelRequest request = new ModelRequest(domain, payload.getPeriod()) //
		      .setProperty("date", date) //
		      .setProperty("database", payload.getDatabase());

		if (m_service.isEligable(request)) {
			ModelResponse<SqlReport> response = m_service.invoke(request);
			SqlReport report = response.getModel();
			return report;
		} else {
			throw new RuntimeException("Internal error: no eligable sql service registered for " + request + "!");
		}
	}

	@Override
	@PayloadMeta(Payload.class)
	@InboundActionMeta(name = "sql")
	public void handleInbound(Context ctx) throws ServletException, IOException {
		// display only, no action here
	}

	@Override
	@OutboundActionMeta(name = "sql")
	public void handleOutbound(Context ctx) throws ServletException, IOException {
		Model model = new Model(ctx);
		Payload payload = ctx.getPayload();

		normalize(model, payload);
		String database = payload.getDatabase();

		switch (payload.getAction()) {
		case HISTORY_REPORT:
			SqlReport historyReport = showSummarizeReport(model, payload);

			long historyDuration = historyReport.getEndTime().getTime() - historyReport.getStartTime().getTime();
			DisplaySqlReport displayHistorySql = new DisplaySqlReport().setDatabase(database).setDuration(
			      historyDuration);

			displayHistorySql.setSortBy(payload.getSortBy()).visitSqlReport(historyReport);
			model.setReport(historyReport);
			model.setDisplaySqlReport(displayHistorySql);
			break;
		case HOURLY_REPORT:
			long hourlyDuration = ONE_HOUR;
			if (ModelPeriod.CURRENT == payload.getPeriod()) {
				hourlyDuration = System.currentTimeMillis() % ONE_HOUR;
			}
			SqlReport hourlyReport = getHourlyReport(payload);
			DisplaySqlReport displaySql = new DisplaySqlReport().setDatabase(database).setDuration(hourlyDuration);

			displaySql.setSortBy(payload.getSortBy()).visitSqlReport(hourlyReport);
			model.setReport(hourlyReport);
			model.setDisplaySqlReport(displaySql);
			break;
		}
		m_jspViewer.view(ctx, model);
	}

	public void normalize(Model model, Payload payload) {
		Action action = payload.getAction();
		model.setAction(action);
		model.setPage(ReportPage.SQL);

		if (StringUtils.isEmpty(payload.getDomain())) {
			payload.setDomain(m_manager.getConsoleDefaultDomain());
		}
		if (StringUtils.isEmpty(payload.getDatabase())) {
			payload.setDatabase(CatString.ALL_Database);
		}
		model.setDisplayDomain(payload.getDomain());
		model.setDatabase(payload.getDatabase());
		
		if (payload.getPeriod().isFuture()) {
			model.setLongDate(payload.getCurrentDate());
		} else {
			model.setLongDate(payload.getDate());
		}
		if (action == Action.HISTORY_REPORT) {
			String type = payload.getReportType();
			if (type == null || type.length() == 0) {
				payload.setReportType("day");
			}
			model.setReportType(payload.getReportType());
			payload.computeStartDate();
			if (!payload.isToday()) {
				payload.setYesterdayDefault();
			}
			model.setLongDate(payload.getDate());
			model.setCustomDate(payload.getHistoryStartDate(), payload.getHistoryEndDate());
		}
	}

	private SqlReport showSummarizeReport(Model model, Payload payload) {
		SqlReport sqlReport = null;
		String domain = payload.getDomain();
		Date start = payload.getHistoryStartDate();
		Date end = payload.getHistoryEndDate();
		Date currentDayStart = TaskHelper.todayZero(new Date());

		if (currentDayStart.getTime() == start.getTime()) {
			try {
				List<Report> reports = m_reportDao.findAllByDomainNameDuration(start, end, domain, "sql",
				      ReportEntity.READSET_FULL);
				List<Report> allReports = m_reportDao.findAllByDomainNameDuration(start, end, null, "sql",
				      ReportEntity.READSET_DOMAIN_NAME);

				Set<String> sqls = new HashSet<String>();
				for (Report report : allReports) {
					sqls.add(report.getDomain());
				}
				sqlReport = m_sqlMerger.mergeForDaily(domain, reports, sqls);
			} catch (DalException e) {
				Cat.logError(e);
			}
		} else {
			try {
				List<Dailyreport> reports = m_dailyreportDao.findAllByDomainNameDuration(start, end, domain, "sql",
				      DailyreportEntity.READSET_FULL);
				SqlReportMerger merger = new SqlReportMerger(new SqlReport(domain));
				for (Dailyreport report : reports) {
					String xml = report.getContent();
					SqlReport reportModel = DefaultSaxParser.parse(xml);
					reportModel.accept(merger);
				}
				sqlReport = merger.getSqlReport();
			} catch (Exception e) {
				Cat.logError(e);
			}
		}

		sqlReport.setStartTime(start);
		sqlReport.setEndTime(end);

		return sqlReport;
	}
}
