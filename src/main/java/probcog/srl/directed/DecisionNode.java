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
package probcog.srl.directed;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import probcog.logic.Formula;
import probcog.logic.GroundAtom;
import probcog.logic.IPossibleWorld;
import probcog.logic.PossibleWorldFromDatabase;
import probcog.logic.WorldVariables;
import probcog.logic.parser.FormulaParser;
import probcog.logic.parser.ParseException;
import probcog.srl.GenericDatabase;

/**
 * Represents a logical precondition for a BLN fragment.
 * @author Dominik Jain
 */
public class DecisionNode extends ExtendedNode {
	protected boolean isOperator;
	protected enum Operator {
		Negation;
	};
	protected Operator operator;
	protected Formula formula;
	
	public DecisionNode(RelationalBeliefNetwork rbn, edu.ksu.cis.bnj.ver3.core.BeliefNode node) throws Exception {
		super(rbn, node);
		// check if the node is an operator that is to be applied to its parents, which are also decision nodes
		operator = null;
		if(node.getName().equals("neg")) {				
			operator = Operator.Negation; 
		}
		// if it's not an operator, it's a formula which we have to parse
		if(operator == null) {
			try {
				formula = FormulaParser.parse(node.getName());
			}
			catch(ParseException e) {
				throw new Exception("Could not parse the formula '" + node.getName() + "'", e);
			}
		}
	}
	
	/**
	 * returns the truth value of the formula that corresponds to this decision node 
	 * @param varBinding	variable binding that applies (as given by the actual parameters of this decision node's child/descendant)
	 * @param w				a possible world specifying truth values for all variables (ground atoms)
	 * @param worldVars		a set of world variables to take ground atom instances from (for grounding the formula)		
	 * @param db			a database to take objects from for existential quantification
	 * @return	true if the formula is satisfied
	 * @throws Exception
	 */
	public boolean isTrue(Map<String, String> varBinding, IPossibleWorld w, WorldVariables worldVars, GenericDatabase<?,?> db) throws Exception {
		if(operator != null) {
			Collection<DecisionNode> parents = this.getDecisionParents();
			switch(operator) {
			case Negation:
				if(parents.size() != 1)
					throw new Exception("Operator neg must have exactly one parent");
				return !parents.iterator().next().isTrue(varBinding, w, worldVars, db);		
			default:
				throw new Exception("Operator not handled");
			}
		}
		else {
			try {
				Formula gf = formula.ground(varBinding, worldVars, db);
				return gf.isTrue(w);				
			}
			catch(Exception e) {
				throw new Exception("Cannot evaluate precondition " + formula, e);
			}
		}
	}
	
	/**
	 * a wrapper for the other implementation of isTrue that uses the possible world implied by the database to determine the truth values of ground atoms
	 * @param paramNames		variables that are bound
	 * @param actualParams		values the variables are bound to
	 * @param db				the database that provides the truth values for all ground atoms (closed-world assumption)
	 * @param closedWorld		whether to make the closed-world assumption (i.e. that atoms not specified in the database are false)
	 * @return
	 * @throws Exception 
	 */
	public boolean isTrue(String[] paramNames, String[] actualParams, GenericDatabase<?,?> db, boolean closedWorld) throws Exception {
		// generate variable bindings
		HashMap<String, String> varBinding = new HashMap<String, String>();
		for(int i = 0; i < paramNames.length; i++)
			varBinding.put(paramNames[i], actualParams[i]);
		return isTrue(varBinding, db, closedWorld);
	}
	
	/**
	 * a wrapper for the other implementation of isTrue that uses the possible world implied by the database to determine the truth values of ground atoms
	 */
	public boolean isTrue(HashMap<String, String> varBinding, GenericDatabase<?,?> db, boolean closedWorld) throws Exception {
		// construct a dummy collection of world variables that can be used to obtain ground atoms for ground formulas
		WorldVariables worldVars = new WorldVariables() { 
			@Override
			public GroundAtom get(String gndAtom) {
				return new GroundAtom(gndAtom); // since we don't need indexed ground atoms, we can just construct them on demand
			}
		};
		// call other implementation
		return isTrue(varBinding, new PossibleWorldFromDatabase(this.bn, db, closedWorld), worldVars, db);
	}
	
	@Override
	public String toString() {
		if(operator != null) {
			Collection<DecisionNode> parents = this.getDecisionParents();
			switch(operator) {
			case Negation:
				return "!(" + parents.iterator().next().toString() + ")";		
			default:
				throw new RuntimeException("Operator not handled");
			}
		}
		else {
			return this.formula.toString();
		}	
	}
	
	public Formula getFormula() {
		return formula;
	}
}
