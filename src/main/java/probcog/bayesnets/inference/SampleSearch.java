/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain.
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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Vector;

import probcog.bayesnets.core.BeliefNetworkEx;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.tum.cs.util.Stopwatch;
import edu.tum.cs.util.datastruct.Map2D;

/**
 * Simple implementation of the SampleSearch algorithm by Gogate & Dechter.
 * <p>The implementation will apply an unbiased estimator (which uses more memory) 
 * only if enabled via {@link #setUseProperWeighting}.</p>
 * 
 * @author Dominik Jain
 */
public class SampleSearch extends Sampler {
	protected int[] nodeOrder;
	protected int currentStep;

	protected double[] samplingProb;
	protected boolean useProperWeighting = false;

	protected boolean usingTopologicalOrdering = true;

	protected ImportanceFunction importanceFunction = ImportanceFunction.Prior;
	protected SampledDistribution importanceDist = null;
	protected int importanceFunctionSteps = 2;

	
	protected enum ImportanceFunction {
		Prior, BP, IJGP;
	}
	
	public SampleSearch(BeliefNetworkEx bn) throws Exception {
		super(bn);
				
		this.paramHandler.add("importanceFunction", "setImportanceFunction");
		this.paramHandler.add("ifSteps", "setImportanceFunctionSteps");
		this.paramHandler.add("bpSteps", "setImportanceFunctionSteps");
		this.paramHandler.add("ijgpSteps", "setImportanceFunctionSteps");
		this.paramHandler.add("unbiased", "setUseProperWeighting");
	}
	
	@Override
	protected void _initialize() throws Exception {
		// TODO could help to guarantee for BLNs that formula nodes appear as early as possible
		nodeOrder = computeNodeOrdering();
		samplingProb = new double[nodes.length];
		
		if(importanceFunction != ImportanceFunction.Prior) {
			if(verbose) System.out.println("computing importance function with " + importanceFunction + "...");
			Sampler s = importanceFunction == ImportanceFunction.BP ? new BeliefPropagation(this.bn) : new IJGP(bn);
			s.setNumSamples(importanceFunctionSteps);
			s.setEvidence(this.evidenceDomainIndices);
			importanceDist = s.infer();
			
			if(debug) {
				System.out.println("importance distribution:");
				importanceDist.print(System.out);
			}
		}
	}
	
	public void setImportanceFunction(String name) {
		importanceFunction = ImportanceFunction.valueOf(name);
	}
	
	public void setImportanceFunctionSteps(int steps) {
		this.importanceFunctionSteps = steps;
	}
	
	protected int[] computeNodeOrdering() throws Exception {
		return bn.getTopologicalOrder();
	}
	
	public void setUseProperWeighting(boolean enabled){
		useProperWeighting = enabled;
	}
	
	protected void info(int step) {
		out.println("  step " + step);
	}
	
	@Override
	public void _infer() throws Exception {
		// sample
		Stopwatch sw = new Stopwatch();
		out.println("sampling...");
		sw.start();
		
		WeightedSample s = new WeightedSample(bn);		
		for(int i = 1; i <= numSamples; i++) {
			currentStep = i;
			if(i % infoInterval == 0)
				info(i);			
			WeightedSample ret = getWeightedSample(s, nodeOrder, evidenceDomainIndices); 
			if(ret != null) {
				addSample(ret);
				/*
				// debugging of weighting
				out.print("w=" + ret.weight);
				double prod = 1.0;
				for(int j = 0; j < evidenceDomainIndices.length; j++)
					if(true || evidenceDomainIndices[j] == -1) {
						BeliefNode node = nodes[j];							
						out.print(" " + node.getName() + "=" + node.getDomain().getName(s.nodeDomainIndices[j]));
						double p = bn.getCPTProbability(node, s.nodeDomainIndices);
						out.printf(" %f", p);
						if(p == 0.0)
							throw new Exception("Sample has 0 probability.");							
						prod *= p;
						if(prod == 0.0)
							throw new Exception("Precision loss - product became 0");
					}
				out.println();
				*/
			}
			if(converged())
				break;
		}
		
		SampledDistribution dist = distributionBuilder.getDistribution();
		report(String.format("time taken: %.2fs (%.4fs per sample, %.1f trials/sample, %.4f*N assignments/sample, %d samples)\n", sw.getElapsedTimeSecs(), sw.getElapsedTimeSecs()/numSamples, dist.getTrialsPerStep(), (float)dist.operations/nodes.length/numSamples, dist.steps));
	}
	
