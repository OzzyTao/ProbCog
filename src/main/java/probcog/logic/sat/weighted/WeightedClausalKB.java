	/*******************************************************************************
 * Copyright (C) 2009-2012 Ralf Wernicke, Dominik Jain.
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
package probcog.logic.sat.weighted;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import probcog.exception.ProbCogException;
import probcog.logic.ComplexFormula;
import probcog.logic.Conjunction;
import probcog.logic.Formula;
import probcog.logic.TrueFalse;
import probcog.logic.sat.Clause.TautologyException;


/**
 * A knowledge base of weighted clauses that is built up from general weighted formulas (retaining a mapping from formulas to clauses and vice versa) 
 * @author Ralf Wernicke
 * @author Dominik Jain
 */
public class WeightedClausalKB implements Iterable<WeightedClause> {
	public enum ConversionMode {
		/**
		 * simply convert the original formula to CNF, no negation is applied
		 */
		NO_NEGATION,
		/**
		 * negate formulas that have negative weights, such that all weights become positive
		 * (which is required for MC-SAT, for example), and use the negated formula's CNF
		 */
		NEGATION_IF_WEIGHT_NEGATIVE,
		/**
		 * negate the original formula if the formula becomes a clause as a result (i.e. if the original formula was
		 * a conjunction of literals), avoiding corresponding splits at conjunctions.
		 * <p>Choose this if the goal is to obtain as many clauses as possible
		 * and avoid the generation of conjunctions at which formulas would need to split (e.g. for MaxWalkSAT).</p>
		 */
		NEGATION_IF_CLAUSE_RESULTS
	}
	
	public static class FormulaAndClauses {
		public final WeightedFormula weightedFormula;
		public final List<WeightedClause> weightedClauses;
		
		public FormulaAndClauses(WeightedFormula weightedFormula, List<WeightedClause> weightedClauses) {
			super();
			this.weightedFormula = weightedFormula;
			this.weightedClauses = weightedClauses;
		}
	}

    protected ArrayList<WeightedClause> clauses;
    protected List<FormulaAndClauses> formulaAndClausesList;

    /**
     * constructs a weighted clausal KB from a collection of weighted formulas
     * @param kb some collection of weighted formulas
     * @param requirePositiveWeights whether to negate formulas with negative weights to yield positive weights only
     * @throws ProbCogException
     */
    public WeightedClausalKB(Iterable<WeightedFormula> kb, boolean requirePositiveWeights) throws ProbCogException {
    	this();
    	ConversionMode conversionMode = requirePositiveWeights ? 
    			ConversionMode.NEGATION_IF_WEIGHT_NEGATIVE : ConversionMode.NO_NEGATION;
        for(WeightedFormula wf : kb) {
            addFormula(wf, conversionMode);
        }
    }
    
    /**
     * constructs a weighted clausal KB from a collection of weighted formulas
     * @param kb some collection of weighted formulas
     * @param conversionMode the mode to apply when converting formulas to clauses
     * @throws ProbCogException
     */
    public WeightedClausalKB(Iterable<WeightedFormula> kb, ConversionMode conversionMode) throws ProbCogException {
    	this();
        for(WeightedFormula wf : kb) {
            addFormula(wf, conversionMode);
        }
    }
    
    /**
     * constructs an empty weighted clausal KB
     */
    public WeightedClausalKB() {
        clauses = new ArrayList<>();
        formulaAndClausesList = new ArrayList<>();    	
    }
   
    /**
     * adds an arbitrary formula to the knowledge base (converting it to CNF and splitting it into clauses) 
     * @param wf formula whose clauses to add (it is automatically converted to CNF and split into clauses; the association between the formula and its clauses is retained)
     * @param makeWeightPositive whether to negate the formula if its weight is negative
     * @throws ProbCogException
     */
    public void addFormula(WeightedFormula wf, boolean makeWeightPositive) throws ProbCogException {
    	addFormula(wf, makeWeightPositive ? ConversionMode.NEGATION_IF_WEIGHT_NEGATIVE : ConversionMode.NO_NEGATION);
    }
    
