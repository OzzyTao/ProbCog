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
package probcog.srl.directed.bln;


import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import probcog.inference.IParameterHandler;
import probcog.inference.ParameterHandler;
import probcog.logic.parser.ParseException;
import probcog.srl.Database;
import probcog.srl.directed.ABLModel;
import probcog.srl.directed.RelationalBeliefNetwork;

/**
 * Abstract base class of Bayesian logic network implementations.
 * @author Dominik Jain
 */
public abstract class AbstractBayesianLogicNetwork extends ABLModel implements IParameterHandler {
	public RelationalBeliefNetwork rbn;
	public File logicFile;
	protected ParameterHandler paramHandler;
	/**
	 * whether to allow that some nodes aren't instantiated because no fragment is applicable (simply skip them if this is true)
	 */
	protected boolean allowPartialInstantiation;
	
	/**
	 * @param declsFile
	 * @param networkFile
	 * @param logicFile may be null
	 * @throws Exception
	 */
	public AbstractBayesianLogicNetwork(String declsFile, String networkFile, String logicFile) throws Exception {
		super(declsFile, networkFile); // reads declarations
		if(logicFile != null)
			setConstraintsFile(new File(logicFile));
		this.paramHandler = new ParameterHandler(this);
		this.rbn = this;
		initKB();
	}
	
	public AbstractBayesianLogicNetwork(String declsFile) throws Exception {
		super(declsFile); // reads declarations
		this.paramHandler = new ParameterHandler(this);
		this.rbn = this;
		initKB();
	}
	
	public void setAllowPartialInstantiation(boolean allow) {
		this.allowPartialInstantiation = allow;
	}

	protected abstract void initKB() throws Exception;
	
	public abstract AbstractGroundBLN ground(Database db) throws Exception;
	
	protected void setConstraintsFile(File f) {
		if(logicFile != null && !logicFile.getAbsoluteFile().equals(f.getAbsoluteFile())) // if we already have another constraints file, then issue a warning
			System.err.println("Notice: Previously declared constraints file " + logicFile + " is overridden by " + f);					
		logicFile = f;
	}
	
	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	@Override
	public boolean readDeclaration(String line) throws Exception {
		if(super.readDeclaration(line))
			return true;
		
		// constraints file reference
		if(line.startsWith("constraints")) {
			Pattern pat = Pattern.compile("constraints\\s+([^;\\s]+)\\s*;?");
			Matcher matcher = pat.matcher(line);			
			if(matcher.matches()) {
				String filename = matcher.group(1);
				File f = findReferencedFile(filename);
				if(f == null)
					throw new Exception("Declared constraints file " + filename + " could not be found");					
				setConstraintsFile(f);
				return true;
			}
		}
		
		if(line.startsWith("constraint")) {
			Pattern pat = Pattern.compile("constraint\\s+([^;]+)\\s*;?");
			Matcher matcher = pat.matcher(line);			
			if(matcher.matches()) {
				String s = matcher.group(1);
				try {
					this.addLogicalConstraint(s);
					return true;
				}
				catch(ParseException e) {
					throw new Exception("Could not parse formula: " + s, e);
				}				
			}
		}
		
		return false;
	}
	
	protected abstract void addLogicalConstraint(String s) throws Exception;
}
