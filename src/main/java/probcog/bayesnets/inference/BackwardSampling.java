/*******************************************************************************
 * Copyright (C) 2008-2012 Dominik Jain.
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
package probcog.bayesnets.inference;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Vector;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.bayesnets.util.TopologicalOrdering;
import probcog.bayesnets.util.TopologicalSort;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.util.Stopwatch;
import edu.tum.cs.util.StringTool;

/**
 * an implementation of the backward simulation algorithm as described by Robert Fung and Brendan Del Favero 
 * in "Backward Simulation in Bayesian Networks" (UAI 1994)
 * 
 * @author Dominik Jain
 */
public class BackwardSampling extends Sampler {

	protected Vector<BeliefNode> backwardSampledNodes;
	protected Vector<BeliefNode> forwardSampledNodes;
	protected HashSet<BeliefNode> outsideSamplingOrder;
	protected int currentStep;
	
	public static class BackSamplingDistribution {
		public Vector<Double> distribution;
		public Vector<int[]> states;
		double Z;
		protected Sampler sampler;
		
		public BackSamplingDistribution(Sampler sampler) {
			Z = 0.0;
			distribution = new Vector<Double>();
			states = new Vector<int[]>();
			this.sampler = sampler;
		}
		
		public void addValue(double p, int[] state) {
			distribution.add(p);
			states.add(state);
			Z += p;
		}
		
		public double getWeightingFactor(int sampledValue) {
			return Z;
		}
		
		public void applyWeight(WeightedSample s, int sampledValue) {
			s.weight *= getWeightingFactor(sampledValue);
		}
		
		public void construct(BeliefNode node, int[] nodeDomainIndices) {
			CPF cpf = node.getCPF();
			BeliefNode[] domProd = cpf.getDomainProduct();
			int[] addr = new int[domProd.length];
			addr[0] = nodeDomainIndices[sampler.nodeIndices.get(node)];
			construct(1, addr, cpf, nodeDomainIndices);
		}
		
		/**
		 * recursively constructs the distribution to backward sample from  
		 * @param i			the node to instantiate next (as an index into the CPF's domain product)
		 * @param addr		the current setting of node indices of the CPF's domain product
		 * @param cpf		the conditional probability function of the node we are backward sampling
		 * @param d			the distribution to fill
		 */
		protected void construct(int i, int[] addr, CPF cpf, int[] nodeDomainIndices) {
			if(i == addr.length) {
				double p = cpf.getDouble(addr);
				if(p != 0)
					addValue(p, addr.clone());
				return;
			}
			BeliefNode[] domProd = cpf.getDomainProduct();
			int nodeIdx = sampler.nodeIndices.get(domProd[i]);
			if(nodeDomainIndices[nodeIdx] >= 0) {
				addr[i] = nodeDomainIndices[nodeIdx];
				construct(i+1, addr, cpf, nodeDomainIndices);
			}
			else {
				Discrete dom = (Discrete)domProd[i].getDomain();		
				for(int j = 0; j < dom.getOrder(); j++) {
					addr[i] = j;
					construct(i+1, addr, cpf, nodeDomainIndices);
				}
			}
		}
	}
	
	public BackwardSampling(BeliefNetworkEx bn) throws Exception {
		super(bn);
	}
	
	/**
	 * for ordering belief nodes in descending order of the tier they are in (as indicated by the topological ordering)
	 * @author Dominik Jain
	 *
	 */
	public static class TierComparator implements Comparator<BeliefNode> {

		TopologicalOrdering topOrder;
		
		public TierComparator(TopologicalOrdering topOrder) {
			this.topOrder = topOrder;
		}
		
		public int compare(BeliefNode o1, BeliefNode o2) {
			return -(topOrder.getTier(o1) - topOrder.getTier(o2));			
		}		
	}
	
