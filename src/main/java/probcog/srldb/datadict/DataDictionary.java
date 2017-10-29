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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import probcog.bayesnets.core.BeliefNetworkEx;
import probcog.srldb.ConstantArgument;
import probcog.srldb.Database;
import probcog.srldb.IRelationArgument;
import probcog.srldb.IdentifierNamer;
import probcog.srldb.Item;
import probcog.srldb.Link;
import probcog.srldb.Object;
import probcog.srldb.datadict.domain.BooleanDomain;
import probcog.srldb.datadict.domain.Domain;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;

/**
 * Represents a data dictionary for a relational database.
 * @author Dominik Jain
 */
public class DataDictionary implements java.io.Serializable {

	private static final long serialVersionUID = 1L;
	protected HashMap<String, DDObject> objects;
	protected HashMap<String, DDRelation> relations;
	protected HashMap<String, DDAttribute> attributes;
	/**
	 * !!! this map is not guaranteed to contain all relevant entries (is not certain to be in sync)
	 */
	protected HashMap<String, Domain<?>> domains;
	
	public DataDictionary() {
		objects = new HashMap<String, DDObject>();
		attributes = new HashMap<String, DDAttribute>();
		relations = new HashMap<String, DDRelation>();
		domains = new HashMap<String, Domain<?>>();
	}
	
	public void addObject(DDObject obj) throws DDException {
		objects.put(obj.getName(), obj);
		addAttributes(obj);
	}

	/**
	 * adds the attributes of a given item (object or relation) to the global list of attributes
	 * @param item
	 * @throws DDException
	 */
	protected void addAttributes(DDItem item) throws DDException {
		for(DDAttribute attr : item.getAttributes().values()) {
			addAttribute(attr);
		}
	}	
	
	/**
	 * adds the given attribute to the data dictionary. This function should not be called
	 * unless an attribute is added to an item (object or link) after the item was added to
	 * the data dictionary, as all of an item's attributes are added automatically when 
	 * addObject or addRelation is called.
	 * @param attr
	 * @throws DDException if an attribute with the same name was already in the data dictionary
	 */
	public void addAttribute(DDAttribute attr) throws DDException {
		if(attributes.containsKey(attr.getName())) {				
			throw new DDException("Duplicate attribute " + attr.getName() + "; already defined for item " + attr.getOwner().getName());
		}
		attributes.put(attr.getName(), attr);
		domains.put(attr.getDomain().getName(), attr.getDomain());
	}
	
	public void addRelation(DDRelation rel) throws DDException {
		relations.put(rel.getName(), rel);
		addAttributes(rel);
		//System.out.println("datadict now contains " + relations.size());
	}
	
	public Collection<DDAttribute> getAttributes() {
		return attributes.values();
	}
	
	public DDObject getObject(String name) throws DDException {
		return objects.get(name);
	}

	public DDRelation getRelation(String name) {
		return relations.get(name);
	}
	
	public DDAttribute getAttribute(String name) throws DDException {
		return attributes.get(name);
	}
	
	public Domain<?> getDomain(String name) {
		return domains.get(name);
	}

	public Collection<DDObject> getObjects() {
		return objects.values();
	}
	
	public Collection<DDRelation> getRelations() {
		return relations.values();
	}
	
	protected class DomainData {
		public Domain<?> domain;
		public Vector<DDAttribute> occurrences = new Vector<DDAttribute>();
		public String[] values;
		public boolean wasReplaced = false;
		public DomainData(Domain<?> domain) {
			this.domain = domain;
			values = domain.getValues();
		}
	}
	
