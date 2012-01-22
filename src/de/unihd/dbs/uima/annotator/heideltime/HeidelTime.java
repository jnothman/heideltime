/*
 * HeidelTime.java
 *
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, Heidelberg University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU General Public License.
 *
 * author: Jannik Strötgen
 * email:  stroetgen@uni-hd.de
 *
 * HeidelTime is a multilingual, cross-domain temporal tagger.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */

package de.unihd.dbs.uima.annotator.heideltime;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Calendar;

import org.apache.uima.UIMAFramework;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.cleartk.timeml.type.DocumentCreationTime;
import org.cleartk.timeml.type.Event;
import org.cleartk.timeml.type.Time;
import org.cleartk.token.type.Token;
import org.cleartk.token.type.Sentence;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.TypeSystemDescriptionFactory;
import org.uimafit.util.JCasUtil;
import de.unihd.dbs.uima.annotator.heideltime.IdGenerator;
import de.unihd.dbs.uima.annotator.heideltime.TimexRuleMatcher;
import de.unihd.dbs.uima.types.heideltime.Timex3;


/**
 * HeidelTime finds temporal expressions and normalizes them according to the TIMEX3
 * TimeML annotation standard.
 *
 * @author Jannik
 *
 */
public class HeidelTime extends JCasAnnotator_ImplBase {

	// TOOL NAME (may be used as componentId)
	private String toolname = "de.unihd.dbs.uima.annoator.heideltime";

	// COUNTER (how many timexes added to CAS? (finally)
	int timex_counter        = 0;
	int timex_counter_global = 0;

	// COUNTER FOR TIMEX IDS
	class TimexIdGenerator implements IdGenerator {
		int counter = 0;
		public String next() {
			return "t" + counter++;
		}
	}
	IdGenerator idGenerator = new TimexIdGenerator();

	// PATTERNS TO READ RESOURCES "RULES" AND "NORMALIZATION"
	Pattern paReadNormalizations = Pattern.compile("\"(.*?)\",\"(.*?)\"");

	// STORE PATTERNS AND NORMALIZATIONS
	HashMap<String, HashMap<String,String>> hmAllNormalization = new HashMap<String, HashMap<String,String>>();
	TreeMap<String, String> hmAllRePattern                     = new TreeMap<String, String>();

	// GLOBAL ACCESS TO SOME NORMALIZATION MAPPINGS (set internally)
	HashMap<String, String> normDayInWeek        = new HashMap<String, String>();
	HashMap<String, String> normNumber           = new HashMap<String, String>();
	HashMap<String, String> normMonthName        = new HashMap<String, String>();
	HashMap<String, String> normMonthInSeason    = new HashMap<String, String>();
	HashMap<String, String> normMonthInQuarter   = new HashMap<String, String>();

	TimexRuleMatcher rmDate;
	TimexRuleMatcher rmTime;
	TimexRuleMatcher rmDuration;
	TimexRuleMatcher rmSet;
	FullSpecifier fullSpecifier;

	// INPUT PARAMETER HANDLING WITH UIMA
	String PARAM_LANGUAGE         = "Language_english_german";
	String PARAM_TYPE_TO_PROCESS  = "Type_news_narratives";
	String language       = "english";
	String typeToProcess  = "news";

	// INPUT PARAMETER HANDLING WITH UIMA (which types shall be extracted)
	String PARAM_DATE      = "Date";
	String PARAM_TIME      = "Time";
	String PARAM_DURATION  = "Duration";
	String PARAM_SET       = "Set";
	Boolean find_dates     = true;
	Boolean find_times     = true;
	Boolean find_durations = true;
	Boolean find_sets      = true;

	// FOR DEBUGGING PURPOSES (IF FALSE)
	Boolean deleteOverlapped = true;
	
	public static AnalysisEngineDescription getDescription() throws ResourceInitializationException {
		// FIXME: No absolute paths!
		return AnalysisEngineFactory.createPrimitiveDescription(HeidelTime.class,
				TypeSystemDescriptionFactory.createTypeSystemDescriptionFromPath("/Users/joel/Dev/eclipse-workspace/HeidelTime/desc/type/HeidelTime_TypeSystem.xml"));
	}

