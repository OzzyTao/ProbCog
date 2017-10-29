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
package probcog.srl.mln.inference;

import java.util.ArrayList;
import java.util.Vector;
import java.util.regex.Pattern;

import probcog.inference.IParameterHandler;
import probcog.inference.ParameterHandler;
import probcog.logging.PrintLogger;
import probcog.logging.VerbosePrinter;
import probcog.logic.GroundAtom;
import probcog.srl.mln.MarkovRandomField;


/**
 * Base class for MLN inference methods.
 * @author Dominik Jain
 */
public abstract class InferenceAlgorithm implements IParameterHandler, VerbosePrinter {
	
	protected MarkovRandomField mrf;
	protected ParameterHandler paramHandler;
	protected boolean debug = false;	
	protected boolean verbose = true;
	protected int maxSteps = 5000;
	protected PrintLogger log;
	
	public InferenceAlgorithm(MarkovRandomField mrf) throws Exception {
		this.mrf = mrf;
		paramHandler = new ParameterHandler(this);
		paramHandler.add("debug", "setDebugMode");
		paramHandler.add("verbose", "setVerbose");
		paramHandler.add("maxSteps", "setMaxSteps");
		this.log = new PrintLogger(this);
	}
	
	public void setDebugMode(boolean active) {
		debug = active;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setMaxSteps(int maxSteps) {
		this.maxSteps = maxSteps;
	}
	
	public abstract double getResult(GroundAtom ga);	
	
	public ArrayList<InferenceResult> getResults(Iterable<String> queries) {
		// generate patterns
		Vector<Pattern> patterns = new Vector<Pattern>();
		int numQueries = 0;
		for(String query : queries) {
			++numQueries;
			String p = query;
			p = Pattern.compile("([,\\(])([a-z][^,\\)]*)").matcher(p).replaceAll("$1.*?");
			p = p.replace("(", "\\(").replace(")", "\\)") + ".*";			
			patterns.add(Pattern.compile(p));
		}
		// check all ground variables for matches
		// TODO This should be done more efficiently by explicitly grounding the requested nodes instead of using pattern matchers
		ArrayList<InferenceResult> results = new ArrayList<InferenceResult>();
		int numRes = 0;
		for(GroundAtom ga : mrf.getWorldVariables())
			for(Pattern pattern : patterns)				
				if(pattern.matcher(ga.toString()).matches()) {
					results.add(new InferenceResult(ga, getResult(ga)));
					numRes++;
					break;
				}
		if(numRes == 0 && numQueries > 0)
			log.warn("Warning: None of the queries could be matched to a variable.");
		return results;
	}
	
	public abstract ArrayList<InferenceResult> infer(Iterable<String> queries) throws Exception;
	
	public String getAlgorithmName() {
		return this.getClass().getSimpleName();
	}

	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}
	
	@Override
	public boolean getVerboseMode() {
		return verbose;
	}

	@Override
	public boolean getDebugMode() {
		return debug;
	}
}