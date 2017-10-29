from jyimportlib import importjar, importbin, importdir
importbin()
importjar("srldb.jar")
importjar("tumutils.jar")
importjar("weka_fipm.jar")
#importdir("../WEKA/bin")

from java.util import Vector, HashMap
from java.lang import String, Double
import jarray
from weka.classifiers.trees import J48, DecisionStump, RandomForest
from weka.classifiers.rules import OneR
from weka.classifiers.functions import SMO;
from weka.classifiers.trees.j48 import Rule;
from weka.classifiers.meta import MultiBoostAB, AdaBoostM1, RandomCommittee, Bagging
from weka.core import Attribute, FastVector, Instance, Instances

class WekaClassifier(object):
	def __init__(self, numericAttributes = None):		
		self.attName2Domain = {}
		self.numericAttributes = []
		if numericAttributes is not None:
			self.numericAttributes = list(numericAttributes)
		self.instances = []
		
	
	def setDomain(self, attName, domain):
		domain = set(domain) #remove duplicates, make sure it is set if things are added later on
		self.attName2Domain[attName] = domain
	
	def setNumericAttribute(self, attName):
		self.numericAttributes.append(attName)
	
	def addInstance(self, i):
		for (attName, value) in i.iteritems():
			if attName not in self.numericAttributes:
				if attName not in self.attName2Domain:
					self.attName2Domain[attName] = set([value])
				else:
					self.attName2Domain[attName].add(value)
		self.instances.append(i)

	def _makeInstance(self, i, instances=None):
		inst = Instance(len(i))
		if instances is not None:
			inst.setDataset(instances)
		for (attName, value) in i.iteritems():
			if attName in self.numericAttributes: value = Double(value)
			else: value = String(value)
			attr = self.attName2Obj[attName]
			#print self.attName2Domain
			#print "attName, value", attName, value
			inst.setValue(attr, value)
		return inst

	def _getInstances(self, classAttr):
		# create attributes
		self.classAttr = classAttr
		attName2Obj = {}
		attVector = FastVector()
		for attName in self.numericAttributes:
			attr = Attribute(attName)
			attVector.addElement(attr)
			attName2Obj[attName] = attr
		for (attName, domain) in self.attName2Domain.iteritems():
			vDomain = FastVector(len(domain))
			for v in domain:
				#print v
				vDomain.addElement(String(str(v)))
			attr = Attribute(attName, vDomain)
			attVector.addElement(attr)
			attName2Obj[attName] = attr
		self.attName2Obj = attName2Obj
		
		# create Instances object
		instances = Instances("instances", attVector, len(self.instances))
		for i in self.instances:
			inst = self._makeInstance(i)
			instances.add(inst)
			
		instances.setClass(attName2Obj[classAttr])
		return instances

	def classify(self, instance):
		if type(instance) == dict: instance = self._makeInstance(instance, self.instances)		
		return self.attName2Obj[self.classAttr].value(int(self.classifier.classifyInstance(instance)))

class DecisionTree(WekaClassifier):
	def __init__(self, numericAttributes=None):
		WekaClassifier.__init__(self, numericAttributes)
		
	def learn(self, classAttr, unpruned=False, minNumObj=2):
		self.instances = self._getInstances(classAttr)		
		j48 = J48()
		j48.setUnpruned(unpruned)
		j48.setMinNumObj(minNumObj);
		#self.j48.setConfidenceFactor(1.0)
		j48.buildClassifier(self.instances)
		self.classifier = j48	
		print j48
        
class MultiBoost(WekaClassifier):
    def __init__(self, numericAttributes=None):
        WekaClassifier.__init__(self, numericAttributes)
        
    def learn(self, classAttr, unpruned=False, minNumObj=2):
        self.instances = self._getInstances(classAttr)        
        #j48 = J48()
        #j48.setUnpruned(unpruned)
        #j48.setMinNumObj(minNumObj);
        
        classifier =  MultiBoostAB()
        #classifier.setDebug(true);
        #classifier.setClassifier(j48)
        
        #self.j48.setConfidenceFactor(1.0)
        classifier.buildClassifier(self.instances)
        self.classifier = classifier    
        print classifier     
        
class AdaBoost(WekaClassifier):
	def __init__(self, numericAttributes=None):
	    WekaClassifier.__init__(self, numericAttributes)
	    
	def learn(self, classAttr, unpruned=False, minNumObj=2):
	    self.instances = self._getInstances(classAttr)        
	    tree = J48() # DecisionStump() #J48
	    
	    classifier =  AdaBoostM1()
	    #classifier.setDebug(true);
	    classifier.setClassifier(tree)
	    #classifier.setNumIterations(50) 
	    
	    
	    #self.j48.setConfidenceFactor(1.0)
	    classifier.buildClassifier(self.instances)
	    self.classifier = classifier   
	    
	    print "numIterations", classifier.getNumIterations() 
	    print classifier     
	    
class RandomForest(WekaClassifier):
	def __init__(self, numericAttributes=None):
	    WekaClassifier.__init__(self, numericAttributes)
	    
	def learn(self, classAttr, unpruned=False, minNumObj=2):
		self.instances = self._getInstances(classAttr)  
		j48 = J48()
		j48.setUnpruned(unpruned)
		j48.setMinNumObj(minNumObj);
		classifier = Bagging() #RandomForest()
		classifier.setClassifier(j48)
		classifier.buildClassifier(self.instances)
		self.classifier = classifier   
	    
	    
		print classifier 
		
class SVM(WekaClassifier):
	def __init__(self, numericAttributes=None):
		WekaClassifier.__init__(self, numericAttributes)
	
	def learn(self, classAttr):
		self.instances = self._getInstances(classAttr)		
		svm = SMO()
		#svm.setUseRBF(True)
		svm.buildClassifier(self.instances)
		self.classifier = svm		
		

if __name__=='__main__':
	inst = [		
		{"sex":"m", "subject":"CS"},
		{"sex":"f", "subject":"Phil"},
		{"sex":"m", "subject":"CS"}
	]
	test = [
		{"sex":"f", "subject":"Phil"},
		{"sex":"m", "subject":"CS"}
	]
	numericAttributes=[]
	classAttr = "subject"
	
	tree = DecisionTree(numericAttributes)
	for i in inst: tree.addInstance(i)
	tree.learn(classAttr, unpruned=True, minNumObj=0)
	
	svm = SVM(numericAttributes)
	for i in inst: svm.addInstance(i)
	svm.learn(classAttr)
	
	ada = AdaBoost(numericAttributes)
	for i in inst: ada.addInstance(i)
	ada.learn(classAttr)
	
	forest = RandomForest(numericAttributes)
	for i in inst: forest.addInstance(i)
	forest.learn(classAttr)
	
	for j,model in enumerate((tree, svm, ada, forest)):
		print "\nmodel", j
		for i in test:
			#del i[classAttr]
			print model.classify(i)
	