	/**
	 * gets the sampling order by filling the members for backward and forward sampled nodes as well as the set of nodes not in the sampling order
	 * @param evidenceDomainIndices
	 * @throws Exception 
	 */
	protected void getOrdering(int[] evidenceDomainIndices) throws Exception {
		HashSet<BeliefNode> uninstantiatedNodes = new HashSet<BeliefNode>(Arrays.asList(nodes));
		backwardSampledNodes = new Vector<BeliefNode>();
		forwardSampledNodes = new Vector<BeliefNode>();
		outsideSamplingOrder = new HashSet<BeliefNode>();
		TopologicalOrdering topOrder = new TopologicalSort(bn.bn).run(true);
		PriorityQueue<BeliefNode> backSamplingCandidates = new PriorityQueue<BeliefNode>(1, new TierComparator(topOrder));

		// check which nodes have evidence; ones that are are candidates for backward sampling and are instantiated
		for(int i = 0; i < evidenceDomainIndices.length; i++) {
			if(evidenceDomainIndices[i] >= 0) { 
				backSamplingCandidates.add(nodes[i]);
				uninstantiatedNodes.remove(nodes[i]);
			}
		}
		
		// check all backward sampling candidates
		while(!backSamplingCandidates.isEmpty()) {
			BeliefNode node = backSamplingCandidates.remove();
			// check if there are any uninstantiated parents
			BeliefNode[] domProd = node.getCPF().getDomainProduct();
			boolean doBackSampling = false;
			for(int j = 1; j < domProd.length; j++) {
				BeliefNode parent = domProd[j];
				// if there are uninstantiated parents, we do backward sampling on the child node
				if(uninstantiatedNodes.remove(parent)) { 
					doBackSampling = true;
					backSamplingCandidates.add(parent);
				}					
			}
			if(doBackSampling)
				backwardSampledNodes.add(node);
			// if there are no uninstantiated parents, the node is not backward sampled but is instantiated,
			// i.e. it is not in the sampling order
			else
				outsideSamplingOrder.add(node);
		}
		
		// schedule all uninstantiated node for forward sampling in the topological order
		for(int i : topOrder) {
			if(uninstantiatedNodes.contains(nodes[i]))
				forwardSampledNodes.add(nodes[i]);
		}
	}
	
	/**
	 * samples backward from the given node, instantiating its parents
	 * @param node	
	 * @param s		the sample to store the instantiation information in; the weight is also updated with the normalizing constant that is obtained
	 * @return true if sampling succeeded, false otherwise
	 */
	protected boolean sampleBackward(BeliefNode node, WeightedSample s) {		
		//out.println("backward sampling from " + node);
		// get the distribution from which to sample 		
		BackSamplingDistribution d = getBackSamplingDistribution(node, s);
		// sample
		int idx = sample(d.distribution, generator);	
		if(idx == -1)
			return false;
		int[] state = d.states.get(idx);
		// apply weight
		d.applyWeight(s, idx);
		if(s.weight == 0.0)
			return false;
		// apply sampled parent setting
		BeliefNode[] domProd = node.getCPF().getDomainProduct();
		for(int i = 1; i < state.length; i++) {
			int nodeIdx = this.nodeIndices.get(domProd[i]);
			s.nodeDomainIndices[nodeIdx] = state[i];
			//out.println("  sampled node " + domProd[i]);
		}
		return true;
	}
	
	protected BackSamplingDistribution getBackSamplingDistribution(BeliefNode node, WeightedSample s) {
		BackSamplingDistribution d = new BackSamplingDistribution(this);
		d.construct(node, s.nodeDomainIndices);
		return d;
	}
	
	@Override
	protected void _initialize() throws Exception {
		getOrdering(evidenceDomainIndices);
		if(debug) {
			out.println("sampling backward: " + this.backwardSampledNodes);
			out.println("sampling forward: " + this.forwardSampledNodes);
			out.println("not in order: " + this.outsideSamplingOrder);
		}
	}
	
