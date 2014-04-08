package org.cytoscape.CytoCluster.internal.Analyze;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.swing.JOptionPane;

import org.cytoscape.CytoCluster.internal.AbstractVizAction;
import org.cytoscape.CytoCluster.internal.ClusterAnalysis.Algorithm.Algorithm;
import org.cytoscape.CytoCluster.internal.ClusterAnalysis.Algorithm.EAGLE;
import org.cytoscape.CytoCluster.internal.ClusterAnalysis.Algorithm.FAGEC;
import org.cytoscape.CytoCluster.internal.ClusterAnalysis.Algorithm.HCPIN;
import org.cytoscape.CytoCluster.internal.ClusterAnalysis.Algorithm.IPCA;
import org.cytoscape.CytoCluster.internal.ClusterAnalysis.Algorithm.MCODE;
import org.cytoscape.CytoCluster.internal.ClusterAnalysis.Algorithm.OHPIN;
import org.cytoscape.CytoCluster.internal.CommonUI.ResultPanel;
import org.cytoscape.CytoCluster.internal.MyUtils.ClusterUtil;
import org.cytoscape.CytoCluster.internal.MyUtils.CurrentParameters;
import org.cytoscape.CytoCluster.internal.MyUtils.ParameterSet;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.events.SetCurrentNetworkEvent;
import org.cytoscape.application.events.SetCurrentNetworkListener;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.application.swing.CytoPanel;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.application.swing.CytoPanelState;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.TaskManager;
import org.cytoscape.model.events.AddedEdgesEvent;
import org.cytoscape.model.events.AddedEdgesListener;
import org.cytoscape.model.events.AddedNodesEvent;
import org.cytoscape.model.events.AddedNodesListener;
import org.cytoscape.model.events.RemovedEdgesEvent;
import org.cytoscape.model.events.RemovedEdgesListener;
import org.cytoscape.model.events.RemovedNodesEvent;
import org.cytoscape.model.events.RemovedNodesListener;

/**
 * Class to handle the action of clicking Button Analyze.
 */
