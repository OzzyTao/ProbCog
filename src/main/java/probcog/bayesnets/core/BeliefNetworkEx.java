/*******************************************************************************
 * Copyright (C) 2006-2012 Dominik Jain.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.bayesnets.core;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import probcog.bayesnets.core.io.Converter_ergo;
import probcog.bayesnets.core.io.Converter_hugin;
import probcog.bayesnets.core.io.Converter_pmml;
import probcog.bayesnets.core.io.Converter_uai;
import probcog.bayesnets.core.io.Converter_xmlbif;
import probcog.bayesnets.inference.WeightedSample;

import edu.ksu.cis.bnj.ver3.core.BeliefNetwork;
import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.CPT;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.DiscreteEvidence;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.ksu.cis.bnj.ver3.inference.approximate.sampling.ForwardSampling;
import edu.ksu.cis.bnj.ver3.inference.exact.Pearl;
import edu.ksu.cis.bnj.ver3.plugin.IOPlugInLoader;
import edu.ksu.cis.bnj.ver3.streams.Exporter;
import edu.ksu.cis.bnj.ver3.streams.Importer;
import edu.ksu.cis.bnj.ver3.streams.OmniFormatV1_Reader;
import edu.ksu.cis.util.graph.algorithms.TopologicalSort;
import edu.ksu.cis.util.graph.core.Graph;
import edu.ksu.cis.util.graph.core.Vertex;

/**
 * An instance of class BeliefNetworkEx represents a full Bayesian Network.
 * It is a wrapper for BNJ's BeliefNetwork class with extended functionality.
 * BeliefNetwork could not simply be extended by inheritance because virtually all members are
 * declared private. Therefore, BeliefNetworkEx has a public member bn, which is an instance of
 * BeliefNetwork. 
 * 
 * @author Dominik Jain
 *
 */
public class BeliefNetworkEx {
	/*static final Logger logger = Logger.getLogger(BeliefNetworkEx.class);	
	static {
		logger.setLevel(Level.WARN);
	}*/
	
	static boolean defaultPluginsRegistered = false;

	/**
	 * The maximum number of unsuccessful trials for sampling. 
	 * TODO: This should perhaps depend on the number of samples to be gathered?
	 */
	public static final int MAX_TRIALS = 5000;

	/**
	 * the BNJ BeliefNetwork object that is wrapped by the instance of class BeliefNetworkEx.
	 * When using BNJ directly, you may need this; or you may want to use the methods of BeliefNetwork to perform
	 * an operation on the network that BeliefNetworkEx does not wrap.  
	 */
	public BeliefNetwork bn;
	
	/**
	 * the name of the currently loaded belief network file
	 */
	protected String filename;
	
	/**
	 * The mapping from attribute names to the node names of nodes that should get data from the attribute.
	 */
	protected Map<String, String> nodeNameToAttributeMapping = new HashMap<String, String>();
	
	/**
	 * The inverse mapping of {@link #nodeNameToAttributeMapping}.
	 */
	protected Map<String, Set<String>> attributeToNodeNameMapping = new HashMap<String, Set<String>>();
	
	/**
	 * constructs a BeliefNetworkEx object from a BNJ BeliefNetwork object
	 * @param bn	the BNJ BeliefNetwork object
	 */
	public BeliefNetworkEx(BeliefNetwork bn) {
		this.bn = bn;
		initAttributeMapping();
	}
	
	/**
	 * constructs a BeliefNetworkEx object from a saved network file
	 * @param filename	the name of the file to load the network from
	 * @throws Exception 
	 */
	public BeliefNetworkEx(String filename) throws Exception {
		initNetwork(filename);
	}
	
	/**
	 * constructs an empty network. Use methods addNode and connect to define the network structure.  
	 */ 
	public BeliefNetworkEx() {
		this.bn = new BeliefNetwork();
	}
	
	protected void initNetwork(String filename) throws Exception {
		this.filename = filename;
		this.bn = load(filename);
		initAttributeMapping();
	}
	
	/**
	 * Initialize the attribute mapping with the basenodes' names to itself respectively.
	 */
	protected void initAttributeMapping() {
	    for (BeliefNode node: bn.getNodes()) {
			addAttributeMapping(node.getName(), node.getName());
	    }
	}
	
	/**
	 * Add a link from the node name to the attribute name.
	 * Insert an entry into {@link #nodeNameToAttributeMapping} and into {@link #attributeToNodeNameMapping}.
	 * @param nodeName 	the name of the node to link.
	 * @param attributeName	the name of the attribute to be linked with the node.
	 */
	protected void addAttributeMapping(String nodeName, String attributeName) {
	    nodeNameToAttributeMapping.put(nodeName, attributeName);
	    Set<String> nodeNames = attributeToNodeNameMapping.get(attributeName);
	    if (nodeNames == null) {
	    	nodeNames = new HashSet<String>();
	    	attributeToNodeNameMapping.put(attributeName, nodeNames);
	    }
	    nodeNames.add(nodeName);
	}
	
	/**
	 * Get the attribute name that is linked to the given node.
	 * @param nodeName	the name of the node.
	 * @return		the attribute's name. 
	 */
	public String getAttributeNameForNode(String nodeName) {
	    return nodeNameToAttributeMapping.get(nodeName);
	}
	
	/**
	 * Get the node names that are linked to the given attribute name.
	 * @param attributeName	the attribute name the nodes are linked to.
	 * @return				the node names that are linked to the attribute.
	 */
	public Set<String> getNodeNamesForAttribute(String attributeName) {
		return attributeToNodeNameMapping.get(attributeName);
	}
	