	/**
	 * Checks the data dictionary for overlapping domains. Each value must be unique (i.e.
	 * in two (or more) domains, all references to the second domain are replaced by the first.
	 * It is assumed that the first domain can be substituted - no merging takes place.
	 * Moreover, it is ensured that attribute names do not coincide with link names - 
	 * as both are eventually used as predicate symbols in the context of MLNs. 
	 */ 
	public void check() throws DDException {
		// get a hash map of all domains used 
		HashMap<String, DomainData> domains = new HashMap<String,DomainData>();
		for(DDAttribute attrib : this.attributes.values()) {
			Domain<?> domain = attrib.getDomain();
			String domName = domain.getName();
			DomainData dd;
			if(!domains.containsKey(domName))
				domains.put(domName, dd = new DomainData(domain));
			else
				dd = domains.get(domName);
			dd.occurrences.add(attrib);
		}
		
		// check attribute domain 
		DomainData[] dd = new DomainData[domains.size()];
		domains.values().toArray(dd);
		domains = null;
		for(int i = 0; i < dd.length; i++) {			
			if(dd[i].wasReplaced) continue;
			
			// check whether the domain is actually boolean
			if(dd[i].domain.isBoolean()) {
				for(DDAttribute attrib : dd[i].occurrences)
					attrib.setDomain(BooleanDomain.getInstance());
				dd[i].wasReplaced = true;
				continue;
			}
			
			// check all of the following domains for overlaps 
			for(int j = i+1; j < dd.length; j++) {
				if(dd[j].wasReplaced) continue;
				// check whether any of the values in the first domain is in the other domain...
				for(int k = 0; k < dd[i].values.length; k++) {
					// ...and if so, replace all occurrences of the second domain with the first 
					if(dd[j].domain.containsString(dd[i].values[k])) {
						System.err.println("Warning: domain " + dd[i].domain.getName() + " already contains value '" + dd[i].values[k] + "' of domain " + dd[j].domain.getName() + "; replacing all occurrences of " + dd[j].domain.getName() + "!");						
						for(DDAttribute attrib : dd[j].occurrences)
							attrib.setDomain(dd[i].domain);							
						dd[j].wasReplaced = true;
						/*// if it's an automatic domain, merge
						if(dd[i].domain instanceof AutomaticDomain) {
							AutomaticDomain adom = (AutomaticDomain) dd[i].domain;
							for(int l = 0; l < dd[j].values.length; l++)
								adom.addValue(dd[j].values[l]);
						}*/
						break;
					}
				}
			}
		}
		
		// ensure that attribute names do not coincide with link names
		Set<String> attrNames = this.attributes.keySet();
		Set<String> linkNames = new HashSet<String>(this.relations.keySet());
		linkNames.retainAll(attrNames);
		if(!linkNames.isEmpty()) {
			throw new DDException("Error: Duplicate predicate name(s); the name(s) " + linkNames.toString() + " cannot be used for attributes and links simultaneously!");
		}
	}
	
	/**
	 * outputs an attribute list for each type of object and relation 
	 * @param out the stream to write to
	 */
	public void outputAttributeLists(PrintStream out) {
		for(DDObject obj : getObjects()) {
			obj.outputAttributeList(out);		
		}
		for(DDRelation rel : getRelations()) {
			rel.outputAttributeList(out);
		}
	}
	
	/**
	 * outputs a comma-separated list of all attribute names, regardless of the item to which the attributes belong
	 * @param out the stream to write to
	 */
	public void outputAttributeList(PrintStream out) {
		int i = 0;
		for(DDAttribute attr : this.attributes.values()) {
			if(attr.isDiscarded())
				continue;
			if(i++ > 0)
				out.print(",");
			out.print(Database.stdAttribName(attr.getName()));
		}		
	}
	
	public void checkObject(Object obj) throws DDException {
		DDObject ddobj = getObject(obj.objType());
		if(ddobj == null)
			throw new DDException("Unknown object type " + obj.objType() + "; not in data dictionary!");
		checkItemAttributes(obj, ddobj);
	}
	
	@SuppressWarnings("unchecked")
	public void checkLink(Link link) throws DDException, Exception {
		// check existence of corresponding link type in data dictionary
		DDRelation ddlink = getRelation(link.getName());
		if(ddlink == null)
			throw new DDException("Unknown relation " + link.getName() + "; not in data dictionary!");
		// check number of arguments
		if(link.getArguments().length != ddlink.getArguments().length)
			throw new DDException("The link " + link.toString() + " has the wrong number of parameters!");
		// check argument types
		int i = 0;
		for(IRelationArgument arg : link.getArguments()) {
			IDDRelationArgument argtype = ddlink.getArguments()[i];
			if(arg instanceof Object) {
				Object o = (Object)arg;
				if(o.objType() != argtype.getDomainName())
					throw new DDException(String.format("Type mismatch for the %dth argument of %s; should be %s!", i+1, link.toString(), argtype.getDomainName()));
			}
			else {
				if(!(arg instanceof ConstantArgument)) {
					throw new DDException(String.format("Type mismatch for argument %d of %s; expected a constant argument!", i+1, link.toString()));
				}
				DDConstantArgument ddconst = (DDConstantArgument) argtype;
				if(!((Domain<String>)ddconst.getDomain()).contains(arg.getConstantName()))
					throw new DDException(String.format("Domain of argument %d of %s does not contain %s!", i+1, link.toString(), arg.getConstantName()));
			}
			i++;		
		}
		// check attributes
		checkItemAttributes(link, ddlink);
	}
	
