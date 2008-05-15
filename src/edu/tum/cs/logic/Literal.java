package edu.tum.cs.logic;

import java.util.HashMap;
import java.util.Set;

import edu.tum.cs.bayesnets.relational.core.Database;

public class Literal extends Formula {
	public boolean isPositive;
	public Atom atom;
	
	public Literal(boolean isPositive, Atom atom) {
		this.atom = atom;
		this.isPositive = isPositive;
	}
	
	public String toString() {
		return isPositive ? atom.toString() : "!" + atom;
	}

	@Override
	public void getVariables(Database db, HashMap<String, String> ret) {
		atom.getVariables(db, ret);	
	}

	@Override
	public Formula ground(HashMap<String, String> binding, WorldVariables vars, Database db) throws Exception {
		return new GroundLiteral(isPositive, (GroundAtom)atom.ground(binding, vars, db));
	}

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
	}

	@Override
	public boolean isTrue(PossibleWorld w) {
		throw new RuntimeException("not supported");
	}
}
