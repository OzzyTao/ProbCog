/*******************************************************************************
 * Copyright (C) 2012 Dominik Jain.
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
package probcog.wcsp;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

/**
  * Represents a WCSP constraint.
  * @author Dominik Jain
  */
public class Constraint {
	protected HashMap<ArrayKey, Tuple> tuples;
	/**
	 * array of variable indices encompassed by this constraint
	 */
	protected int[] varIndices;
	protected long defaultCost;
	
	public Constraint(long defaultCost, int[] varIndices, int initialTuples) {
		this.varIndices = varIndices;
		this.defaultCost = defaultCost;		
		tuples = new HashMap<ArrayKey, Tuple>(initialTuples);
	}
	
	public void addTuple(int[] domainIndices, long cost) {
		tuples.put(new ArrayKey(domainIndices), new Tuple(domainIndices, cost));
	}
	
	public void addTuple(Tuple t) {
		tuples.put(new ArrayKey(t.domIndices), t);
	}
	
	public long getCost(int[] domainIndices) {
		Tuple t = tuples.get(domainIndices);
		if(t == null)
			return defaultCost;
		return t.cost;
	}
	
	public int[] getVarIndices() {
		return varIndices;
	}
	
	public java.util.Collection<Tuple> getTuples() {
		return tuples.values();
	}
	
	public Tuple getTuple(int[] setting) {
		return tuples.get(new ArrayKey(setting));
	}
	
	public Tuple getTuple(ArrayKey k) {
		return tuples.get(k);
	}
	
	public long getDefaultCosts() {
		return defaultCost;
	}
	
	public void setDefaultCosts(long c) {
		this.defaultCost = c;
	}
	
	/**
	 * @return the number of tuples in this constraint
	 */
	public int size() {
		return tuples.size();
	}
	
	/**
	 * merge the contents of another constraint c2, whose domain and variable ordering is the same, into this constraint
	 * @param c2 the constraint to merge into this. The constraint should no longer be used after being passed to this
	 * method, as its contents are modified.
	 */
	public void merge(Constraint c2) {
		long c2defaultCosts = c2.getDefaultCosts();	
		boolean c1TupleUpdateRequired = c2defaultCosts != 0L;
		// add contents of c2's tuples to c1
		HashSet<Tuple> processedTuples = c1TupleUpdateRequired ? new HashSet<Tuple>(c2.size()) : null;
		for(Tuple t2 : c2.getTuples()) {
			// unify with corresponding tuple
			Tuple t1 = getTuple(t2.domIndices);
			if(t1 != null) {
				t1.cost += t2.cost;
				if(c1TupleUpdateRequired) processedTuples.add(t1);
			}
			else {
				t2.cost += this.defaultCost;
				addTuple(t2);
				if(c1TupleUpdateRequired) processedTuples.add(t2);
			}
		}
		// if c2 has relevant default costs...				 
		if(c1TupleUpdateRequired)  {
			// add c2's default costs to tuples found in c1 but not in c2
			for(Tuple t1 : getTuples()) {
				if(!processedTuples.contains(t1)) {
					t1.cost += c2defaultCosts;
				}
			}
			// update the default costs
			setDefaultCosts(defaultCost + c2defaultCosts);
		}
	}
	
	/**
	 * merges the contents of c2, which is assumed to have the same domain but not necessarily the same variable ordering,
	 * into this constraint
	 * @param c2 the constraint to merge into this. The constraint should no longer be used after being passed to this
	 * method, as its contents are modified.
	 */
	public void mergeReorder(Constraint c2) {
		HashMap<Integer,Integer> varIdx2arrayIdx = new HashMap<Integer, Integer>();
		int[] c2varIndices = c2.varIndices;
		boolean sameOrder = true;
		for(int k = 0; k < varIndices.length; k++) {
			varIdx2arrayIdx.put(varIndices[k], k);
			sameOrder = sameOrder && varIndices[k] == c2varIndices[k];
		}
		// if the order isn't the same, reorder all of c2's tuples
		if(!sameOrder) {
			for(Tuple t2 : c2.getTuples()) {
				int[] domIndices = new int[t2.domIndices.length];
				for(int k = 0; k < varIndices.length; k++)
					domIndices[varIdx2arrayIdx.get(c2varIndices[k])] = t2.domIndices[k];
				t2.domIndices = domIndices;
			}
		}
		// do the actual merge
		merge(c2);
	}
	
	public void writeWCSP(PrintStream out) {
		// first line of the constraint: arity of the constraint followed by indices of the variables, default costs, and number of constraint lines
		out.print(varIndices.length);
		out.print(' ');
		for(int varIdx : varIndices) {
			out.print(varIdx);
			out.print(' ');
		}
		out.print(defaultCost);
		out.print(' ');
		out.println(tuples.size());
		// actual constraint tuples
		for(Entry<ArrayKey, Tuple> e : tuples.entrySet()) {
			Tuple t = e.getValue();
			for(int domIdx : t.domIndices) {
				out.print(domIdx);
				out.print(' ');				
			}
			out.println(t.cost);
		}
	}

	
	protected static class Tuple {
		public int[] domIndices;
		public long cost;
		
		public Tuple(int[] domIndices, long cost) {
			this.cost = cost;
			this.domIndices = domIndices;
		}

		/**
		 * @param c the constraint for which the check is made
		 * @param partialAssignment 
		 * @return true if under the given partial assignment, the tuple could apply (i.e. the assignment
		 * includes only assignments that are also made in this tuple)
		 */
		public boolean couldApply(Constraint c, java.util.Map<Integer,Integer> partialAssignment) {
			for(int i = 0; i < c.varIndices.length; i++) {
				Integer a = partialAssignment.get(c.varIndices[i]);
				if(a != null && domIndices[i] != a)
					return false;
			}
			return true;
		}

	}
	
	public static final class ArrayKey {
		protected int[] array; 
		protected int hashCode;
		
		public ArrayKey(int[] domIndices) {
			this.array = domIndices;
			this.hashCode = Arrays.hashCode(array);
		}
		
		@Override
		public int hashCode() {
			return hashCode;
		}
		
		public boolean equals(Object o) {
			if(!(o instanceof ArrayKey))
				return false;
			return Arrays.equals(this.array, ((ArrayKey)o).array);
		}
	}
}