    /**
     * Adds a weighted formula with known CNF to this knowledge base
     * @param wf the weighted formula
     * @param cnf the formula's CNF
     * @throws ProbCogException
     */
    protected void addFormula(WeightedFormula wf, Formula cnf) throws ProbCogException {
    	if(cnf instanceof Conjunction) { // conjunction of clauses
            Conjunction c = (Conjunction) cnf;
            int numChildren = c.children.length;
            List<WeightedClause> weightedClauses = new ArrayList<>();
            for(Formula child : c.children) {
            	try {
            		WeightedClause wc = new WeightedClause(child, wf.weight / numChildren, wf.isHard);
            		weightedClauses.add(wc);
            	}
            	catch(TautologyException e) {}
            }
            addFormulaAndClauses(wf, weightedClauses);
        } 
        else if(!(cnf instanceof TrueFalse)) { // clause
            try {
            	WeightedClause wc = new WeightedClause(cnf, wf.weight, wf.isHard);
				addFormulaAndClauses(wf, Arrays.asList(wc));
            }
            catch(TautologyException e) {}
        }
	}

	protected boolean isConjunctionOfLiterals(Formula cnf) {
    	if(cnf instanceof Conjunction) {
            Conjunction c = (Conjunction) cnf;
            for(Formula child : c.children) {
            	if (child instanceof ComplexFormula) {
            		return false;
            	}
            }
            return true;
        }     
    	else {
    		return false;
    	}
    }
    
    /**
     * adds an arbitrary formula to the knowledge base (converting it to CNF and splitting it into clauses) 
     * @param wf formula whose clauses to add (it is automatically converted to CNF and split into clauses; the association between the formula and its clauses is retained)
     * @param conversionMode the mode which controls how the conversion process performs the CNF conversion
     * @throws ProbCogException
     */
    public void addFormula(WeightedFormula wf, ConversionMode conversionMode) throws ProbCogException {
    	Formula cnf;
    	switch (conversionMode) {
    	case NO_NEGATION:
    		cnf = wf.formula.toCNF();
    		break;
    	case NEGATION_IF_WEIGHT_NEGATIVE:
    		if(wf.weight < 0) {
        		wf.weight *= -1;
        		wf.formula = new probcog.logic.Negation(wf.formula);
        	}
    		cnf = wf.formula.toCNF();
    		break;
		case NEGATION_IF_CLAUSE_RESULTS:
			cnf = wf.formula.toCNF();
			if (isConjunctionOfLiterals(cnf)) { 
				wf.weight *= -1;
				wf.formula = new probcog.logic.Negation(wf.formula);
				cnf = new probcog.logic.Negation(cnf).toCNF();
			}
			break;
		default:
			throw new RuntimeException("Unhandled conversion mode " + conversionMode);
    	}
    	addFormula(wf, cnf);
    }
    
    protected void addFormulaAndClauses(WeightedFormula wf, List<WeightedClause> wcs) {
        clauses.addAll(wcs);
        formulaAndClausesList.add(new FormulaAndClauses(wf, wcs));
    }
    
    /**
     * adds a weighted clause to the KB (where the weighted formula the clause originated from is the clause itself) 
     * @param wc the weighted clause to add
     */
    public void addClause(WeightedClause wc) {
    	addFormulaAndClauses(new WeightedFormula(wc, wc.weight, wc.isHard), Arrays.asList(wc));
    }

    /**
     * Method returns the iterator of the weighted clauses in knowledge base.
     * @return Iterator of weighted clauses
     */
    public Iterator<WeightedClause> iterator() {
        return clauses.iterator();
    }

    /**
     * returns the number of weighted clauses in the knowledge base.
     * @return size of the knowledge base (number of weighted clauses)
     */
    public int size() {
        return clauses.size();
    }

    /**
     * prints all weighted clauses in the knowledge base to stdout
     */
    public void print() {
        int i = 0;
        for (WeightedClause c : this)
            System.out.printf("%4d  %s\n", ++i, c.toString());
    }

    /**
     * gets a list of with formulas and the clauses that the formulas are made up of when converted to CNF
     * @return the list
     */
    public List<FormulaAndClauses> getFormulasAndClauses() {
        return this.formulaAndClausesList;
    }
}