	Logger logger;

	/**
	 * @see AnalysisComponent#initialize(UimaContext)
	 */
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		logger = UIMAFramework.getLogger(HeidelTime.class);
	
		/////////////////////////////////
		// DEBUGGING PARAMETER SETTING //
		/////////////////////////////////
		deleteOverlapped = true;
	
		//////////////////////////////////
		// GET CONFIGURATION PARAMETERS //
		//////////////////////////////////
//		language       = (String)  aContext.getConfigParameterValue(PARAM_LANGUAGE);
//		typeToProcess  = (String)  aContext.getConfigParameterValue(PARAM_TYPE_TO_PROCESS);
//		find_dates     = (Boolean) aContext.getConfigParameterValue(PARAM_DATE);
//		find_times     = (Boolean) aContext.getConfigParameterValue(PARAM_TIME);
//		find_durations = (Boolean) aContext.getConfigParameterValue(PARAM_DURATION);
//		find_sets      = (Boolean) aContext.getConfigParameterValue(PARAM_SET);
	
		////////////////////////////////////////////////////////////
		// READ NORMALIZATION RESOURCES FROM FILES AND STORE THEM //
		////////////////////////////////////////////////////////////
		HashMap<String, String> hmResourcesNormalization = readResourcesFromDirectory("normalization");
		for (String which : hmResourcesNormalization.keySet()){
			hmAllNormalization.put(which, new HashMap<String, String>());
		}
		readNormalizationResources(hmResourcesNormalization);
	
		//////////////////////////////////////////////////////
		// READ PATTERN RESOURCES FROM FILES AND STORE THEM //
		//////////////////////////////////////////////////////
		HashMap<String, String> hmResourcesRePattern = readResourcesFromDirectory("repattern");
		for (String which : hmResourcesRePattern.keySet()){
			hmAllRePattern.put(which, "");
		}
		readRePatternResources(hmResourcesRePattern);

		///////////////////////////////////////////////////
		// READ RULE RESOURCES FROM FILES AND STORE THEM //
		///////////////////////////////////////////////////
		HashMap<String, String> hmResourcesRules = readResourcesFromDirectory("rules");
		readRules(hmResourcesRules);
		
		fullSpecifier = new FullSpecifier(hmAllRePattern);
	