	/**
	 * checks compatibility of an item's attributes with the corresponding data dictionary item
	 * @param item 
	 * @param ddItem 
	 * @throws DDException
	 */
	protected void checkItemAttributes(Item item, DDItem ddItem) throws DDException {
		Set<String> allowedAttributes = ddItem.getAttributes().keySet();
		for(Entry<String,String> attr : item.getAttributes().entrySet()) {
			String attribName = attr.getKey();
			if(!allowedAttributes.contains(attribName))
				throw new DDException("Undefined attribute '" + attribName + "' for item type '" + ddItem.getName() + "' or the attribute was applied to more than one type of object.");
			if(getAttribute(attribName).isDiscarded())
				continue;
			Domain<?> domain = ddItem.getAttributes().get(attribName).getDomain();
			String value = attr.getValue();
			if(!domain.containsString(value))
				throw new DDException("Invalid value " + value + " for attribute " + attribName + " of item " + ddItem.getName() + "; not in domain " + domain.getName()); 
		}		
	}

	public void onCommitObject(Object o) throws DDException {}
	
	public void onCommitLink(Link l) throws DDException {}
	
	/**
	 * outputs the basic MLN for this data dictionary, which contains domain definitions and predicate declarations   
	 * @param out the stream to write to
	 */
	public void writeBasicMLN(PrintStream out) {
		DataDictionary datadict = this;
		out.println("// Markov Logic Network\n\n");
		IdentifierNamer idNamer = new IdentifierNamer(datadict);
		// domains
		out.println("// ***************\n// domains\n// ***************\n");
		HashSet<String> printedDomains = new HashSet<String>(); // the names of domains that have already been printed
		// - check all attributes for finite domains
		for(DDAttribute attrib : datadict.getAttributes()) {
			if(attrib.isDiscarded())
				continue;
			Domain<?> domain = attrib.getDomain();
			if(domain == null || attrib.isBoolean() || !domain.isFinite()) // boolean domains aren't handled because a boolean attribute value is not specified as a constant but rather using negation of the entire predicate
				continue;
			// we have a finite domain -> output this domain if it hasn't already been printed
			String name = domain.getName();
			if(!printedDomains.contains(name)) {
				// check if the domain is empty
				String[] values = domain.getValues();
				if(values.length == 0) {
					System.err.println("Warning: Domain " + domain.getName() + " is empty!");
					continue;
				}
				// print the domain name
				String domIdentifier = idNamer.getLongIdentifier("domain", domain.getName());
				out.print(domIdentifier + " = {");
				// print the values (must start with upper-case letter)				
				for(int i = 0; i < values.length; i++) {
					if(i > 0)
						out.print(", ");
					out.print(Database.stdAttribStringValue(values[i]));				
				}
				out.println("}");
				printedDomains.add(name);
			}			
		}
		// predicate declarations
		out.println("\n\n// *************************\n// predicate declarations\n// *************************\n");
		for(DDObject obj : datadict.getObjects()) {
			obj.MLNprintPredicateDeclarations(idNamer, out);			
		}
		out.println("// Relations");
		for(DDRelation rel : datadict.getRelations()) {
			rel.MLNprintPredicateDeclarations(idNamer, out);
		}	
		// rules
		out.println("\n\n// ******************\n// rules\n// ******************\n");
		/*
		for(DDObject obj : datadict.getObjects()) {
			obj.MLNprintRules(idNamer, out);
		}		
		out.println("\n// mutual exclusiveness and exhaustiveness: relations");
		for(DDRelation rel : datadict.getRelations()) {
			rel.MLNprintRules(idNamer, out);
		}*/
		// unit clauses
		out.println("\n// unit clauses");
		for(DDObject obj : datadict.getObjects()) {
			obj.MLNprintUnitClauses(idNamer, out);
		}
		for(DDRelation rel : datadict.getRelations()) {
			rel.MLNprintUnitClauses(idNamer, out);
		}
	}	