	@Override
	public void _infer() throws Exception {		
		Stopwatch sw = new Stopwatch();
		sw.start();
		
		if(verbose) out.println("sampling...");
		WeightedSample s = new WeightedSample(this.bn, evidenceDomainIndices.clone(), 1.0, null, 0);
		for(currentStep = 1; currentStep <= this.numSamples; currentStep++) {	
			if(verbose && currentStep % infoInterval == 0)
				out.println("  step " + currentStep);
			getSample(s);
			this.addSample(s);
			onAddedSample(s);
			if(converged())
				break;
		}
		
		sw.stop();
		
		SampledDistribution dist = distributionBuilder.getDistribution();
		report(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/step)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep()));
	}
	
	/**
	 * gets one full sample of all of the nodes
	 * @param s
	 * @throws Exception 
	 */
	public void getSample(WeightedSample s) throws Exception {
		int MAX_TRIALS = this.maxTrials;	
loop1:  for(int t = 1; t <= MAX_TRIALS || MAX_TRIALS == 0; t++) {
			// initialize sample
			initSample(s);
			// backward sampling
			for(BeliefNode node : backwardSampledNodes) {
				if(!sampleBackward(node, s)) {
					if(debug) out.println("!!! backward sampling failed at " + node + " in step " + currentStep);
					continue loop1;
				}				
			}
			//out.println("after backward: weight = " + s.weight);
			// forward sampling
			for(BeliefNode node : forwardSampledNodes) {
				if(!sampleForward(node, s)) {
					if(debug) {/*
						BeliefNode[] domain_product = node.getCPF().getDomainProduct();
						StringBuffer cond = new StringBuffer();
						for(int i = 1; i < domain_product.length; i++) {
							if(i > 1)
								cond.append(", ");
							cond.append(domain_product[i].getName()).append(" = ");
							cond.append(domain_product[i].getDomain().getName(s.nodeDomainIndices[this.getNodeIndex(domain_product[i])]));
						}*/						
						out.println("!!! forward sampling failed at " + node + " in step " + currentStep + "; cond: " + s.getCPDLookupString(node));
					}
					continue loop1;
				}
			}
			//out.println("after forward: weight = " + s.weight);
			// nodes outside the sampling order: adjust weight
			for(BeliefNode node : outsideSamplingOrder) {
				double p = this.getCPTProbability(node, s.nodeDomainIndices);
				s.weight *= p;
				if(s.weight == 0.0) {
					if(p != 0.0)
						throw new Exception("Precision loss in weight calculation");
					// error diagnosis					
					if(debug) out.println("!!! weight became zero at unordered node " + node + " in step " + currentStep + "; cond: " + s.getCPDLookupString(node));
					if(debug && this instanceof BackwardSamplingWithPriors) {
						double[] dist = ((BackwardSamplingWithPriors)this).priors.get(node);
						out.println("prior: " + StringTool.join(", ", dist) + " value=" + s.nodeDomainIndices[getNodeIndex(node)]);
						CPF cpf = node.getCPF();
						BeliefNode[] domProd = cpf.getDomainProduct();						
						int[] addr = new int[domProd.length];
						for(int i = 1; i < addr.length; i++)
							addr[i] = s.nodeDomainIndices[getNodeIndex(domProd[i])];
						for(int i = 0; i < dist.length; i++) {
							addr[0] = i;
							dist[i] = cpf.getDouble(addr);
						}
						out.println("cpd: " + StringTool.join(", ", dist));
					}
					continue loop1;
				}
			}
			// sample could be obtained in this trial (t)
			s.trials = t;
			return;
		}
		throw new RuntimeException("Maximum number of trials exceeded.");
	}
	
	public void initSample(WeightedSample s) throws Exception {
		s.nodeDomainIndices = evidenceDomainIndices.clone();
		s.weight = 1.0;
		s.trials = 1;
		s.operations = 0;
	}
	
	protected boolean sampleForward(BeliefNode node, WeightedSample s) {
		int idx = super.sampleForward(node, s.nodeDomainIndices);
		if(idx == -1)
			return false;
		s.nodeDomainIndices[this.nodeIndices.get(node)] = idx;
		return true;
	}
	
	protected void onAddedSample(WeightedSample s) throws Exception {		
	}
}