	public WeightedSample getWeightedSample(WeightedSample s, int[] nodeOrder, int[] evidenceDomainIndices) throws Exception {
		s.trials = 1;
		s.operations = 0;	
		s.weight = 1.0;		
		// assign values to the nodes in order
		HashMap<Integer, boolean[]> domExclusions = new HashMap<Integer, boolean[]>();
		for(int i=0; i < nodeOrder.length;) {
			s.operations++;
			int nodeIdx = nodeOrder[i];
			int domainIdx = evidenceDomainIndices[nodeIdx];
			// get domain exclusions
			boolean[] excluded = domExclusions.get(nodeIdx);
			if(excluded == null) {
				excluded = new boolean[nodes[nodeIdx].getDomain().getOrder()];
				domExclusions.put(nodeIdx, excluded);
			}
			// debug info
			if(debug) {					
				int numex = 0;
				for(int j=0; j<excluded.length; j++)
					if(excluded[j])
						numex++;
				out.printf("    step %d, node %d '%s' (%d/%d exclusions)\n", currentStep, i, nodes[nodeIdx].getName(), numex, excluded.length);
			}
			// for evidence nodes, we can continue if the evidence probability was non-zero
			if(domainIdx >= 0) { 
				s.nodeDomainIndices[nodeIdx] = domainIdx;
				samplingProb[nodeIdx] = 1.0;
				double prob = getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
				if(prob != 0.0) {
					++i;
					continue;
				}
				else {
					if(debug)
						out.println("      evidence with probability 0.0; backtracking...");
				}
			} 
			// for non-evidence nodes, do forward sampling
			else {
				SampledAssignment sa = sampleForward(nodes[nodeIdx], s.nodeDomainIndices, excluded);
				if(sa != null) {
					domainIdx = sa.domIdx;
					samplingProb[nodeIdx] = sa.probability;				
					s.nodeDomainIndices[nodeIdx] = domainIdx;
					++i;
					continue;
				}
				else if(debug)
					out.println("      impossible case; backtracking...");
			}
			// if we get here, we need to backtrack to the last non-evidence node
			s.trials++;
			do {
				// kill the current node's exclusions
				domExclusions.remove(nodeIdx); 
				// add the previous node's setting as an exclusion
				--i;
				if(i < 0)
					throw new Exception("Could not find a sample with non-zero probability. Most likely, the evidence specified has 0 probability.");
				nodeIdx = nodeOrder[i];
				boolean[] prevExcl = domExclusions.get(nodeIdx);
				prevExcl[s.nodeDomainIndices[nodeIdx]] = true;
				// proceed with previous node...				
			} while(evidenceDomainIndices[nodeIdx] != -1);
		}
		return s;
	}
	
	public class SampledAssignment {
		public int domIdx;
		public double probability;
		public SampledAssignment(int domainIdx, double p) {
			domIdx = domainIdx;
			probability = p;
		}
	}
	
	/**
	 * samples forward, i.e. samples a value for 'node' given its parents
	 * @param node  the node for which to sample a value
	 * @param nodeDomainIndices  array of domain indices for all nodes in the network; the values for the parents of 'node' must be set already
	 * @return  the index of the domain element of 'node' that is sampled, or -1 if sampling is impossible because all entries in the relevant column are 0
	 */
	protected SampledAssignment sampleForwardPrior(BeliefNode node, int[] nodeDomainIndices, boolean[] excluded) {
		CPF cpf = node.getCPF();
		BeliefNode[] domProd = cpf.getDomainProduct();
		int[] addr = new int[domProd.length];
		// get the addresses of the first two relevant fields and the difference between them
		for(int i = 1; i < addr.length; i++)
			addr[i] = nodeDomainIndices[this.nodeIndices.get(domProd[i])];		
		addr[0] = 0; // (the first element in the index into the domain of the node we are sampling)
		int realAddr = cpf.addr2realaddr(addr);
		addr[0] = 1;
		int diff = cpf.addr2realaddr(addr) - realAddr; // diff is the address difference between two consecutive entries in the relevant column
		// get probabilities for outcomes
		double[] cpt_entries = new double[domProd[0].getDomain().getOrder()];
		double sum = 0;
		for(int i = 0; i < cpt_entries.length; i++) {
			double value;
			if(excluded[i])
				value = 0.0;
			else
				value = cpf.getDouble(realAddr); 
			cpt_entries[i] = value;
			sum += value;
			realAddr += diff;
		}
		// if the column contains only zeros, it is an impossible case -> cannot sample
		if(sum == 0)
			return null;
		int domIdx = sample(cpt_entries, sum, generator);
		return new SampledAssignment(domIdx, cpt_entries[domIdx]/sum);
	}
	
