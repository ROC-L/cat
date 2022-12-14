/*
 * Copyright (c) 2011-2018, Meituan Dianping. All Rights Reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dianping.cat.consumer.top;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.unidal.helper.Files;
import org.unidal.lookup.ComponentTestCase;

import com.dianping.cat.Constants;
import com.dianping.cat.analysis.MessageAnalyzer;
import com.dianping.cat.consumer.top.model.entity.TopReport;
import com.dianping.cat.helper.TimeHelper;
import com.dianping.cat.message.Event;
import com.dianping.cat.message.Heartbeat;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.message.internal.DefaultEvent;
import com.dianping.cat.message.internal.DefaultHeartbeat;
import com.dianping.cat.message.internal.DefaultTransaction;
import com.dianping.cat.message.spi.DefaultMessageTree;
import com.dianping.cat.message.spi.MessageTree;

public class TopAnalyzerTest extends ComponentTestCase {

	private long m_timestamp;

	private TopAnalyzer m_analyzer;

	private String m_domain = "group";

	@Before
	public void setUp() throws Exception {
		super.setUp();

		m_timestamp = 1385470800000L;
		m_analyzer = (TopAnalyzer) lookup(MessageAnalyzer.class, TopAnalyzer.ID);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm");
		Date date = sdf.parse("20120101 00:00");

		m_analyzer.initialize(date.getTime(), Constants.HOUR, Constants.MINUTE * 5);
	}

	@Test
	public void testProcess() throws Exception {
		for (int i = 1; i <= 100; i++) {
			MessageTree tree = ((DefaultMessageTree) generateMessageTree(i)).copyForTest();

			m_analyzer.process(tree);
		}

		TopReport report = m_analyzer.getReport(m_domain);

		String expected = Files.forIO().readFrom(getClass().getResourceAsStream("top_analyzer.xml"), "utf-8");
		Assert.assertEquals(expected.replaceAll("\r", ""), report.toString().replaceAll("\r", ""));
	}

	protected MessageTree generateMessageTree(int i) {
		MessageTree tree = new DefaultMessageTree();

		tree.setMessageId("" + i);
		tree.setDomain(m_domain);
		tree.setHostName("group001");
		tree.setIpAddress("192.168.1.1");
		tree.setThreadGroupName("cat");
		tree.setThreadName("Cat-ProblemAnalyzer-Test");
		if (i < 10) {
			DefaultEvent error = new DefaultEvent("Error", "Error");

			error.setTimestamp(m_timestamp);
			tree.setMessage(error);
		} else if (i < 20) {
			DefaultHeartbeat heartbeat = new DefaultHeartbeat("heartbeat", "heartbeat");

			heartbeat.setTimestamp(m_timestamp);
			tree.setMessage(heartbeat);
		} else {
			DefaultTransaction t = new DefaultTransaction("A", "n" + i % 2);

			t.setTimestamp(m_timestamp);
			t.setDurationInMillis(i * 50);

			Event error = new DefaultEvent("Error", "Error");
			((DefaultEvent) error).setTimestamp(m_timestamp + TimeHelper.ONE_MINUTE);
			Event exception = new DefaultEvent("Other", "Exception");
			Heartbeat heartbeat = new DefaultHeartbeat("heartbeat", "heartbeat");
			DefaultTransaction transaction = new DefaultTransaction("Transaction", "Transaction");

			transaction.setStatus(Transaction.SUCCESS);
			t.addChild(transaction);
			t.addChild(error);
			t.addChild(exception);
			t.addChild(heartbeat);
			tree.setMessage(t);
		}
		return tree;
	}

}
