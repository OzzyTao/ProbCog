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

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

import probcog.srldb.Database;
import probcog.srldb.IdentifierNamer;


public abstract class DDItem implements Serializable {
	private static final long serialVersionUID = 1L;
	protected String name;
	protected HashMap<String, DDAttribute> attributes;
	
	public DDItem(String name) {
		this.name = name;
		attributes = new HashMap<String, DDAttribute>();
	}
	
	public String getName() {
		return name;
	}
	
	public void addAttribute(DDAttribute attr) throws DDException {
		attr.setOwner(this);
		attributes.put(attr.getName(), attr);
	}
	
	/**
	 * @return a hashmap containing all attributes for this item with attribute names as keys
	 */
	public HashMap<String, DDAttribute> getAttributes() {
		return attributes;
	}	
	
	public abstract boolean isObject();
	
	public void discardAllAttributesExcept(String[] keep) {
		for(Entry<String,DDAttribute> entry : attributes.entrySet()) {
			boolean discard = true;
			for(int i = 0; i < keep.length; i++)
				if(entry.getKey().equals(keep[i])) {
					discard = false;
					break;
				}	
			if(discard)
				entry.getValue().discard();
		}
	}
	
	public void MLNprintUnitClauses(IdentifierNamer idNamer, PrintStream out) {	
		for(DDAttribute attr : attributes.values()) {
			if(attr.isDiscarded() || attr.isBoolean())
				continue;
			String idCategory = attr.getName();
			idNamer.resetCounts();
			out.print(Database.stdPredicateName(attr.getName()) + "(" + idNamer.getCountedShortIdentifier(idCategory, this.getName()));
			out.print(", +");
			out.println(idNamer.getCountedShortIdentifier(attr.getName(), attr.getDomain().getName()) + ")");
		}
	}
	
	protected void outputAttributeList(PrintStream out) {
		Collection<DDAttribute> attributes = getAttributes().values();
		if(attributes.isEmpty())
			return;
		out.print(getName() + "_attr_names = [");
		int i = 0;
		for(DDAttribute attrib : attributes) {
			if(attrib.isDiscarded())
				continue;
			if(i++ > 0)
				out.print(", ");
			out.print("'" + Database.stdAttribName(attrib.getName()) + "'"); 
		}
		out.println("]");
	}
	
	protected void MLNprintAttributePredicateDeclaration(DDAttribute attr, String objectOfAttribute, IdentifierNamer idNamer, PrintStream out) {
		if(attr.isDiscarded())
			return;
		out.print(Database.stdPredicateName(attr.getName()) + "(" + objectOfAttribute);
		if(attr.isBoolean()) {
			out.println(")");
			return;
		}
		out.print(", ");
		out.print(idNamer.getLongIdentifier("domain", Database.stdDomainName(attr.getDomain().getName())));		
		out.println("!)");
	}

	protected void BLNprintAttributePredicateDeclaration(DDAttribute attr, String objectOfAttribute, IdentifierNamer idNamer, PrintStream out) {
		if(attr.isDiscarded())
			return;
		out.print("random ");
		if(attr.isBoolean())
			out.print("boolean");
		else
			out.print(idNamer.getLongIdentifier("domain", Database.stdDomainName(attr.getDomain().getName())));
		out.println(" " + Database.stdPredicateName(attr.getName()) + "(" + objectOfAttribute + ");");
	}
}