	protected SampledAssignment sampleForward(BeliefNode node, int[] nodeDomainIndices, boolean[] excluded) {
		if(this.importanceDist == null)
			return sampleForwardPrior(node, nodeDomainIndices, excluded);
		
		CPF cpf = node.getCPF();
		BeliefNode[] domProd = cpf.getDomainProduct();
		int[] addr = new int[domProd.length];
		// get the addresses of the first two relevant fields and the difference between them
		for(int i = 1; i < addr.length; i++)
			addr[i] = nodeDomainIndices[this.nodeIndices.get(domProd[i])];		
		addr[0] = 0; // (the first element in the index into the domain of the node we are sampling)
		int realAddr = cpf.addr2realaddr(addr);
		addr[0] = 1;
		int diff = cpf.addr2realaddr(addr) - realAddr; // diff is the address difference between two consecutive entries in the relevant column
		// get probabilities for outcomes
		// If we are sampling in top. order, we always additionally filter
		// values that are zero given the parents
		double[] samplingDist = importanceDist.getDistribution(getNodeIndex(node));
		double sum = 0;
		for(int i = 0; i < samplingDist.length; i++) {
			Double cptValue = null;
			if(usingTopologicalOrdering)
				cptValue = cpf.getDouble(realAddr);
			if(excluded[i] || (cptValue != null && cptValue.equals(0.0)))
				samplingDist[i] = 0.0;
			sum += samplingDist[i];
			realAddr += diff;
		}
		// if the column contains only zeros, it is an impossible case -> cannot sample
		if(sum == 0)
			return null;
		int domIdx = sample(samplingDist, sum, generator);
		return new SampledAssignment(domIdx, samplingDist[domIdx]/sum);
	}

	@Override
	public String getAlgorithmName() {
		return super.getAlgorithmName() + "[" + importanceFunction + "]";
	}
	
	@Override
	protected IDistributionBuilder createDistributionBuilder() throws Exception {
		if(useProperWeighting)			
			return new UnbiasedEstimator();
		else
			return new BiasedEstimator();
	}
	
	/**
	 * simple but biased estimator
	 */
	protected class BiasedEstimator extends DirectDistributionBuilder {

		public BiasedEstimator() throws Exception {
			super(createDistribution());
		}
		
		@Override
		public void addSample(WeightedSample s) {
			// do weighting
			s.weight = 1.0;
			for(int i = 0; i < nodes.length; i++) {
				s.weight *= getCPTProbability(nodes[i], s.nodeDomainIndices) / samplingProb[i];
			}
			// directly add to distribution
			super.addSample(s);
		}
	}
	
	/**
	 * unbiased "max"-estimator (additional storage space and computation time required)
	 */
	protected class UnbiasedEstimator implements IDistributionBuilder {
		protected Map2D<Integer,BigInteger,Double> maxQ;
		protected Vector<WeightedSample> samples;
		protected SampledDistribution dist;
		protected boolean dirty = false;
		
		public UnbiasedEstimator() throws Exception {
			maxQ = new Map2D<Integer,BigInteger,Double>();
			samples = new Vector<WeightedSample>();
		}
		
		@Override
		public synchronized void addSample(WeightedSample s) throws Exception {
			BigInteger partAssign = BigInteger.valueOf(0);
			Vector<Integer> partAssign2 = new Vector<Integer>();
			for(int i = 0; i < nodeOrder.length; i++) {				
				int nodeIdx = nodeOrder[i];
				if(evidenceDomainIndices[nodeIdx] < 0) {
					partAssign = partAssign.multiply(BigInteger.valueOf(nodes[nodeIdx].getDomain().getOrder()));
					partAssign = partAssign.add(BigInteger.valueOf(s.nodeDomainIndices[nodeIdx]));
					partAssign2.add(s.nodeDomainIndices[nodeIdx]);
					Double p = maxQ.get(i, partAssign);
					if(p == null || samplingProb[nodeIdx] > p) {							
						this.maxQ.put(i, partAssign, samplingProb[nodeIdx]);
					}
				}
			}
			samples.add(s.clone());
			dirty = true;
		}

		@Override
		public synchronized SampledDistribution getDistribution() throws Exception {			
			if(!dirty)
				return dist;
			System.out.println("unbiased sample weighting...");
			dist = createDistribution();
			for(WeightedSample s : samples) {
				s.weight = 1.0;					
				BigInteger partAssign = BigInteger.valueOf(0);
				for(int i = 0; i < nodeOrder.length; i++) {
					int nodeIdx = nodeOrder[i];					
					if(evidenceDomainIndices[nodeIdx] < 0) {
						partAssign = partAssign.multiply(BigInteger.valueOf(nodes[nodeIdx].getDomain().getOrder()));
						partAssign = partAssign.add(BigInteger.valueOf(s.nodeDomainIndices[nodeIdx]));
						s.weight *= getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices) / maxQ.get(i, partAssign);
					}
					else
						s.weight *= getCPTProbability(nodes[nodeIdx], s.nodeDomainIndices);
				}					
				dist.addSample(s);
			}
			dirty = false;
			return dist;
		}
	}
}
