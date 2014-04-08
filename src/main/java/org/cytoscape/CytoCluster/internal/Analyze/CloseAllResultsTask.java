package org.cytoscape.CytoCluster.internal.Analyze;

import java.util.Collection;

import org.cytoscape.CytoCluster.internal.CommonUI.ResultPanel;
import org.cytoscape.CytoCluster.internal.MyUtils.ClusterUtil;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.application.swing.CytoPanel;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.application.swing.CytoPanelState;
import org.cytoscape.work.ProvidesTitle;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

public class CloseAllResultsTask implements Task {

	@Tunable(description = "<html>You are about to close the CytoCluster app.<br />Do you want to continue?</html>", params = "ForceSetDirectly=true")
	public boolean close = true;
	private final CySwingApplication swingApplication;
	private final ClusterUtil mcodeUtil;

	public CloseAllResultsTask(CySwingApplication swingApplication,
			ClusterUtil mcodeUtil) {
		this.swingApplication = swingApplication;
		this.mcodeUtil = mcodeUtil;
	}

	@ProvidesTitle
	public String getTitle() {
		return "Close All Results";
	}

	public void run(TaskMonitor taskMonitor) throws Exception {
		if (this.close) {
			Collection<ResultPanel> resultPanels = this.mcodeUtil
					.getResultPanels();

			for (ResultPanel panel : resultPanels) {
				panel.discard(false);
			}

			CytoPanel cytoPanel = this.swingApplication
					.getCytoPanel(CytoPanelName.WEST);

			if (cytoPanel.getCytoPanelComponentCount() == 0)
				cytoPanel.setState(CytoPanelState.HIDE);
		}
	}

	public void cancel() {

	}
}