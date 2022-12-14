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
package com.dianping.cat.status;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.dianping.cat.Cat;
import com.dianping.cat.component.ComponentContext;
import com.dianping.cat.component.lifecycle.Initializable;
import com.dianping.cat.configuration.ConfigureManager;
import com.dianping.cat.configuration.ConfigureProperty;
import com.dianping.cat.configuration.NetworkInterfaceManager;
import com.dianping.cat.message.Event;
import com.dianping.cat.message.Heartbeat;
import com.dianping.cat.message.Message;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.message.internal.MilliSecondTimer;
import com.dianping.cat.status.model.entity.Extension;
import com.dianping.cat.status.model.entity.StatusInfo;
import com.dianping.cat.support.Threads.Task;

// Component
public class StatusUpdateTask implements Task, Initializable {
	// Inject
	private MessageStatistics m_statistics;

	// Inject
	private ConfigureManager m_configureManager;

	private boolean m_active = true;

	private String m_ipAddress;

	private long m_interval = 60 * 1000; // 60 seconds

	private String m_jars;

	private void buildClasspath() {
		ClassLoader loader = StatusUpdateTask.class.getClassLoader();
		StringBuilder sb = new StringBuilder();

		buildClasspath(loader, sb);
		if (sb.length() > 0) {
			m_jars = sb.substring(0, sb.length() - 1);
		}
	}

	private void buildClasspath(ClassLoader loader, StringBuilder sb) {
		if (loader instanceof URLClassLoader) {
			URL[] urLs = ((URLClassLoader) loader).getURLs();
			for (URL url : urLs) {
				String jar = parseJar(url.toExternalForm());

				if (jar != null) {
					sb.append(jar).append(',');
				}
			}
			ClassLoader parent = loader.getParent();

			buildClasspath(parent, sb);
		}
	}

	private void buildExtensionData(StatusInfo status) {
		StatusExtensionRegister res = StatusExtensionRegister.getInstance();
		List<StatusExtension> extensions = res.getStatusExtension();
		int length = extensions.size();

		for (int i = 0; i < length; i++) {
			StatusExtension extension = extensions.get(i);
			String id = extension.getId();
			String des = extension.getDescription();
			Map<String, String> propertis = extension.getProperties();
			Extension item = status.findOrCreateExtension(id).setDescription(des);

			for (Entry<String, String> entry : propertis.entrySet()) {
				try {
					double value = Double.parseDouble(entry.getValue());
					item.findOrCreateExtensionDetail(entry.getKey()).setValue(value);
				} catch (Exception e) {
					Cat.logError("StatusExtension can only be double type", e);
				}
			}
		}
	}

	@Override
	public String getName() {
		return "StatusUpdateTask";
	}

	@Override
	public void initialize(ComponentContext ctx) {
		m_configureManager = ctx.lookup(ConfigureManager.class);
		m_statistics = ctx.lookup(MessageStatistics.class);
		m_ipAddress = NetworkInterfaceManager.INSTANCE.getLocalHostAddress();
	}

	private String parseJar(String path) {
		if (path.endsWith(".jar")) {
			int index = path.lastIndexOf('/');

			if (index > -1) {
				return path.substring(index + 1);
			}
		}
		return null;
	}

	@Override
	public void run() {
		// try to wait cat client init success
		try {
			Thread.sleep(10 * 1000);
		} catch (InterruptedException e) {
			return;
		}

		while (true) {
			Calendar cal = Calendar.getInstance();
			int second = cal.get(Calendar.SECOND);

			// try to avoid send heartbeat at 59-01 second
			if (second < 2 || second > 58) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// ignore it
				}
			} else {
				break;
			}
		}

		try {
			buildClasspath();
		} catch (Exception e) {
			e.printStackTrace();
		}

		Transaction reboot = Cat.newTransaction("System", "Reboot");

		reboot.setStatus(Message.SUCCESS);
		Cat.logEvent("Reboot", NetworkInterfaceManager.INSTANCE.getLocalHostAddress(), Message.SUCCESS, null);
		reboot.complete();

		while (m_active) {
			long start = MilliSecondTimer.currentTimeMillis();

			if (m_configureManager.isEnabled()) {
				Transaction t = Cat.newTransaction("System", "Status");
				Heartbeat h = Cat.newHeartbeat("Heartbeat", m_ipAddress);
				StatusInfo status = new StatusInfo();
				boolean dumpLocked = m_configureManager.getBooleanProperty(ConfigureProperty.DUMP_LOCKED, false);

				t.addData("dumpLocked", dumpLocked);

				StatusInfoCollector collector = new StatusInfoCollector(m_statistics, m_jars);

				try {
					status.accept(collector.setDumpLocked(dumpLocked));

					buildExtensionData(status);
					h.addData(status.toString());
					h.setStatus(Message.SUCCESS);
				} catch (Throwable e) {
					h.setStatus(e);
					Cat.logError(e);
				} finally {
					h.complete();
				}
				Cat.logEvent("Heartbeat", "jstack", Event.SUCCESS, collector.getJstackInfo());
				t.setStatus(Message.SUCCESS);
				t.complete();
			}

			long elapsed = MilliSecondTimer.currentTimeMillis() - start;

			if (elapsed < m_interval) {
				try {
					Thread.sleep(m_interval - elapsed);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}

	public void setInterval(long interval) {
		m_interval = interval;
	}

	@Override
	public void shutdown() {
		m_active = false;
	}
}
