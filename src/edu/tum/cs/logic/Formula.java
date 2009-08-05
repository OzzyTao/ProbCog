package edu.tum.cs.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import edu.tum.cs.logic.parser.FormulaParser;
import edu.tum.cs.logic.parser.ParseException;
import edu.tum.cs.srl.Database;

public abstract class Formula {	
	public abstract void getVariables(Database db, Map<String,String> ret);
	/**
	 * grounds this formula for a particular binding of its variables
	 * @param binding		the variable binding
	 * @param worldVars		the set of ground atoms (which is needed to return the ground versions of atoms)
	 * @param db			a database containing a set of constants for each type that can be used to ground existentially quantified formulas
	 * @return
	 * @throws Exception
	 */
	public abstract Formula ground(Map<String, String> binding, WorldVariables worldVars, Database db) throws Exception;
	public abstract void getGroundAtoms(Set<GroundAtom> ret);
	public abstract boolean isTrue(IPossibleWorld w);

	/**
	 * gets a list of all groundings of the formula for a particular set of objects
	 * @param db  the database containing all relevant constant symbols (objects) to use for grounding
	 * @param worldVars  the collection of variables (ground atoms) that defines the set of possible worlds
	 * @return
	 * @throws Exception
	 */
	public Vector<Formula> getAllGroundings(Database db, WorldVariables worldVars) throws Exception {
		Vector<Formula> ret = new Vector<Formula>();
		addAllGroundingsTo(ret, db, worldVars);
		return ret;
	}

	/**
	 * generates all groundings and adds them to the given collection
	 * @param collection
	 * @param db
	 * @param worldVars
	 * @throws Exception
	 */
	public void addAllGroundingsTo(Collection<Formula> collection, Database db, WorldVariables worldVars) throws Exception {
		HashMap<String, String> vars = new HashMap<String, String>();
		getVariables(db, vars);
		String[] varNames = vars.keySet().toArray(new String[vars.size()]);
		generateGroundings(collection, db, new HashMap<String, String>(), varNames, 0, vars, worldVars);
	}

	/**
	 * recursively generates groundings of the formula
	 * @param ret  the collection in which to store the generated groundings
	 * @param db  the database in which all usable constant symbols are found
	 * @param binding  a mapping of variable names to constant names
	 * @param varNames  the variables that are to be grounded
	 * @param i  the current index of the variable in varNames to ground next
	 * @param var2domName  a mapping of variable names to domain names (that contains as keys at least the variables in varNames)
	 * @param worldVars  the collection of variables (ground atoms) that defines the set of possible worlds
	 * @throws Exception
	 */
	protected void generateGroundings(Collection<Formula> ret, Database db, Map<String, String> binding, String[] varNames, int i, Map<String, String> var2domName, WorldVariables worldVars) throws Exception {
		// if we have the full set of parameters, add it to the collection
        //TODO: kopieren und umbenennen und in der klasse belassen
		if(i == varNames.length) {
                        Formula f = (this.ground(binding, worldVars, db)).simplify(db);
                        if (!(f instanceof TrueFalse))
                            ret.add(f);
			return;
		}
		// otherwise consider all ways of extending the current list of parameters using the domain elements that are applicable
		String varName = varNames[i];
		String domName = var2domName.get(varName);
		Set<String> domain = db.getDomain(domName);
		if(domain == null)
			throw new Exception("Domain named '" + domName + "' (of variable " + varName + " in formula " + this.toString() + ") not found in the database!");
		for(String element : domain) {
			binding.put(varName, element);
			generateGroundings(ret, db, binding, varNames, i+1, var2domName, worldVars);
		}
	}

	public abstract Formula toCNF();

        public abstract Formula simplify(Database evidence);

	public static Formula fromString(String f) throws ParseException {
		return FormulaParser.parse(f);
	}

    public Vector<Formula> getAllSimplifiedGroundings(Database db, WorldVariables worldVars) throws Exception {
		Vector<Formula> ret = new Vector<Formula>();
		addAllSimplifiedGroundingsTo(ret, db, worldVars);
		return ret;
	}

	/**
	 * generates all groundings, simplifies them according to a given evidence (db) and adds them to the given collection
	 * @param collection
	 * @param db
	 * @param worldVars
	 * @throws Exception
	 */
	public void addAllSimplifiedGroundingsTo(Collection<Formula> collection, Database db, WorldVariables worldVars) throws Exception {
		HashMap<String, String> vars = new HashMap<String, String>();
		getVariables(db, vars);
		String[] varNames = vars.keySet().toArray(new String[vars.size()]);
		generateSimplifiedGroundings(collection, db, new HashMap<String, String>(), varNames, 0, vars, worldVars);
	}

	/**
	 * recursively generates simplified groundings of the formula
	 * @param ret  the collection in which to store the generated groundings
	 * @param db  the database in which all usable constant symbols are found
	 * @param binding  a mapping of variable names to constant names
	 * @param varNames  the variables that are to be grounded
	 * @param i  the current index of the variable in varNames to ground next
	 * @param var2domName  a mapping of variable names to domain names (that contains as keys at least the variables in varNames)
	 * @param worldVars  the collection of variables (ground atoms) that defines the set of possible worlds
	 * @throws Exception
	 */
	   protected void generateSimplifiedGroundings(Collection<Formula> ret, Database db, Map<String, String> binding, String[] varNames, int i, Map<String, String> var2domName, WorldVariables worldVars) throws Exception {
        // if we have the full set of parameters, add it to the collection
        if (i == varNames.length) {
            Formula f = (this.ground(binding, worldVars, db)).simplify(db);
            if (!(f instanceof TrueFalse)) {
                ret.add(f);
            }
            return;
        }
        // otherwise consider all ways of extending the current list of parameters using the domain elements that are applicable
        String varName = varNames[i];
        String domName = var2domName.get(varName);
        Set<String> domain = db.getDomain(domName);
        if (domain == null) {
            throw new Exception("Domain named '" + domName + "' (of variable " + varName + " in formula " + this.toString() + ") not found in the database!");
        }
        for (String element : domain) {
            binding.put(varName, element);
            generateSimplifiedGroundings(ret, db, binding, varNames, i + 1, var2domName, worldVars);
        }
    }

	public static void main(String[] args) throws ParseException {
		String s = "a(x) <=> b(x)";
		//s = "a(x) v (b(x) ^ c(x))";
		//s = "(a(x) v (b(x) ^ c(x))) => f(x)";
		s = "(a(x) ^ b(x) ^ !c(x) ^ !d(x)) v (a(x) ^ !b(x) ^ c(x) ^ !d(x)) v (!a(x) ^ b(x) ^ c(x) ^ !d(x)) v (a(x) ^ !b(x) ^ !c(x) ^ d(x)) v (!a(x) ^ b(x) ^ !c(x) ^ d(x)) v (!a(x) ^ !b(x) ^ c(x) ^ d(x))";
		Formula f = fromString(s);
		System.out.println("CNF: " + f.toCNF().toString());
	}
}
