/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.web.component.collection;

import be.nabu.eai.developer.api.ApplicationProvider;
import be.nabu.eai.developer.collection.ApplicationManager;
import be.nabu.eai.module.web.application.api.TargetAudience;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import javafx.scene.Node;

public class DashboardApplicationProvider implements ApplicationProvider {

	@Override
	public Node getLargeCreateIcon() {
		return ApplicationManager.newNode("application/application-dashboard-large.png", "Dashboard Application", "Get insight into the data and take action.");
	}
	
	@Override
	public String getSubType() {
		return "dashboard";
	}

	@Override
	public Node getSummaryView(Entry entry) {
		return ConsumerApplicationProvider.getSummaryView(entry, "application/application-dashboard-large.png");
	}

	@Override
	public void initialize(Entry newApplication, String version) {
		ConsumerApplicationProvider.getOrCreateWebApplication((RepositoryEntry) newApplication, version, TargetAudience.BUSINESS);
	}
	
	@Override
	public String suggestName(Entry entry) {
		return entry.getChild("dashboard") == null ? "Dashboard" : null;
	}

	@Override
	public String getMediumIcon() {
		return "application/application-dashboard-medium.png";
	}

	@Override
	public String getSmallIcon() {
		return "application/application-dashboard-small.png";
	}

	@Override
	public String getLargeIcon() {
		return "application/application-dashboard-large.png";
	}
}