		/////////////////////////////
		// PRINT WHAT WILL BE DONE //
		/////////////////////////////
		if (find_dates){
			logger.log(Level.INFO, "Getting Dates...");
		}
		if (find_times){
			logger.log(Level.INFO, "Getting Times...");
		}
		if (find_durations){
			logger.log(Level.INFO, "Getting Durations...");
		}
		if (find_sets){
			logger.log(Level.INFO, "Getting Sets...");
		}
	}

	/**
	 * Reads resource files of the type resourceType from the "used_resources.txt" file and returns a HashMap
	 * containing information to access these resources.
	 * @param resourceType
	 * @return
	 */
	public HashMap<String, String> readResourcesFromDirectory(String resourceType){

		HashMap<String, String> hmResources = new HashMap<String, String>();
	
		BufferedReader br = new BufferedReader(new InputStreamReader (this.getClass().getClassLoader().getResourceAsStream("used_resources.txt")));
		try {
			for ( String line; (line=br.readLine()) != null; ){

				Pattern paResource = Pattern.compile("\\./"+language+"/"+resourceType+"/resources_"+resourceType+"_"+"(.*?)\\.txt");
				for (MatchResult ro : findMatches(paResource, line)){
					String foundResource  = ro.group(1);
					String pathToResource = language+"/"+resourceType+"/resources_"+resourceType+"_"+foundResource+".txt";
					hmResources.put(foundResource, pathToResource);
				}
			}
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		return hmResources;
	}

	/**
	 * Find all the matches of a pattern in a charSequence and return the
	 * results as list.
	 *
	 * @param pattern
	 * @param s
	 * @return
	 */
	public static Iterable<MatchResult> findMatches(Pattern pattern,
			CharSequence s) {
		List<MatchResult> results = new ArrayList<MatchResult>();

		for (Matcher m = pattern.matcher(s); m.find();)
			results.add(m.toMatchResult());

		return results;
	}


	/**
	 * READ THE RULES FROM THE FILES. The files have to be defined in the HashMap hmResourcesRules.
	 * @param hmResourcesRules
	 */
	public void readRules(HashMap<String, String> hmResourcesRules){
		try {
			for (String resource : hmResourcesRules.keySet()) {
				if (!resource.endsWith("rules")) {
					logger.log(Level.WARNING, "Not adding resource unless it ends 'rules': " + resource);
					continue;
				}
				logger.log(Level.INFO, "Adding rule resource: "+resource);
				// timexType is prefix before "rules"
				String timexType = resource.substring(0, resource.length() - 5).toUpperCase();
				TimexRuleMatcher rm = new TimexRuleMatcher(timexType, new InputStreamReader (
						this.getClass().getClassLoader().getResourceAsStream(hmResourcesRules.get(resource))),
						hmAllRePattern, hmAllNormalization);
				if ("DATE".equals(timexType)) {
					rmDate = rm;
				} else if ("TIME".equals(timexType)) {
					rmTime = rm;
				} else if ("DURATION".equals(timexType)) {
					rmDuration = rm;
				} else if ("SET".equals(timexType)) {
					rmSet = rm;
				}
			
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * READ THE REPATTERN FROM THE FILES. The files have to be defined in the HashMap hmResourcesRePattern.
	 * @param hmResourcesRePattern
	 */
	public void readRePatternResources(HashMap<String, String> hmResourcesRePattern){
	
		//////////////////////////////////////
		// READ REGULAR EXPRESSION PATTERNS //
		//////////////////////////////////////
		try {
			for (String resource : hmResourcesRePattern.keySet()) {
				logger.log(Level.INFO, "Adding pattern resource: "+resource);
				// create a buffered reader for every repattern resource file
				BufferedReader in = new BufferedReader(new InputStreamReader
						(this.getClass().getClassLoader().getResourceAsStream(hmResourcesRePattern.get(resource)),"UTF-8"));
				for ( String line; (line=in.readLine()) != null; ){
					if (!(line.startsWith("//"))){
						boolean correctLine = false;
						if (!(line.equals(""))){
							correctLine = true;
							for (String which : hmAllRePattern.keySet()){
								if (resource.equals(which)){
									String devPattern = hmAllRePattern.get(which);
									devPattern = devPattern + "|" + line;
									hmAllRePattern.put(which, devPattern);
								}
							}
						}
						if ((correctLine == false) && (!(line.matches("")))){
							logger.log(Level.WARNING, "Cannot read one of the lines of pattern resource "+resource + "\nLine: "+line);
						}
					}
				}
			}
			////////////////////////////
			// FINALIZE THE REPATTERN //
			////////////////////////////
			for (String which : hmAllRePattern.keySet()){
				finalizeRePattern(which, hmAllRePattern.get(which));
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Pattern containing regular expression is finalized, i.e., created correctly and added to hmAllRePattern.
	 * @param name
	 * @param rePattern
	 */
	public void finalizeRePattern(String name, String rePattern){
		// create correct regular expression
		rePattern = rePattern.replaceFirst("\\|", "");
		rePattern = "(" + rePattern + ")";
		// add rePattern to hmAllRePattern
		hmAllRePattern.put(name, rePattern.replaceAll("\\\\", "\\\\\\\\"));
	}

	/**
	 * Read the resources (of any language) from resource files and
	 * fill the HashMaps used for normalization tasks.
	 * @param hmResourcesNormalization
	 */
	public void readNormalizationResources(HashMap<String, String> hmResourcesNormalization){
		try {
			for (String resource : hmResourcesNormalization.keySet()) {
				logger.log(Level.INFO, "Adding normalization resource: "+resource);
				// create a buffered reader for every normalization resource file
				BufferedReader in = new BufferedReader(new InputStreamReader
						(this.getClass().getClassLoader().getResourceAsStream(hmResourcesNormalization.get(resource)),"UTF-8"));
				for ( String line; (line=in.readLine()) != null; ){
					if (!(line.startsWith("//"))){
						boolean correctLine = false;
						// check each line for the normalization format (defined in paReadNormalizations)
						for (MatchResult r : findMatches(paReadNormalizations, line)){
							correctLine = true;
							String resource_word   = r.group(1);
							String normalized_word = r.group(2);
							for (String which : hmAllNormalization.keySet()){
								if (resource.equals(which)){
									hmAllNormalization.get(which).put(resource_word,normalized_word);
								}
							}
							if ((correctLine == false) && (!(line.matches("")))){
								logger.log(Level.WARNING, "Cannot read one of the lines of normalization resource "+resource + "\nLine: "+line);
							}
						}
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @see JCasAnnotator_ImplBase#process(JCas)
	 */
	public void process(JCas jcas) {

		timex_counter = 0;
	
		////////////////////////////////////////////
		// CHECK SENTENCE BY SENTENCE FOR TIMEXES //
		////////////////////////////////////////////
		FSIterator sentIter = jcas.getAnnotationIndex(Sentence.type).iterator();
		while (sentIter.hasNext()) {
			Sentence s = (Sentence) sentIter.next();
			if (find_dates) {
				timex_counter += rmDate.findTimexes(s, jcas, idGenerator);
			}
			if (find_times) {
				timex_counter += rmTime.findTimexes(s, jcas, idGenerator);
			}
			if (find_durations) {
				timex_counter += rmDuration.findTimexes(s, jcas, idGenerator);
			}
			if (find_sets) {
				timex_counter += rmSet.findTimexes(s, jcas, idGenerator);
			}
		}

		/*
		 * get longest Timex expressions only (if needed)
		 */
		if (deleteOverlapped == true) {
			// could be modified to: get longest TIMEX expressions of one type, only ???
			deleteOverlappedTimexes(jcas);
		}

		/*
		 * specify ambiguous values, e.g.: specific year for date values of
		 * format UNDEF-year-01-01; specific month for values of format UNDEF-last-month
		 */
		fullSpecifier.process(jcas, typeToProcess);
	
		removeInvalids(jcas);

		timex_counter_global = timex_counter_global + timex_counter;
		logger.log(Level.FINE, "Number of Timexes added to CAS: "+timex_counter + "(global: "+timex_counter_global+")");
		saveClearTkFormat(jcas);
	}

    private void saveClearTkFormat(JCas jcas) {
            for (Timex3 heidelTime : JCasUtil.select(jcas, Timex3.class)) {
                    Time clearTime = new Time(jcas);
                    clearTime.setBegin(heidelTime.getBegin());
                    clearTime.setEnd(heidelTime.getEnd());
                    clearTime.setFreq(heidelTime.getTimexFreq());
                    clearTime.setMod(heidelTime.getTimexMod());
                    clearTime.setQuant(heidelTime.getTimexQuant());
                    clearTime.setTimeType(heidelTime.getTimexType());
                    clearTime.setValue(heidelTime.getTimexValue());
                    clearTime.setId(heidelTime.getTimexId());
                    // FunctionInDocument, TemporalFunction, BeginPoint, EndPoint unset!
                    clearTime.addToIndexes();
            }
    }

	/**
	 * Postprocessing: Remove invalid timex expressions. These are already
	 * marked as invalid: timexValue().equals("REMOVE")
	 *
	 * @param jcas
	 */
	public void removeInvalids(JCas jcas) {

		/*
		 * Iterate over timexes and add invalids to HashSet
		 * (invalids cannot be removed directly since iterator is used)
		 */
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();
		HashSet<Timex3> hsTimexToRemove = new HashSet<Timex3>();
		while (iterTimex.hasNext()) {
			Timex3 timex = (Timex3) iterTimex.next();
			if (timex.getTimexValue().equals("REMOVE")) {
				hsTimexToRemove.add(timex);
			}
		}

		// remove invalids, finally
		for (Timex3 timex3 : hsTimexToRemove) {
			timex3.removeFromIndexes();
			timex_counter--;
			logger.log(Level.FINE, timex3.getTimexId()+"REMOVING PHASE: "+"found by:"+timex3.getFoundByRule()+" text:"+timex3.getCoveredText()+" value:"+timex3.getTimexValue());
		}
	}

	/**
	 * @param jcas
	 */
	public void deleteOverlappedTimexes(JCas jcas) {
		FSIterator timexIter1 = jcas.getAnnotationIndex(Timex3.type).iterator();
		HashSet<Timex3> hsTimexesToRemove = new HashSet<Timex3>();

		while (timexIter1.hasNext()) {
			Timex3 t1 = (Timex3) timexIter1.next();
			FSIterator timexIter2 = jcas.getAnnotationIndex(Timex3.type)
					.iterator();

			while (timexIter2.hasNext()) {
				Timex3 t2 = (Timex3) timexIter2.next();
				if (((t1.getBegin() >= t2.getBegin()) && (t1.getEnd() < t2.getEnd())) ||     // t1 starts inside or with t2 and ends before t2 -> remove t1
						((t1.getBegin() > t2.getBegin()) && (t1.getEnd() <= t2.getEnd()))) { // t1 starts inside t2 and ends with or before t2 -> remove t1
					hsTimexesToRemove.add(t1);
				}
				else if (((t2.getBegin() >= t1.getBegin()) && (t2.getEnd() < t1.getEnd())) || // t2 starts inside or with t1 and ends before t1 -> remove t2
						((t2.getBegin() > t1.getBegin()) && (t2.getEnd() <= t1.getEnd()))) {    // t2 starts inside t1 and ends with or before t1 -> remove t2
					hsTimexesToRemove.add(t2);
				}
				// identical length
				if ((t1.getBegin() == t2.getBegin()) && (t1.getEnd() == t2.getEnd())) {
					if ((t1.getTimexType().equals("SET")) || (t2.getTimexType().equals("SET"))) {
						// REMOVE REAL DUPLICATES (the one with the lower timexID)
						if ((Integer.parseInt(t1.getTimexId().substring(1)) < Integer.parseInt(t2.getTimexId().substring(1)))) {
							hsTimexesToRemove.add(t1);
						}
					} else {
						if (!(t1.equals(t2))){
							if ((t1.getTimexValue().startsWith("UNDEF")) && (!(t2.getTimexValue().startsWith("UNDEF")))) {
								hsTimexesToRemove.add(t1);
							}
							else if ((!(t1.getTimexValue().startsWith("UNDEF"))) && (t2.getTimexValue().startsWith("UNDEF"))) {
								hsTimexesToRemove.add(t2);
							}
							// t1 is explicit, but t2 is not
							else if ((t1.getFoundByRule().endsWith("explicit")) && (!(t2.getFoundByRule().endsWith("explicit")))){
								hsTimexesToRemove.add(t2);
							}
							// REMOVE REAL DUPLICATES (the one with the lower timexID)
							else if ((Integer.parseInt(t1.getTimexId().substring(1)) < Integer.parseInt(t2.getTimexId().substring(1)))) {
								hsTimexesToRemove.add(t1);
							}
						}
					}
				}
			}
		}
		// remove, finally
		for (Timex3 t : hsTimexesToRemove) {
			logger.log(Level.FINE, t.getTimexId()+"REMOVE DUPLICATE: " + t.getCoveredText()+"(id:"+t.getTimexId()+" value:"+t.getTimexValue()+" found by:"+t.getFoundByRule()+")");
			t.removeFromIndexes();
			timex_counter--;
		}
	}


	// TODO outsource and include modification to be added to Timex RULES
	public String getModification(String modString) {
		String mod = "";
		if (modString.equals("at least")) {
			mod = "EQUAL_OR_MORE";
		} else if (modString.equals("more than")) {
			mod = "MORE_THAN";
		} else if ((modString.equals("the middle of"))
				|| (modString.equals("mid"))) {
			mod = "MID";
		} else if (modString.equals("early")) {
			mod = "START";
		} else if ((modString.equals("late")) || (modString.equals("later"))) {
			mod = "END";
		}
		return mod;
	}

}