public class AnalyzeAction extends AbstractVizAction implements
		SetCurrentNetworkListener, AddedNodesListener, AddedEdgesListener,
		RemovedNodesListener, RemovedEdgesListener {

	private static final long serialVersionUID = -4326449698338258355L;
	final static int FIRST_TIME = 0;
	final static int RESCORE = 1;
	final static int REFIND = 2;
	final static int FIND = 3;
	final static int INTERRUPTED = 4;
	final static int FINDCLIQUE = 5;
	final static int CLIQUEBASED = 6;
	final static int EXISTS = 7;

	private HashMap networkManager;// Keeps track of netowrks (id is key) and
									// their algorithms
	private boolean resultFound;
	private ResultPanel resultPanel;
	int analyze = FIRST_TIME;
	int resultCounter = 0;
	ParameterSet curParams;

	// ClusterVisualStyle visualstyle;
	private String interruptedMessage = "";
	private int resultIndex;

	private final ClusterUtil clusterUtil;

	public static final int INTERRUPTION = 3;
	private final CyServiceRegistrar registrar;
	private final TaskManager taskManager;
	private Map<Long, Boolean> dirtyNetworks;

	public AnalyzeAction(String title, CyApplicationManager applicationManager,
			CySwingApplication swingApplication,
			CyNetworkViewManager netViewManager, CyServiceRegistrar registrar,
			TaskManager taskManager, ParameterSet curParams, ClusterUtil util) {
		super(title, applicationManager, swingApplication, netViewManager,
				"network");
		analyze = 0;
		this.registrar = registrar;
		this.taskManager = taskManager;
		this.curParams = curParams;
		this.clusterUtil = util;
		this.dirtyNetworks = new HashMap();
	}

	public void actionPerformed(ActionEvent event) {
		setResultFound(false);
		CurrentParameters resultParaSet = null;

		// get the network object, this contains the graph
		final CyNetwork network = applicationManager.getCurrentNetwork();
		final CyNetworkView networkView = this.applicationManager
				.getCurrentNetworkView();
		ParameterSet currentParamsCopy = getMainPanel().getCurrentParamsCopy();

		// pretreat the variable
		if (network == null) {
			JOptionPane.showMessageDialog(null, "Can't get a network.",
					"Error", JOptionPane.WARNING_MESSAGE);
			// System.err.println("Can't get a network.");
			return;
		}
		if (network.getNodeCount() < 1) {
			JOptionPane.showMessageDialog(null/* Cytoscape.getDesktop() */,
					"Network has not been loaded!", "Error",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// get selected node
		List<CyNode> nodes = network.getNodeList();
		Set<Long> selectedNodes = new HashSet<Long>();
		for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
			CyNode n = (CyNode) iterator.next();
			if (((Boolean) network.getRow(n).get("selected",
					java.lang.Boolean.class)).booleanValue())
				selectedNodes.add(n.getSUID());
		}

		// Set selectedNodes = network.getSelectedNodes();
		Long[] selectedNodesRGI = new Long[selectedNodes.size()];
		int c = 0;
		for (Iterator i = selectedNodes.iterator(); i.hasNext();) {
			Long nodeID = (Long) i.next();
			selectedNodesRGI[c++] = nodeID;
		}
		currentParamsCopy.setSelectedNodes(selectedNodesRGI);
		Algorithm alg1 = null;
		boolean newNet = isDirty(network);// judge new network
		/**********************************************************************************/
		analyze = checkParams(currentParamsCopy, newNet);
		/**********************************************************************************/
		if (analyze == INTERRUPTED || analyze == EXISTS)
			JOptionPane.showMessageDialog(
					AnalyzeAction.this.swingApplication.getJFrame(),
					interruptedMessage, "Interrupted",
					JOptionPane.WARNING_MESSAGE);
		else {
			// parallel edges
			ArrayList de = this.clusterUtil.detectparalleledges(network);
			if (de.size() != 0) {
				if (JOptionPane
						.showConfirmDialog(
								this.swingApplication.getJFrame(),
								"There are parallel edges or loops in the network! \n Do you still contine?",
								"WARNING", JOptionPane.YES_NO_OPTION) == 1) {
					this.clusterUtil.setSelected(de, network);
					return;
				} else
					this.clusterUtil.setSelected(de, network);

			}

			final int resultId = this.clusterUtil.getCurrentResultId();
			this.clusterUtil.getCurrentParameters().setParams(
					currentParamsCopy, resultId, network.getSUID());

			ParameterSet savedParamsCopy ;
			if (this.clusterUtil.containsNetworkAlgorithm(network.getSUID()
					.longValue())) {
				savedParamsCopy = this.clusterUtil.getCurrentParameters()
						.getParamsCopy(network.getSUID());
			} else {
				savedParamsCopy = this.clusterUtil.getCurrentParameters()
						.getParamsCopy(null);
				this.analyze = 0;
			}

			if (currentParamsCopy.getAlgorithm().equals(ParameterSet.MCODE))
				alg1 = new MCODE(null, this.clusterUtil);
			if (currentParamsCopy.getAlgorithm().equals(ParameterSet.EAGLE))
				alg1 = new EAGLE(null, this.clusterUtil);
			if (currentParamsCopy.getAlgorithm().equals(ParameterSet.FAGEC))
				alg1 = new FAGEC(null, this.clusterUtil);
			if (currentParamsCopy.getAlgorithm().equals(ParameterSet.HCPIN))
				alg1 = new HCPIN(null, this.clusterUtil);
			if (currentParamsCopy.getAlgorithm().equals(ParameterSet.OHPIN))
				alg1 = new OHPIN(null, this.clusterUtil);
			if (currentParamsCopy.getAlgorithm().equals(ParameterSet.IPCA))
				alg1 = new IPCA(null, this.clusterUtil);

			this.clusterUtil.addNetworkAlgorithm(network.getSUID().longValue(),
					alg1);

			/*---the analysis is successfully analyzed -------*/
			final Algorithm alg2 = alg1;
			AnalysisCompletedListener listener = new AnalysisCompletedListener() {
				public void handleEvent(AnalysisCompletedEvent e) {
					ResultPanel resultsPanel = null;
					boolean resultFound = false;
					AnalyzeAction.this.setDirty(network, false);
					if (e.isSuccessful()) {
						if ((e.getClusters() != null)
								&& (!e.getClusters().isEmpty())) {
							resultFound = true;
							AnalyzeAction.this.clusterUtil
									.addNetworkResult(network.getSUID()
											.longValue());
							DiscardResultAction discardResultAction = new DiscardResultAction(
									"Discard Result", resultId,
									AnalyzeAction.this.applicationManager,
									AnalyzeAction.this.swingApplication,
									AnalyzeAction.this.netViewManager,
									AnalyzeAction.this.registrar,
									AnalyzeAction.this.clusterUtil);

							resultsPanel = new ResultPanel(e.getClusters(),
									alg2, AnalyzeAction.this.clusterUtil,
									network, networkView, resultId,
									discardResultAction);

							AnalyzeAction.this.registrar.registerService(
									resultsPanel, CytoPanelComponent.class,
									new Properties());
						} else {
							JOptionPane
									.showMessageDialog(
											AnalyzeAction.this.swingApplication
													.getJFrame(),
											"No clusters were found.\nYou can try changing the parameters or\nmodifying your node selection if you are using\na selection-specific scope.",
											"No Results", 2);
						}
					}

					/**
					 * get the East CytoPanel
					 */
					CytoPanel cytoPanel = AnalyzeAction.this.swingApplication
							.getCytoPanel(CytoPanelName.EAST);

					if ((resultFound)
							|| ((AnalyzeAction.this.analyze == INTERRUPTED) && (cytoPanel
									.indexOfComponent(resultsPanel) >= 0))) {
						int index = cytoPanel.indexOfComponent(resultsPanel);
						cytoPanel.setSelectedIndex(index);

						if (cytoPanel.getState() == CytoPanelState.HIDE)
							cytoPanel.setState(CytoPanelState.DOCK);
					}
				}
			};

			AnalyzeTaskFactory analyzeTaskFactory = new AnalyzeTaskFactory(
					network, this.analyze, resultId, alg1, this.clusterUtil,
					listener);
			this.taskManager.execute(analyzeTaskFactory.createTaskIterator());
		}
	}

	/**
	 * check the values of the input parameters so as to take corresponding
	 * action
	 * 
	 * @param params
	 *            The set of input parameters
	 * @return the code of action to be taken
	 */
	private int checkParams(ParameterSet curParams, boolean newNet) {
		int analyze = -1;

		if (curParams.getScope().equals(ParameterSet.SELECTION)
				&& curParams.getSelectedNodes().length < 1) {
			analyze = INTERRUPTED;
			interruptedMessage = "At least one nodes should be selected";
		} else {
			String which = curParams.getAlgorithm();
			if (which.length() == 0) { // if no algorithm is selected
				analyze = INTERRUPTED;
				interruptedMessage = "An algorithm need to be selected for clustering";
			} else {
				if (!newNet) {
					// get list of copys of the saved parameters for comparison
					// with the current ones
					Collection clParaSets = this.clusterUtil
							.getCurrentParameters().getAllParamSets().values();
					Iterator it = clParaSets.iterator();
					ParameterSet curParaSet;
					while (it.hasNext()) {
						curParaSet = (ParameterSet) it.next();

						if (curParams.isWeakHCPIN() == true)
							System.out.println("curParams true");
						else
							System.out.println("curParams false");
						if (curParaSet.isWeakHCPIN() == true)
							System.out.println("curParaSet true");
						else
							System.out.println("curParaSet false");
						// System.out.println("curParams "+curParams.getCliqueSizeThresholdEAGLE());
						// System.out.println("curParaSet "+curParaSet.getCliqueSizeThresholdEAGLE());

						System.out.println("curParams "
								+ curParams.getNodeScoreCutoff());
						System.out.println("curParaSet "
								+ curParaSet.getNodeScoreCutoff());

						if (checkEqual(curParams, curParaSet) == true) { // exists
							System.out.println("%%%%%");
							CytoPanel panel = swingApplication
									.getCytoPanel(CytoPanelName.WEST.EAST);
							// CytoPanel panel =
							// Cytoscape.getDesktop().getCytoPanel(SwingConstants.EAST);
							setResultIndex(panel.indexOfComponent(resultPanel));
							analyze = EXISTS;
							interruptedMessage = "The result exits";
							System.out.println("#####");
							break;
						}
					}
				}
				if (newNet || analyze != EXISTS) {
					if (which.equals(ParameterSet.MCODE)) {
						analyze = FIND;
					}

					else if (which.equals(ParameterSet.EAGLE))
						analyze = CLIQUEBASED;

					else if (which.equals(ParameterSet.FAGEC)) {
						if (curParams.isOverlapped())
							analyze = FINDCLIQUE;
						else
							analyze = FIND;
					}

					else if (which.equals(ParameterSet.HCPIN)) {
						analyze = FIND;
					}

					else if (which.equals(ParameterSet.OHPIN)) {
						analyze = FIND;
					}

					else if (which.equals(ParameterSet.IPCA)) {
						analyze = FIND;
					}

				}
			}
		}
		return analyze;
	}

	private void setDirty(CyNetwork net, boolean dirty) {
		if (this.clusterUtil
				.containsNetworkAlgorithm(net.getSUID().longValue()))
			if (dirty)
				this.dirtyNetworks.put(net.getSUID(), Boolean.valueOf(dirty));
			else
				this.dirtyNetworks.remove(net.getSUID());
	}

	/**
	 * check if two sets of Parameter are equal or not
	 */
	private boolean checkEqual(ParameterSet PSa, ParameterSet PSb) {
		System.out.println("~~~~~~");
		// return false;
		if (!PSa.getAlgorithm().equals(PSb.getAlgorithm()))
			return false;
		if (!PSa.getScope().equals(PSb.getScope())
				|| (PSa.getScope().equals(ParameterSet.SELECTION) && PSa
						.getSelectedNodes() != PSb.getSelectedNodes()))
			return false;

		if (PSa.getAlgorithm().equals(ParameterSet.MCODE)) {
			if (PSa.isIncludeLoops() == PSb.isIncludeLoops()
					&& PSa.getDegreeCutoff() == PSb.getDegreeCutoff()
					&& PSa.getKCore() == PSb.getKCore()
					&& PSa.isHaircut() == PSb.isHaircut()
					&& PSa.getNodeScoreCutoff() == PSb.getNodeScoreCutoff()
					&& PSa.getMaxDepthFromStart() == PSb.getMaxDepthFromStart()
					&& PSa.isFluff() == PSb.isFluff()
					&& (!PSa.isFluff() || (PSa.isFluff() && PSa
							.getFluffNodeDensityCutoff() == PSb
							.getFluffNodeDensityCutoff())))
				return true;
		}
		if (PSa.getAlgorithm().equals(ParameterSet.FAGEC)) {
			if (PSa.isWeakFAGEC() == PSb.isWeakFAGEC()
					&& PSa.getfThresholdFAGEC() == PSb.getfThresholdFAGEC()
					&& PSa.getComplexSizeThresholdFAGEC() == PSb
							.getComplexSizeThresholdFAGEC()
					&& PSa.isOverlapped() == PSb.isOverlapped()
					&& PSa.getCliqueSizeThresholdFAGEC() == PSb
							.getCliqueSizeThresholdFAGEC())
				return true;
		}
		if (PSa.getAlgorithm().equals(ParameterSet.HCPIN)) {
			if (PSa.isWeakHCPIN() == PSb.isWeakHCPIN()
					&& PSa.getfThresholdHCPIN() == PSb.getfThresholdHCPIN()
					&& PSa.getComplexSizeThresholdHCPIN() == PSb
							.getComplexSizeThresholdHCPIN())
				return true;
		}
		if (PSa.getAlgorithm().equals(ParameterSet.OHPIN)) {
			if (PSa.getOverlappingScore() == PSb.getOverlappingScore()
					&& PSa.getfThresholdOHPIN() == PSb.getfThresholdOHPIN())
				return true;
		}
		if (PSa.getAlgorithm().equals(ParameterSet.IPCA)) {
			if (PSa.getShortestPathLength() == PSb.getShortestPathLength()
					&& PSa.getTinThreshold() == PSb.getTinThreshold()
					&& PSa.getComplexSizeThresholdHCPIN() == PSb
							.getComplexSizeThresholdHCPIN())
				return true;
		}
		if (PSa.getAlgorithm().equals(ParameterSet.EAGLE)) {
			if (PSa.getCliqueSizeThresholdEAGLE() == PSb
					.getCliqueSizeThresholdEAGLE()
					&& PSa.getComplexSizeThresholdEAGLE() == PSb
							.getComplexSizeThresholdEAGLE())
				return true;
		}
		System.out.println("@@@@@@");
		return false;
	}

	public void handleEvent(SetCurrentNetworkEvent e) {
		updateEnableState();
	}

	public void handleEvent(RemovedEdgesEvent e) {
		setDirty((CyNetwork) e.getSource(), true);
	}

	public void handleEvent(RemovedNodesEvent e) {
		setDirty((CyNetwork) e.getSource(), true);
	}

	public void handleEvent(AddedEdgesEvent e) {
		setDirty((CyNetwork) e.getSource(), true);
	}

	public void handleEvent(AddedNodesEvent e) {
		setDirty((CyNetwork) e.getSource(), true);
	}

	private boolean isDirty(CyNetwork net) {
		return Boolean.TRUE.equals(this.dirtyNetworks.get(net.getSUID()));
	}

	public void setResultIndex(int resultIndex) {
		this.resultIndex = resultIndex;
	}

	public int getResultIndex() {
		return resultIndex;
	}

	public void setResultFound(boolean resultFound) {
		this.resultFound = resultFound;
	}

	public boolean isResultFound() {
		return resultFound;
	}

	public void setNetworkManager(HashMap networkManager) {
		this.networkManager = networkManager;
	}

	public HashMap getNetworkManager() {
		return networkManager;
	}

	public String getTitle() {
		return "Network Cluster Detection";
	}
}