	/**
	 * adds a node to the network
	 * @param node	the node that is to be added
	 */
	public void addNode(BeliefNode node) {
		bn.addBeliefNode(node);
		addAttributeMapping(node.getName(), node.getName());
	}
	
	/**
	 * adds a decision node (boolean) to the network 
	 * @param name	label of the node
	 */
	public BeliefNode addDecisionNode(String name) {
		BeliefNode node = new BeliefNode(name, new Discrete(new String[]{"True", "False"}));
		node.setType(BeliefNode.NODE_DECISION);
		bn.addBeliefNode(node);
		return node;
	}
	
	/**
	 * adds a node with the given name and the standard discrete domain {True, False} to the network
	 * @param name	the name of the node
	 * @return		a reference to the BeliefNode object that was constructed
	 */
	public BeliefNode addNode(String name) {
		return addNode(name, new Discrete(new String[]{"True", "False"}));
	}
	
	/**
	 * adds a node with the given name and domain to the network.
	 * Associate the attribute with the same name to the node.
	 * @param name		the name of the node
	 * @param domain	the node's domain (usually an instance of BNJ's class Discrete)
	 * @return			a reference to the BeliefNode object that was constructed
	 */
	public BeliefNode addNode(String name, Domain domain) {
		return addNode(name, domain, name);
	}
	
	/**
	 * adds a node with the given name and domain and attribute name to the network.
	 * @param name		the name of the node
	 * @param domain	the node's domain (usually an instance of BNJ's class Discrete)
	 * @param attributeName	the name of the attribute that is assigned to the node
	 * @return			a reference to the BeliefNode object that was constructed
	 */
	public BeliefNode addNode(String name, Domain domain, String attributeName) {
		return addNode(name, domain, attributeName, BeliefNode.NODE_CHANCE);
	}
	
	/**
	 * adds a node with the given name and domain and attribute name to the network.
	 * @param name		the name of the node
	 * @param domain	the node's domain (usually an instance of BNJ's class Discrete)
	 * @param attributeName	the type of the node (BeliefNode.NODE_CHANCE, BeliefNode.NODE_UTILITY or BeliefNode.NODE_DECISION)
	 * @return			a reference to the BeliefNode object that was constructed
	 */
	public BeliefNode addNode(String name, Domain domain, int type) {
		return addNode(name, domain, name, type);
	}
	
	/**
	 * adds a node with the given name and domain and attribute name to the network.
	 * @param name		the name of the node
	 * @param domain	the node's domain (usually an instance of BNJ's class Discrete)
	 * @param attributeName	the name of the attribute that is assigned to the node
	 * @param attributeName	the type of the node (BeliefNode.NODE_CHANCE, BeliefNode.NODE_UTILITY or BeliefNode.NODE_DECISION)
	 * @return			a reference to the BeliefNode object that was constructed
	 */
	public BeliefNode addNode(String name, Domain domain, String attributeName, int type) {
		BeliefNode node = new BeliefNode(name, domain);
		node.setType(type);
		bn.addBeliefNode(node);
		addAttributeMapping(name, attributeName);
		//logger.debug("Added node "+name+" with attributeName "+attributeName);
		return node;
	}
	
	/**
	 * adds an edge to the network, i.e. a dependency
	 * @param node1		the name of the node that influences another
	 * @param node2		the name of node that is influenced
	 * @throws Exception	if either of the node names are invalid
	 */
	public void connect(String node1, String node2) throws Exception {
		try {
			//logger.debug("connecting "+node1+" and "+node2);
			//logger.debug("Memory free: "+Runtime.getRuntime().freeMemory()+"/"+Runtime.getRuntime().totalMemory());
			BeliefNode n1 = getNode(node1);
			BeliefNode n2 = getNode(node2);
			if(n1 == null || n2 == null)
				throw new Exception("One of the node names "+node1+" or "+node2+" is invalid!");
			//logger.debug("Domainsize: "+n1.getDomain().getOrder()+"x"+n2.getDomain().getOrder());
			//logger.debug("Doing the connect...");
			bn.connect(n1, n2);
			//logger.debug("Memory free: "+Runtime.getRuntime().freeMemory()+"/"+Runtime.getRuntime().totalMemory());
			//logger.debug("Connection done.");
		} catch(Exception e) {
			System.out.println("Exception occurred in connect!");
			e.printStackTrace(System.out);
			throw e;
		} catch(Error e2) {
			System.out.println("Error occurred");
			e2.printStackTrace(System.out);
			throw e2;
		}
	}
	
	/** connect two nodes
	 * @param parent	parent which the bnode will be a child of
	 * @param child  	node which will be made a child of parent
	 * @param adjustCPF whether to adjust the CPF as well (otherwise only the graph is altered); should be set to false only if the CPF is manually initialized later on
	 */
	public void connect(BeliefNode parent, BeliefNode child, boolean adjustCPF) {
		Graph graph = bn.getGraph();
		graph.addDirectedEdge(parent.getOwner(), child.getOwner());
		if(adjustCPF) {
			Vertex[] parents = graph.getParents(child.getOwner());
			BeliefNode[] after = new BeliefNode[parents.length + 1];
			for (int i = 0; i < parents.length; i++)
			{
				after[i + 1] = ((BeliefNode) parents[i].getObject());
			}
			after[0] = child;
			CPT beforeCPF = (CPT)child.getCPF();
			child.setCPF(beforeCPF.expand(after));
		}
	}
	
	public void connect(BeliefNode parent, BeliefNode child) {
		connect(parent, child, true);
	}
	
	/**
	 * retrieves the node with the given name 
	 * @param name		the name of the node
	 * @return			a reference to the node (or null if there is no node with the given name)
	 */
	public BeliefNode getNode(String name) {
		int idx = getNodeIndex(name);
		if(idx == -1)
			return null;
		return bn.getNodes()[idx];
	}
	
