package edu.tum.cs.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import edu.tum.cs.bayesnets.relational.core.Database;
import edu.tum.cs.bayesnets.relational.core.RelationalBeliefNetwork;
import edu.tum.cs.bayesnets.relational.core.Signature;
import edu.tum.cs.tools.StringTool;

public class Atom extends Formula {

	public Collection<String> params;
	public String predName;
	
	public Atom(String predName, Collection<String> params) {
		this.predName = predName;
		this.params = params;
	}
	
	@Override
	public String toString() {
		return predName + "(" + StringTool.join(",", params) + ")";
	}

	@Override
	public void getVariables(Database db, HashMap<String, String> ret) {
		Signature sig = db.getSignature(predName);
		int i = 0;
		for(String param : params) {			
			if(isVariable(param)) {
				String type;
				if(i < sig.argTypes.length)
					type = sig.argTypes[i];
				else
					type = sig.returnType;
				ret.put(param, type);
			}			
			++i;
		}
	}
	
	public static boolean isVariable(String paramName) {
		return Character.isLowerCase(paramName.charAt(0));
	}

	@Override
	public Formula ground(HashMap<String, String> binding, WorldVariables vars, Database db) throws Exception {
		StringBuffer sb = new StringBuffer(predName + "(");
		int i = 0;
		for(String param : params) {
			if(i++ > 0)
				sb.append(',');
			String value = binding.get(param);
			if(value == null)
				value = param;
			sb.append(value);
		}	
		sb.append(')');
		String strGA = sb.toString();
		GroundAtom ga = vars.get(strGA);
		if(ga == null)
			throw new Exception("Could not find ground atom '" + strGA + "' in set of world variables " + vars);
		return ga;
	}

	@Override
	public void getGroundAtoms(Set<GroundAtom> ret) {
		// TODO Auto-generated method stub		
	}

	@Override
	public boolean isTrue(PossibleWorld w) {
		throw new RuntimeException("not supported");
	}
}