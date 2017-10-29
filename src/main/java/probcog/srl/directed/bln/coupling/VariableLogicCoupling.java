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
package probcog.srl.directed.bln.coupling;

import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import probcog.logic.GroundAtom;
import probcog.logic.GroundLiteral;
import probcog.logic.PossibleWorld;
import probcog.logic.WorldVariables;
import probcog.logic.WorldVariables.Block;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;

/**
 * Represents the coupling between world variables and logical variables for 
 * a concrete ground model.
 * @author Dominik Jain
 */
public class VariableLogicCoupling {
	protected WorldVariables worldVars;
	/**
	 * maps (non-auxiliary) belief nodes to the corresponding logical variable coupler
	 */
	protected HashMap<BeliefNode, IVariableLogicCoupler> variable2groundAtomLookup;
	protected HashMap<GroundAtom, BeliefNode> groundAtom2variable;
	
	public VariableLogicCoupling() {
		worldVars = new WorldVariables();
		variable2groundAtomLookup = new HashMap<BeliefNode, IVariableLogicCoupler>();
		groundAtom2variable = new HashMap<GroundAtom, BeliefNode>();
	}
	
	public void addBooleanVariable(BeliefNode var, String predicateName, String[] params) {
		GroundAtom ga = new GroundAtom(predicateName, params);
		worldVars.add(ga);
		variable2groundAtomLookup.put(var, new BooleanVariable(ga));
		groundAtom2variable.put(ga, var);
	}
	
	public void addBlockVariable(BeliefNode var, Discrete dom, String functionName, String[] params) {
		String[] atomParams = new String[params.length+1];
		for(int i = 0; i < params.length; i++)
			atomParams[i] = params[i];
		Vector<GroundAtom> block = new Vector<GroundAtom>(dom.getOrder());
		for(int i = 0; i < dom.getOrder(); i++) {
			atomParams[atomParams.length-1] = dom.getName(i);
			GroundAtom ga = new GroundAtom(functionName, atomParams.clone());
			block.add(ga);				
		} 
		Block b = worldVars.addBlock(block); 
		for(GroundAtom ga : b)
			groundAtom2variable.put(ga, var);
		variable2groundAtomLookup.put(var, new BlockVariable(b));
	}
	
	/**
	 * Adds a logical/evidence variable, for which no actual coupling is required, but 
	 * which still needs to be added to the world variables
	 * @param functionName
	 * @param params
	 */
	public void addEvidenceVariable(String functionName, String[] params) {
		GroundAtom ga = new GroundAtom(functionName, params);
		worldVars.add(ga);
	}
	
	public GroundLiteral getGroundLiteral(BeliefNode var, int domIdx) {
		IVariableLogicCoupler vlc = variable2groundAtomLookup.get(var);
		if(vlc == null)
			throw new RuntimeException("Variable " + var + " has no logical coupling!");
		return vlc.getGroundLiteral(domIdx, worldVars);
	}
	
	public WorldVariables getWorldVars() {
		return worldVars;
	}
	
	/**
	 * returns the belief node that corresponds to the given ground atom
	 * @param gndAtom
	 * @return the belief node corresponding to gndAtom or null if no correspondence is found 
	 */
	public BeliefNode getVariable(GroundAtom gndAtom) {
		return groundAtom2variable.get(gndAtom);
	}
	
	public int getVariableValue(BeliefNode var, PossibleWorld w) {
		return variable2groundAtomLookup.get(var).getValue(w);
	}
	
	/**
	 * sets the given variable value in the possible world
	 * @param var
	 * @param domIdx
	 * @param w the logical state in which to set the value
	 */
	public void setVariableValue(BeliefNode var, int domIdx, PossibleWorld w) {
		variable2groundAtomLookup.get(var).setValue(w, domIdx);
	}

	/**
	 * 
	 * @param var
	 * @return true if the variable has a logical coupling
	 */
	public boolean hasCoupling(BeliefNode var) {
		return variable2groundAtomLookup.containsKey(var);
	}
	
	/**
	 * gets the set of regular variables (i.e. non-auxiliary belief nodes, which do not correspond to logical constraints) 
	 * @return
	 */
	public Set<BeliefNode> getCoupledVariables() {
		return variable2groundAtomLookup.keySet();
	}
	
	public Set<GroundAtom> getCoupledGroundAtoms() {
		return groundAtom2variable.keySet();
	}
	
	/**
	 * gets the original parameters of the non-logical variable
	 * @param var
	 * @return
	 */
	public Iterable<String> getOriginalParams(BeliefNode var) {
		return variable2groundAtomLookup.get(var).getOriginalParams();
	}
}
