/*******************************************************************************
 * Copyright (C) 2006-2012 Dominik Jain.
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
package probcog.srldb.datadict;

import java.util.Map.Entry;

import probcog.srldb.Database;
import probcog.srldb.IRelationArgument;
import probcog.srldb.Item;
import probcog.srldb.Link;
import probcog.srldb.Object;
import probcog.srldb.datadict.domain.AutomaticDomain;
import probcog.srldb.datadict.domain.Domain;

/**
 * Represents a data dictionary that is built up automatically based on
 * the data that is observed.
 * @author Dominik Jain
 */
public class AutomaticDataDictionary extends DataDictionary {

	private static final long serialVersionUID = 1L;

	public AutomaticDataDictionary() {
		super();		
	}
	
	@Override
	public void onCommitObject(Object o) throws DDException {
		// add the object type to the data dictionary if it isn't contained yet
		DDObject ddo = this.getObject(o.objType());
		if(ddo == null) 
			addObject(ddo = new DDObject(o.objType()));
		// process the attributes
		onCommitItemAttributes(o, ddo);
	}	
	
	protected void onCommitItemAttributes(Item item, DDItem ddi) throws DDException {
		// add all attributes that aren't contained yet as attributes with automatic domains
		// and extend all automatic attribute domains
		for(Entry<String,String> e : item.getAttributes().entrySet()) {
			String attrName = e.getKey();
			DDAttribute dda = this.getAttribute(attrName);
			if(dda == null) {
				AutomaticDomain dom = new AutomaticDomain("dom" + Database.upperCaseString(attrName));
				addAttribute(dda = new DDAttribute(attrName, dom));
				ddi.addAttribute(dda);
				dom.addValue(e.getValue());
			}
			else {				
				Domain<?> dom = dda.getDomain();
				if(dom instanceof AutomaticDomain)
					((AutomaticDomain)dom).addValue(e.getValue());
			}
		}		
	}
	
	@Override
	public void onCommitLink(Link link) throws DDException {
		DDRelation ddlink = getRelation(link.getName());
		if(ddlink == null) { // this relation is not yet in the data dictionary
			// get a data dictionary definition for each argument
			IRelationArgument[] args = link.getArguments();
			IDDRelationArgument[] ddArgs = new IDDRelationArgument[args.length];
			int i = 0;
			for(IRelationArgument arg : args) {
				// if it's an object, check if we have a definition for that type of object
				if(arg instanceof Object) {
					Object obj = (Object) arg;
					ddArgs[i] = getObject(obj.objType());
				}
				// it's a constant (which is treated as an attribute value such that its domain can be collected and written appropriately)
				else {
					// add an attribute with an AutomaticDomain if necessary
					String domName = "dom" + Database.upperCaseString(link.getName()) + i;
					DDAttribute ddattr = this.attributes.get(domName);
					if(ddattr == null) {				
						DDConstantArgument ddconst = new DDConstantArgument(link.getName() + i, new AutomaticDomain(domName)); 
						ddArgs[i] = ddconst;
						addAttribute(ddconst);
					}
				}
				i++;
			}
			// create the relation definition and add it to the dictionary
			ddlink = new DDRelation(link.getName(), ddArgs);  
			this.addRelation(ddlink);
		}
		// check the link's attributes (and extend their domains if necessary)
		onCommitItemAttributes(link, ddlink);
		// check for constant arguments (and extend the corresponding domains if necessary)
		int i = 0;
		for(IDDRelationArgument argtype : ddlink.getArguments()) {
			if(argtype instanceof DDConstantArgument) {
				Domain<?> dom = getDomain(argtype.getDomainName());
				// extend the domain if applicable
				IRelationArgument arg = link.getArguments()[i];
				String value = arg.getConstantName();					
				if(!dom.containsString(value)) {
					if(dom instanceof AutomaticDomain) {
						((AutomaticDomain)dom).addValue(value);
					}
					else
						throw new DDException("Argument " + (i+1) + " of " + link.toString() + " is invalid; not in domain " + dom.getName());
				}
			}
			i++;
		}
	}
}