	public BeliefNode getNode(int idx) {
		return bn.getNodes()[idx];
	}
	
	/**
	 * get the index (into the BeliefNetwork's array of nodes) of the node with the given name
	 * @param name	the name of the node
	 * @return		the index of the node (or -1 if there is no node with the given name)
	 */
	public int getNodeIndex(String name) {
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++)
			if(nodes[i].getName().equals(name))
				return i;
		return -1;		
	}
	
	/**
	 * Get the indices of the nodes that the CPT of the given node depends on.
	 * @param node	the node to take the CPT from. 
	 * @return		the indices of the nodes that the CPT of the given node depends on.
	 */
	public int[] getDomainProductNodeIndices(BeliefNode node) {
		BeliefNode[] nodes = node.getCPF().getDomainProduct();
		int[] nodeIndices = new int[nodes.length];
		for(int i = 0; i < nodes.length; i++)
			nodeIndices[i] = this.getNodeIndex(nodes[i].getName());
		return nodeIndices;
	}
	
	/**
	 * Get the indices into the domains of the nodes for the given node value assignments.
	 * @param nodeAndDomains	the assignments to be converted.
	 * @return					the assignment converted to doamin indices.
	 */
	public int[] getNodeDomainIndicesFromStrings(String[][] nodeAndDomains) {
		BeliefNode[] nodes = bn.getNodes(); 
		int[] nodeDomainIndices = new int[nodes.length];
		Arrays.fill(nodeDomainIndices, -1);
		for (String[] nodeAndDomain: nodeAndDomains) {
			if (nodeAndDomain == null || nodeAndDomain.length != 2)
				throw new IllegalArgumentException("Evidences not in the correct format: "+Arrays.toString(nodeAndDomain)+"!");
			int nodeIdx = getNodeIndex(nodeAndDomain[0]);
			if (nodeIdx < 0)
				throw new IllegalArgumentException("Variable with the name "+nodeAndDomain[0]+" not found!");
			/*if (nodeDomainIndices[nodeIdx] > 0)
				logger.warn("Evidence "+nodeAndDomain[0]+" set twice!");*/
			Discrete domain = (Discrete)nodes[nodeIdx].getDomain();
			int domainIdx = domain.findName(nodeAndDomain[1]);
			if (domainIdx < 0) {
				if (domain instanceof Discretized) {
					try {
						double value = Double.parseDouble(nodeAndDomain[1]);
						String domainStr = ((Discretized)domain).getNameFromContinuous(value);
						domainIdx = domain.findName(domainStr);
					} catch (Exception e) {
						throw new IllegalArgumentException("Cannot find evidence value "+nodeAndDomain[1]+" in domain "+domain+"!");
					}
				} else {
					throw new IllegalArgumentException("Cannot find evidence value "+nodeAndDomain[1]+" in domain "+domain+"!");
				}
			}
			nodeDomainIndices[nodeIdx]=domainIdx;
		}
		return nodeDomainIndices;
	}
	
	public int getNodeIndex(BeliefNode node) {
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++)
			if(nodes[i] == node)
				return i;
		return -1;
	}

	/**
	 * sets evidence for one of the network's node
	 * @param nodeName		the name of the node for which evidence is to be set
	 * @param outcome		the outcome, which must be in compliance with the node's domain
	 * @throws Exception	if the node name does not exist in the network or the outcome is not valid for the node's domain
	 */
	public void setEvidence(String nodeName, String outcome) throws Exception {
		BeliefNode node = getNode(nodeName);
		if(node == null)
			throw new Exception("Invalid node reference: " + nodeName);
		Discrete domain = (Discrete) node.getDomain();
		int idx = domain.findName(outcome);
		if(idx == -1)
			throw new Exception("Outcome " + outcome + " not in domain of " + nodeName);
		node.setEvidence(new DiscreteEvidence(idx));
	}
	
	/**
	 * calculates a probability Pr[X=x, Y=y, ... | E=e, F=f, ...]
	 * @param queries		an array of 2-element string arrays (variable, outcome)
	 * 						that represents the conjunction "X=x AND Y=y AND ...";
	 * @param evidences		the conjunction of evidences, specified in the same way
	 * @return				the calculated probability
	 * @throws Exception
	 */
	public double getProbability(String[][] queries, String[][] evidences) throws Exception {
		// queries with only one query variable (i.e. Pr[X | A,B,...]) can be solved directly
		// ... for others, recursion is necessary
		if(queries.length == 1) { 
			// remove any previous evidence
			BeliefNode[] nodes = bn.getNodes();			
			for(int i = 0; i < nodes.length; i++)
				nodes[i].setEvidence(null);
			// set new evidence
			if(evidences != null)
				for(int i = 0; i < evidences.length; i++) {
					setEvidence(evidences[i][0], evidences[i][1]);				
				}
			// run inference
			Pearl inf = new Pearl();		
			inf.run(this.bn);
			// return result
			BeliefNode node = getNode(queries[0][0]);
			CPF cpf = inf.queryMarginal(node);
			BeliefNode[] dp = cpf.getDomainProduct();
			boolean done = false;
			int[] addr = cpf.realaddr2addr(0);
			while(!done) {
				for (int i = 0; i < addr.length; i++)
					if(dp[i].getDomain().getName(addr[i]).equals(queries[0][1])) {
						ValueDouble v = (ValueDouble) cpf.get(addr);
						return v.getValue();						
					}
				done = cpf.addOne(addr);
			}
			throw new Exception("Outcome not in domain!");
			//inf.printResults();
		}
		else { // Pr[A,B,C,D | E] = Pr[A | B,C,D,E] * Pr[B,C,D | E]
			String[][] _queries = new String[1][2];
			String[][] _queries2 = new String[queries.length-1][2];
			_queries[0] = queries[0];
			int numEvidences = evidences == null ? 0 : evidences.length;
			String[][] _evidences = new String[numEvidences+queries.length-1][2];
			int idx = 0;
			for(int i = 1; i < queries.length; i++, idx++) {
				_evidences[idx] = queries[i];
				_queries2[idx] = queries[i];
			}
			for(int i = 0; i < numEvidences; i++, idx++)
				_evidences[idx] = evidences[i];
			return getProbability(_queries, _evidences) * getProbability(_queries2, evidences);			
		}
	}

	protected void printProbabilities(int node, Stack<String[]> evidence) throws Exception {
		BeliefNode[] nodes = bn.getNodes();
		if(node == nodes.length) {
			String[][] e = new String[evidence.size()][];
			evidence.toArray(e);
			double prob = getProbability(e, null);
			StringBuffer s = new StringBuffer();
			s.append(String.format("%6.2f%%  ", 100*prob));
			int i = 0;
			for(String[] pair : evidence) {
				if(i > 0)
					s.append(", ");
				s.append(String.format("%s=%s", pair[0], pair[1]));
				i++;
			}
			System.out.println(s);
			return;
		}
		Domain dom = nodes[node].getDomain();
		for(int i = 0; i < dom.getOrder(); i++) {
			evidence.push(new String[]{nodes[node].getName(), dom.getName(i)});
			printProbabilities(node+1, evidence);
			evidence.pop();
		}
	}
	
	public void printFullJoint() throws Exception {
		printProbabilities(0, new Stack<String[]>());
	}

	/**
	 * prints domain information for all nodes of the network to System.out
	 */
	public void printDomain() {
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			System.out.print(nodes[i].getName());
			Discrete domain = (Discrete)nodes[i].getDomain();
			System.out.print(" {");
			int c = domain.getOrder();
			for(int j = 0; j < c; j++) {
				if(j > 0) System.out.print(", ");
				System.out.print(domain.getName(j));
			}			
			System.out.println("}");
		}
	}
	
	/**
	 * static function for loading a Bayesian network into an instance of class BeliefNetwork
	 * @param filename					the file containing the network data	
	 * @param importer					an importer that is capable of understanding the file format
	 * @return							the loaded network in a new instance of class BeliefNetwork
	 * @throws FileNotFoundException
	 */
	public static BeliefNetwork load(String filename, Importer importer) throws FileNotFoundException {
		FileInputStream fis = new FileInputStream(filename);
		OmniFormatV1_Reader ofv1w = new OmniFormatV1_Reader();
		importer.load(fis, ofv1w);
		return ofv1w.GetBeliefNetwork(0);
	}
	
	/**
	 * loads a Bayesian network from the given file (determining a suitable importer from the extension) 
	 * @param filename
	 * @return
	 * @throws Exception 
	 */
	public static BeliefNetwork load(String filename) throws Exception {
		registerDefaultPlugins();
		IOPlugInLoader iopl = IOPlugInLoader.getInstance();
		String ext = iopl.GetExt(filename);
		Importer imp = iopl.GetImporterByExt(ext);
		if(imp == null) 
			throw new Exception("Unable to find an importer that can handle " + ext + " files.");
		return load(filename, imp);
	}
	
	/**
	 * saves a Bayesian network to the given filename (determining a suitable exporter from the extension) 
	 * @param filename
	 * @return
	 * @throws Exception 
	 */
	public static void save(BeliefNetwork net, String filename) throws Exception {
		registerDefaultPlugins();
		IOPlugInLoader iopl = IOPlugInLoader.getInstance();
		String ext = iopl.GetExt(filename);
		Exporter exporter = iopl.GetExportersByExt(ext);
		if(exporter == null) 
			throw new Exception("Unable to find an exporter that can handle " + ext + " files.");
		save(net, filename, exporter);
	}
	
	/**
	 * static function for writing a Bayesian network to a file using a given exporter
	 * @param net						the network to be written
	 * @param filename					the file to write to
	 * @param exporter					an exporter for the desired file format
	 * @throws FileNotFoundException
	 */
	public static void save(BeliefNetwork net, String filename, Exporter exporter) throws FileNotFoundException {
		exporter.save(net, new FileOutputStream(filename));
		//OmniFormatV1_Writer.Write(net, (OmniFormatV1)exporter);
	}
	
	/**
	 * saves a Bayesian network to the given filename (determining a suitable exporter from the extension) 
	 * @param filename
	 * @return
	 * @throws Exception 
	 */
	public void save(String filename) throws Exception {
		save(this.bn, filename);
	}
	
	/**
	 * writes the Bayesian network to a file with the given name using an exporter
	 * @param filename					the file to write to
	 * @param exporter					an exporter for the desired file format
	 * @throws FileNotFoundException
	 */
	public void save(String filename, Exporter exporter) throws FileNotFoundException {
		save(this.bn, filename, exporter);
	}

	/**
	 * writes the Bayesian network to a file with the given name in XML-BIF format
	 * @param filename					the file to write to
	 * @throws FileNotFoundException
	 */
	public void saveXMLBIF(String filename) throws FileNotFoundException {
		save(filename, new Converter_xmlbif());
	}
	
	/**
	 * writes the Bayesian network to a file with the given name in a PMML-based format
	 * @param filename					the file to write to
	 * @throws FileNotFoundException
	 */
	public void savePMML(String filename) throws FileNotFoundException {
		save(filename, new Converter_pmml());
	}
	
	/**
	 * writes the Bayesian network to the same file it was loaded from
	 * @throws Exception 
	 *
	 */
	public void save() throws Exception {
		IOPlugInLoader pil = IOPlugInLoader.getInstance();
		if(filename == null)
			throw new Exception("Cannot save - filename not given!");
		Exporter exporter = pil.GetExportersByExt(pil.GetExt(filename));
		save(filename, exporter);
	}
	
	/**
	 * sorts the domain of the node with the given name alphabetically (if numeric is false) or
	 * numerically (if numeric is true) - in ascending order
	 * @param nodeName		the name of the node whose domain is to be sorted
	 * @param numeric		whether to sort numerically or not. If numeric is true,
	 * 						all domain values are converted to double for sorting.
	 * 						If numeric is false, the values are simply sorted alphabetically. 
	 * @throws Exception	if the node name is invalid
	 */
	public void sortNodeDomain(String nodeName, boolean numeric) throws Exception {
		BeliefNode node = getNode(nodeName);
		if(node == null)
			throw new Exception("Node not found");
		Discrete domain = (Discrete)node.getDomain();
		int ord = domain.getOrder();
		String[] strings = new String[ord];
		if(!numeric) {			
			for(int i = 0; i < ord; i++)
				strings[i] = domain.getName(i);
			Arrays.sort(strings);			
		}
		else {
			double[] values = new double[ord];
			for(int i = 0; i < ord; i++)
				values[i] = Double.parseDouble(domain.getName(i));
			double[] sorted_values = values.clone();			
			Arrays.sort(sorted_values);			
			for(int i = 0; i < ord; i++)
				for(int j = 0; j < ord; j++)
					if(sorted_values[i] == values[j])
						strings[i] = domain.getName(j);			
		}
		bn.changeBeliefNodeDomain(node, new Discrete(strings));
	}
	
	/**
	 * returns the domain of the node with the given name
	 * @param nodeName	the name of the node for which the domain is to be returned
	 * @return			the domain of the node (usually instance of class Discrete)
	 * 					or null if the node name is invalid
	 */
	public Domain getDomain(String nodeName) {
		BeliefNode node = getNode(nodeName);
		if(node == null)
			return null;
		return node.getDomain();
	}
	
	/**
	 * shows the Bayesian Network in an editor window (with support for standard IO plugins)
	 */
	public void show() {
		registerDefaultPlugins();		
		edu.ksu.cis.bnj.gui.GUIWindow window = new edu.ksu.cis.bnj.gui.GUIWindow();
		window.register();	
		window.open(bn, filename);
	}
	
	public static void registerDefaultPlugins() {
		if(defaultPluginsRegistered)
			return;
		IOPlugInLoader iopl = IOPlugInLoader.getInstance();
		// XML-BIF
		Converter_xmlbif xmlbif = new Converter_xmlbif();
		iopl.addPlugin(xmlbif, xmlbif);
		// PMML
		Converter_pmml pmml = new Converter_pmml();
		iopl.addPlugin(pmml, pmml);
		// Hugin
		Converter_hugin hugin = new Converter_hugin();
		iopl.addPlugin(null, hugin);
		// Ergo
		Converter_ergo ergo = new Converter_ergo();
		iopl.addPlugin(ergo, ergo);
		// UAI
		Converter_uai uai = new Converter_uai();
		iopl.addPlugin(null, uai);
		defaultPluginsRegistered = true;
	}
	
	/**
	 * shows the Bayesian Network in a BNJ editor window,
	 * loading the BNJ plugins in the given directory  
	 * @param pluginDir		a directory containing BNJ plugins (jar files)
	 */
	public void show(String pluginDir) {
		IOPlugInLoader iopl = IOPlugInLoader.getInstance();
		iopl.loadPlugins(pluginDir);
		show();
	}
	
	/**
	 * helper function for queryShell that reads a list of comma-separated assignments "A=a,B=b,..."
	 * into an array [["A","a"],["B","b"],...]
	 * @param list
	 * @return
	 * @throws java.lang.Exception
	 */
	protected static String[][] readList(String list) throws java.lang.Exception {
		if(list == null)
			return null;
		String[] items = list.split(",");
		String[][] res = new String[items.length][2];
		for(int i = 0; i < items.length; i++) {
			res[i] = items[i].split("=");
			if(res[i].length != 2)
				throw new java.lang.Exception("syntax error!");
		}
		return res;
	}

	/**
	 * starts a shell that allows the user to query the network
	 */
	public void queryShell() {
		// output some usage information
		System.out.println("Domain:");
		printDomain();
		System.out.println("\nUsage: Pr[X=x, Y=y, ... | E=e, F=f, ...]   (X,Y: query vars;\n" +
						     "                                            E,F: evidence vars;\n" +
						     "                                            x,y,e,f: outcomes\n" + 
				             "       exit                                (close shell)");
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in)); 
	    for(;;) {	
			try {
				// get input query from stdin 
				System.out.print("\n> ");			
			    String input = br.readLine(); 
				
				if(input.equalsIgnoreCase("exit"))
					break;
			
				// parse expression...
				input = input.replaceAll("\\s+", "");
				Pattern p = Pattern.compile("Pr\\[([^\\]\\|]*)(?:\\|([^\\]]*))?\\]");		
				Matcher m = p.matcher(input);
				if(!m.matches()) {
					System.out.println("syntax error!");
				}
				else {
					String[][] queries = readList(m.group(1));
					String[][] evidences = readList(m.group(2));
					try {
						// evaluate and output result...
						double result = getProbability(queries, evidences);
						System.out.println(result);
					}
					catch(Exception e) {
						System.out.println(e.getMessage());
					}
				}
			}
			catch(IOException e) {
				System.err.println(e.getMessage());
			}
			catch(java.lang.Exception e) {
				System.out.println(e.getMessage());
			}
	    }
	}
	
	/**
	 * Get the sample assignment and its sampled probability as the weight sorted by probability.
	 * @param evidences		the evidences for the distribution.
	 * @param queryNodes	the nodes that should be sampled.
	 * @param numSamples	the number of samples to draw from.
	 * @return				the accumulated samples and their sampled conditional probabilities given the evidences 
	 * 						or null	if we run out of trials for the first sample. 
	 * @throws Exception 
	 */
	public WeightedSample[] getAssignmentDistribution(String[][] evidences, String[] queryNodeNames, int numSamples) throws Exception {
		HashMap<WeightedSample, Double> sampleSums = new HashMap<WeightedSample, Double>();

		int[] queryNodes = new int[queryNodeNames.length];
		for (int i=0; i<queryNodeNames.length; i++) {
			queryNodes[i]=getNodeIndex(queryNodeNames[i]);
			if (queryNodes[i] < 0)
				throw new IllegalArgumentException("Cannot find node with name "+queryNodeNames[i]);
		}

		Random generator = new Random();
		for (int i=0; i<numSamples; i++) {
			WeightedSample sample = getWeightedSample(evidences, generator);
			if (sample == null && i == 0)	// If we need too many trials and we have no samples 
				return null;				// it will be very probable that we have an endless loop because of a bad evidence!
			WeightedSample subSample = sample.subSample(queryNodes);

			if (sampleSums.containsKey(subSample)) {
				sampleSums.put(subSample, sampleSums.get(subSample)+subSample.weight);
			} else {
				sampleSums.put(subSample, subSample.weight);
			}
		}

		double sum = 0;
		for (WeightedSample sample: sampleSums.keySet()) {
			//logger.debug(sample);
			double value = sampleSums.get(sample);
			sum += value;
		}
		WeightedSample[] samples = sampleSums.keySet().toArray(new WeightedSample[0]);
		for (WeightedSample sample: samples) {
			sample.weight = sampleSums.get(sample)/sum;
		}
		
		Arrays.sort(samples, new Comparator<WeightedSample>() {
			public int compare(WeightedSample o1, WeightedSample o2) {
				return Double.compare(o2.weight, o1.weight);
			}
		});

		return samples;
	}
	
	/**
	 * gets a topological ordering of the network's nodes  
	 * @return an array of integers containing node indices
	 */
	public int[] getTopologicalOrder() {
		TopologicalSort topsort = new TopologicalSort();
		topsort.execute(bn.getGraph());
		return topsort.alpha;
	}
	
	/**
	 * Get a specific entry in the cpt of the given node.
	 * The nodeDomainIndices should contain a value for each node in the BeliefNet but only values
	 * in the domain product of the node are queried for.
	 * WARNING: This is very slow (mainly because getDomainProductNodeIndices performs a linear search for each node)
	 * @param node				the node the CPT should come from.
	 * @param nodeDomainIndices	the values the nodes should have (domain indices for all the nodes in the network)
	 * @return					the probability entry in the CPT.
	 */
	public double getCPTProbability(BeliefNode node, int[] nodeDomainIndices ) {
		CPF cpf = node.getCPF();
		int[] domainProduct = getDomainProductNodeIndices(node);
		int[] address = new int[domainProduct.length];
		for (int i=0; i<address.length; i++) {
			address[i]=nodeDomainIndices[domainProduct[i]];
		}
		int realAddress = cpf.addr2realaddr(address);
		return cpf.getDouble(realAddress);
	}
	
	/**
	 * Remove all evidences.
	 */
	public void removeAllEvidences() {
		// remove evidences (restoring original state)
		for(BeliefNode node : bn.getNodes()) {
			node.setEvidence(null);
		}
	}
	
	/**
	 * Calculates a probability Pr[X=x, Y=y, ... | E=e, F=f, ...] by sampling a number of samples.
	 * @param queries		an array of 2-element string arrays (variable, outcome)
	 * 						that represents the conjunction "X=x AND Y=y AND ...".
	 * @param evidences		the conjunction of evidences, specified in the same way.
	 * @param numSamples	the number of samples to draw.
	 * @return				the calculated probability.
	 * @throws Exception 
	 */
	public double getSampledProbability(String[][] queries, String[][] evidences, int numSamples) throws Exception {
	    String[] queryNodes = new String[queries.length];
	    for (int i=0; i<queryNodes.length; i++) {
	    	queryNodes[i]=queries[i][0];
	    }
	    WeightedSample[] samples = getAssignmentDistribution(evidences, queryNodes, numSamples);
	    double goodSum = 0;
	    double allSum = 0;
	    for (int i=0; i<samples.length; i++) {
			allSum += samples[i].weight;
			if (samples[i].checkAssignment(queries))
			    goodSum += samples[i].weight;
	    }
	    return goodSum/allSum;
	}
	
	/**
	 * Sample from the BeliefNet via likelihood weighted sampling.
	 * @param evidences				the evidences for the sample.
	 * @param sampleDomainIndexes	the resulting domain indexes for each node.
	 * 			The length must be initialized to the number of nodes in the net.
	 * @return
	 * @throws Exception 
	 */
	public WeightedSample getWeightedSample(String[][] evidences, Random generator) throws Exception {		
		if (generator == null) {
			generator = new Random();
		}		
		return getWeightedSample(getTopologicalOrder(), evidence2DomainIndices(evidences), generator);
	}
	
	public WeightedSample getWeightedSample(int[] nodeOrder, int[] evidenceDomainIndices, Random generator) throws Exception {
		BeliefNode[] nodes = bn.getNodes();
		int[] sampleDomainIndices  = new int[nodes.length];
		boolean successful = false;
		double weight = 1.0;
		int trials=0;
success:while (!successful) {
			//System.out.println(trials);
			weight = 1.0;
			if (trials > MAX_TRIALS)
				return null;
			for (int i=0; i< nodeOrder.length; i++) {
				int nodeIdx = nodeOrder[i];
				int domainIdx = evidenceDomainIndices[nodeIdx];
				if (domainIdx >= 0) { // This is an evidence node?
					sampleDomainIndices[nodeIdx] = domainIdx;
					nodes[nodeIdx].setEvidence(new DiscreteEvidence(domainIdx));
					// TODO this call is inefficient
					double prob = getCPTProbability(nodes[nodeIdx], sampleDomainIndices);
					if (prob == 0.0) {
						//System.out.println("sampling failed at evidence node " + nodes[nodeIdx].getName());
						removeAllEvidences();
						trials++;
						continue success;
					}
					weight *= prob;
				} else {
					domainIdx = ForwardSampling.sampleForward(nodes[nodeIdx], bn, generator);
					if (domainIdx < 0) {
						System.out.println("could not sample forward because of column with 0s in CPT of " + nodes[nodeIdx].getName());
						removeAllEvidences();
						trials++;
						continue success;
					}
					sampleDomainIndices[nodeIdx] = domainIdx;
					nodes[nodeIdx].setEvidence(new DiscreteEvidence(domainIdx));
				}
			}
			trials++;
			removeAllEvidences();
			successful = true;
		}
		return new WeightedSample(this, sampleDomainIndices, weight, null, trials);		
	}
	
	public int[] evidence2DomainIndices(String[][] evidences) {
		BeliefNode[] nodes = bn.getNodes();
		int[] evidenceDomainIndices = new int[nodes.length];
		Arrays.fill(evidenceDomainIndices, -1);
		for (String[] evidence: evidences) {
			if (evidence == null || evidence.length != 2)
				throw new IllegalArgumentException("Evidences not in the correct format: "+Arrays.toString(evidence)+"!");
			int nodeIdx = getNodeIndex(evidence[0]);
			if (nodeIdx < 0) {
				String error = "Variable with the name "+evidence[0]+" not found in model but mentioned in evidence!";
				System.err.println("Warning: " + error);
				continue;
				//throw new IllegalArgumentException(error);
			}
			/*if (evidenceDomainIndices[nodeIdx] > 0)
				logger.warn("Evidence "+evidence[0]+" set twice!");*/
			Discrete domain = (Discrete)nodes[nodeIdx].getDomain();
			int domainIdx = domain.findName(evidence[1]);
			if (domainIdx < 0) {
				if (domain instanceof Discretized) {
					try {
						double value = Double.parseDouble(evidence[1]);
						String domainStr = ((Discretized)domain).getNameFromContinuous(value);
						domainIdx = domain.findName(domainStr);
					} catch (Exception e) {
						throw new IllegalArgumentException("Cannot find evidence value "+evidence[1]+" in domain "+domain+"!");
					}
				} 
				else {
					throw new IllegalArgumentException("Cannot find evidence value "+evidence[1]+" in domain "+domain+" of node " + nodes[nodeIdx].getName());
				}
			}
			evidenceDomainIndices[nodeIdx]=domainIdx;
		}
		return evidenceDomainIndices;
	}

	/**
	 * performs sampling on the network and returns a sample of the marginal distribution represented by this Bayesian network; evidences that are set during sampling are removed
	 * afterwards in order to retain the original state of the network.
	 * @return a hashmap of (node name, string value) pairs representing the sample
	 * @param generator random number generator to use to generate sample (null to create one) 
	 * @throws Exception
	 */
	public HashMap<String,String> getSample(Random generator) throws Exception {
		if(generator == null)
			generator = new Random();
		HashMap<String,String> ret = new HashMap<String,String>();
		// perform topological sort to determine sampling order
		TopologicalSort topsort = new TopologicalSort();
		topsort.execute(bn.getGraph());
		int[] order = topsort.alpha;
		// sample
		BeliefNode[] nodes = bn.getNodes();
		boolean succeeded = false;
		while(!succeeded) {
			ArrayList<BeliefNode> setEvidences = new ArrayList<BeliefNode>(); // remember nodes for which we set evidences while sampling
			for(int i = 0; i < order.length; i++) {
				BeliefNode node = nodes[order[i]];
				if(node.hasEvidence()) {
					throw new Exception("At least one node has evidence. You can only sample from the marginal distribution!");
				}
				int idxValue = ForwardSampling.sampleForward(node, bn, generator);			
				if(idxValue == -1) {
					// sampling node failed - most probably because the distribution was all 0 values -> retry from start
					succeeded = false;
					break;
				}
				succeeded = true;
				Domain dom = node.getDomain();
				//System.out.println("set node " + node.getName() + " to " + dom.getName(idxValue));
				ret.put(node.getName(), dom.getName(idxValue));
				node.setEvidence(new DiscreteEvidence(idxValue));
				setEvidences.add(node);				
			}
			// remove evidences (restoring original state)
			for(BeliefNode node : setEvidences) {
				node.setEvidence(null);
			}
		}
		return ret;
	}
	
	public static String[] getDiscreteDomainAsArray(BeliefNode node) {
		Discrete domain = (Discrete)node.getDomain();
		String[] ret = new String[domain.getOrder()];
		for(int i = 0; i < ret.length; i++)
			ret[i] = domain.getName(i);
		return ret;		
	}
	
	public String[] getDiscreteDomainAsArray(String nodeName) {
		return getDiscreteDomainAsArray(getNode(nodeName));
	}

	/*
	public void dump() {
		BeliefNode[] nodes = bn.getNodes();
		for (int i=0; i<nodes.length; i++) {
			logger.debug("Node "+i+": "+nodes[i].getName());
			logger.debug("\tAttribute: "+getAttributeNameForNode(nodes[i].getName()));
		}
		for (String attributeName: attributeToNodeNameMapping.keySet()) {
			logger.debug("Attribute "+attributeName+": "+attributeToNodeNameMapping.get(attributeName));
		}
	}
	*/

	public interface CPTWalker {
		public abstract void tellSize(int childConfigs, int parentConfigs);
		public abstract void tellNode(BeliefNode n);		
		public abstract void tellValue(int[] addr, double v);
	}
	
	/**
	 * @param node the node whose CPT to walk
	 * @param walker the visitor
	 * @param byColumn whether to walk the CPT by column rather than by row
	 */
	public void walkCPT(BeliefNode node, CPTWalker walker, boolean byColumn) {
		CPF cpf = node.getCPF();
		BeliefNode[] nodes = cpf.getDomainProduct();
		int parentConfigs = 1;
		for(int i = 1; i < nodes.length; i++)
			parentConfigs *= nodes[i].getDomain().getOrder();
		walker.tellSize(nodes[0].getDomain().getOrder(), parentConfigs);
		int[] addr = new int[cpf.getDomainProduct().length];
		walker.tellNode(node);
		walkCPT(walker, cpf, addr, byColumn ? 1 : 0, byColumn);
	}
	
	protected void walkCPT(CPTWalker walker, CPF cpf, int[] addr, int i, boolean byColumn) {
		BeliefNode[] nodes = cpf.getDomainProduct();
		boolean done = !byColumn ? i == addr.length : i == addr.length+1; 
		if(done) { // we have a complete address of all parents
			// get the probability value
			int realAddr = cpf.addr2realaddr(addr);
			double value = ((ValueDouble)cpf.get(realAddr)).getValue();
			walker.tellValue(addr, value);
		}
		else { // the address is yet incomplete -> consider all ways of setting the next e
			int idx = i % addr.length;
			Discrete dom = (Discrete)nodes[idx].getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[idx] = j;
				walkCPT(walker, cpf, addr, i+1, byColumn);
			}
		}
	}
	
	/**
	 * gets the index of the given value inside the given node's domain
	 * @param node  a node with a discrete domain
	 * @param value  the value whose index to search for
	 * @return  the index of the value in the node's domain
	 */
	public int getDomainIndex(BeliefNode node, String value) {
		Discrete domain = (Discrete)node.getDomain();
		return domain.findName(value);
	}

	/**
	 * computes the prior distribution of each node
	 * @param evidenceDomainIndices may be null, otherwise evidence to be "faked in" (domain index for each of the nodes, -1 for no evidence). The prior of an evidence node is then calculated as 1 and nodes lower in the topology will consider the evidence in their priors.
	 * @return
	 */
	public HashMap<BeliefNode, double[]> computePriors(int[] evidenceDomainIndices) {
		HashMap<BeliefNode, double[]> priors = new HashMap<BeliefNode, double[]>();
		BeliefNode[] nodes = bn.getNodes();
		int[] topOrder = getTopologicalOrder();
		for(int i : topOrder) {
			BeliefNode node = nodes[i];
			double[] dist = new double[node.getDomain().getOrder()];
			int evidence = evidenceDomainIndices != null ? evidenceDomainIndices[i] : -1;
			if(evidence >= 0) {
				for(int j = 0; j < dist.length; j++)
					dist[j] = evidence == j ? 1.0 : 0.0;
			}
			else {
				CPF cpf = node.getCPF();
				computePrior(priors, evidenceDomainIndices, cpf, 0, new int[cpf.getDomainProduct().length], dist);
			}
			priors.put(node, dist);
		}
		return priors;
	}
	
	protected void computePrior(HashMap<BeliefNode, double[]> priors, int[] evidenceDomainIndices, CPF cpf, int i, int[] addr, double[] dist) {
		BeliefNode[] domProd = cpf.getDomainProduct(); 
		if(i == addr.length) {
			double p = cpf.getDouble(addr); // p = P(node setting | parent configuration)
			for(int j = 1; j < addr.length; j++) {
				double[] parentPrior = priors.get(domProd[j]);
				p *= parentPrior[addr[j]]; 
			} // p = P(node setting, parent configuration)
			dist[addr[0]] += p;
			return;
		}
		BeliefNode node = domProd[i];
		int nodeIdx = getNodeIndex(node);
		if(evidenceDomainIndices[nodeIdx] >= 0) {
			addr[i] = evidenceDomainIndices[nodeIdx];
			computePrior(priors, evidenceDomainIndices, cpf, i+1, addr, dist);
		}
		else {
			Domain dom = node.getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[i] = j;
				computePrior(priors, evidenceDomainIndices, cpf, i+1, addr, dist);
			}
		}
	}
	
	/**
	 * gets the probability of the possible world given by the vector of domain indices
	 * @param nodeDomainIndices domain indices for each of the node's random variables
	 * @return
	 */
	public double getWorldProbability(int[] nodeDomainIndices) {
		BeliefNode[] nodes = bn.getNodes();
		double ret = 1.0;
		for(int i = 0; i < nodes.length; i++)
			ret *= getCPTProbability(nodes[i], nodeDomainIndices);
		return ret;
	}
	
	public BeliefNode[] getNodes() {
		return bn.getNodes();
	}
	
	/**
	 * gets the total number of possible worlds
	 */
	public double getNumWorlds() {
		double num = 1;
		for(BeliefNode n : getNodes())
			num *= n.getDomain().getOrder();
		return num;
	}
}