	/**
	 * outputs the basic BLOG model for this data dictionary, which contains domain definitions and predicate declarations   
	 * @param out the stream to write to
	 */
	public void writeBasicBLOGModel(PrintStream out) {
		out.println("// ABL Model\n\n");
		IdentifierNamer idNamer = new IdentifierNamer(this);
		
		// object types
		out.println("// ***************\n// object types\n// ***************\n");		
		for(DDObject ddo : this.getObjects()) 
			out.printf("type %s;\n", idNamer.getLongIdentifier("domain", ddo.getDomainName()));
		
		// fixed domains
		out.println("\n// ***************\n// domains\n// ***************\n");
		HashSet<String> handledDomainTypes = new HashSet<String>();
		for(DDAttribute dda : this.getAttributes()) {
			Domain<?> dom = dda.getDomain();
			
			if(dda.isDiscarded() || dda.isBoolean() || handledDomainTypes.contains(dom))
				continue;
			
			handledDomainTypes.add(dom.getName());
			
			// print type declaration
			String domIdentifier = idNamer.getLongIdentifier("domain", dom.getName());
			out.printf("type %s;\n", domIdentifier);
			
			if(dom == null || !dom.isFinite())
				continue;
			
			// check if the domain is empty
			String[] values = dom.getValues();
			if(values.length == 0) {
				System.err.println("Warning: Domain " + dom.getName() + " is empty!");
				continue;
			}
			// print the domain
			out.print("guaranteed " + domIdentifier + " ");
			for(int i = 0; i < values.length; i++) {
				if(i > 0)
					out.print(", ");
				out.print(Database.stdAttribStringValue(values[i]));				
			}
			out.println(";");
		}
		
		// predicate declarations
		out.println("\n\n// *************************\n// function/predicate declarations\n// *************************\n");
		for(DDObject obj : this.getObjects()) {
			obj.BLNprintPredicateDeclarations(idNamer, out);			
		}
		out.println("// Relations");
		for(DDRelation rel : this.getRelations()) {
			rel.BLNprintPredicateDeclarations(idNamer, out);
		}
	}	

	
	public static class BLNStructure {
		public BeliefNetworkEx bn;
		protected HashMap<java.lang.Object,BeliefNode> dd2node;
		
		public BLNStructure(BeliefNetworkEx bn, HashMap<java.lang.Object,BeliefNode> dd2node) {
			this.bn = bn;
			this.dd2node = dd2node;			
		}
		
		public BeliefNode getNode(DDAttribute attr) {
			return dd2node.get(attr);
		}
		
		public BeliefNode getNode(DDRelation rel) {
			return dd2node.get(rel);
		}
		
		public void connect(java.lang.Object ddAttributeOrRelation_Parent, java.lang.Object ddAttributeOrRelation_Child) {
			bn.bn.connect(dd2node.get(ddAttributeOrRelation_Parent), dd2node.get(ddAttributeOrRelation_Child));
		}
		
		public void disconnect(java.lang.Object ddAttributeOrRelation_Parent, java.lang.Object ddAttributeOrRelation_Child) {
			bn.bn.disconnect(dd2node.get(ddAttributeOrRelation_Parent), dd2node.get(ddAttributeOrRelation_Child));
		}
	}
	
	public BLNStructure createBasicBLNStructure() {
		BeliefNetworkEx bn = new BeliefNetworkEx();
		HashMap<java.lang.Object, BeliefNode> dd2node = new HashMap<java.lang.Object, BeliefNode>();
		IdentifierNamer namer = new IdentifierNamer(this);
		// attribute nodes
		for(DDAttribute attr : this.getAttributes()) {
			if(attr.isDiscarded())
				continue;
			String nodeName = String.format("%s(%s)", attr.getName(), namer.getShortIdentifier("object", attr.getOwner().getName()));
			dd2node.put(attr, bn.addNode(nodeName));
		}
		// relation nodes
		for(probcog.srldb.datadict.DDRelation rel : this.getRelations()) {
			StringBuffer nodeName = new StringBuffer(rel.getName() + "(");
			IDDRelationArgument[] relargs = rel.getArguments();
			for(int i = 0; i < relargs.length; i++) {
				if(i > 0)
					nodeName.append(',');
				nodeName.append(namer.getShortIdentifier(rel.getName(), relargs[i].getDomainName()));
			}
			nodeName.append(')');
			dd2node.put(rel, bn.addNode(nodeName.toString()));
		}
		return new BLNStructure(bn, dd2node);
	}
	
	public String toString() {		
		StringBuffer sb = new StringBuffer("DataDictionary:\n");
		for(DDObject ddo : this.objects.values()) {
			sb.append(ddo);
			sb.append('\n');
		}
		for(DDRelation ddr : this.relations.values()) {
			sb.append(ddr);
			sb.append('\n');
		}
		return sb.toString();
	}
	
	/**
	 * cleans up stale domain references
	 */
	public void cleanUp() {
		domains.clear();
		for(DDAttribute attr : attributes.values())
			domains.put(attr.getDomain().getName(), attr.getDomain());
	}
}
