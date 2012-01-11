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
		logger = aContext.getLogger();
	
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

		// GLOBAL NORMALIZATION INFORMATION
		readGlobalNormalizationInformation();
	
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
						hmAllRePattern);
				rm.setLogger(logger);
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
				timex_counter += rmDate.findTimexes(s, jcas, hmAllNormalization, idGenerator);
			}
			if (find_times) {
				timex_counter += rmTime.findTimexes(s, jcas, hmAllNormalization, idGenerator);
			}
			if (find_durations) {
				timex_counter += rmDuration.findTimexes(s, jcas, hmAllNormalization, idGenerator);
			}
			if (find_sets) {
				timex_counter += rmSet.findTimexes(s, jcas, hmAllNormalization, idGenerator);
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
		specifyAmbiguousValues(jcas);
	
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

	private void logNearTense(String title, Token token) {
		logger.log(Level.FINE, title + ": string:"+token.getCoveredText()+" pos:"+token.getPos() + "\n" +
				"hmAllRePattern.containsKey(tensePos4PresentFuture):"+hmAllRePattern.get("tensePos4PresentFuture") + "\n" +
				"hmAllRePattern.containsKey(tensePos4Future):"+hmAllRePattern.get("tensePos4Future") + "\n" +
				"hmAllRePattern.containsKey(tensePos4Past):"+hmAllRePattern.get("tensePos4Past") + "\n" +
				"CHECK TOKEN:"+token.getPos());
	}

	/**
	 * Get the last tense used in the sentence
	 *
	 * @param timex
	 * @return
	 */
	public String getClosestTense(Timex3 timex, JCas jcas) {
	
		String lastTense = "";
		String nextTense = "";
	
		int tokenCounter = 0;
		int lastid = 0;
		int nextid = 0;
		int tid    = 0;

		// Get the sentence
		FSIterator iterSentence = jcas.getAnnotationIndex(Sentence.type).iterator();
		Sentence s = new Sentence(jcas);
		while (iterSentence.hasNext()) {
			s = (Sentence) iterSentence.next();
			if ((s.getBegin() < timex.getBegin())
					&& (s.getEnd() > timex.getEnd())) {
				break;
			}
		}

		// Get the tokens
		TreeMap<Integer, Token> tmToken = new TreeMap<Integer, Token>();
		FSIterator iterToken = jcas.getAnnotationIndex(Token.type).subiterator(s);
		while (iterToken.hasNext()) {
			Token token = (Token) iterToken.next();
			tmToken.put(token.getEnd(), token);
		}
	
		// Get the last VERB token
		for (Integer tokEnd : tmToken.keySet()) {
			tokenCounter++;
			if (tokEnd < timex.getBegin()) {
				Token token = tmToken.get(tokEnd);
				logNearTense("GET LAST TENSE", token);
				if (token.getPos() == null){
				
				}
				else if ((hmAllRePattern.containsKey("tensePos4PresentFuture")) && (token.getPos().matches(hmAllRePattern.get("tensePos4PresentFuture")))){
					lastTense = "PRESENTFUTURE";
					lastid = tokenCounter;
				}
				else if ((hmAllRePattern.containsKey("tensePos4Past")) && (token.getPos().matches(hmAllRePattern.get("tensePos4Past")))){
					lastTense = "PAST";
					lastid = tokenCounter;
				}
				else if ((hmAllRePattern.containsKey("tensePos4Future")) && (token.getPos().matches(hmAllRePattern.get("tensePos4Future")))){
					if (token.getCoveredText().matches(hmAllRePattern.get("tenseWord4Future"))){
						lastTense = "FUTURE";
						lastid = tokenCounter;
					}
				}
			}
			else{
				if (tid == 0){
					tid = tokenCounter;
				}
			}
		}
		tokenCounter = 0;
		for (Integer tokEnd : tmToken.keySet()) {
			tokenCounter++;
			if (nextTense.equals("")) {
				if (tokEnd > timex.getEnd()) {
					Token token = tmToken.get(tokEnd);
					logNearTense("GET NEXT TENSE", token);
					if (token.getPos() == null){
					
					}
					else if ((hmAllRePattern.containsKey("tensePos4PresentFuture")) && (token.getPos().matches(hmAllRePattern.get("tensePos4PresentFuture")))){
						nextTense = "PRESENTFUTURE";
						nextid = tokenCounter;
					}
					else if ((hmAllRePattern.containsKey("tensePos4Past")) && (token.getPos().matches(hmAllRePattern.get("tensePos4Past")))){
						nextTense = "PAST";
						nextid = tokenCounter;
					}
					else if ((hmAllRePattern.containsKey("tensePos4Future")) && (token.getPos().matches(hmAllRePattern.get("tensePos4Future")))){
						if (token.getCoveredText().matches(hmAllRePattern.get("tenseWord4Future"))){
							nextTense = "FUTURE";
							nextid = tokenCounter;
						}
					}
				}
			}
		}
		if (lastTense.equals("")){
			logger.log(Level.FINE, "TENSE: "+nextTense);
			return nextTense;
		}
		else if (nextTense.equals("")){
			logger.log(Level.FINE, "TENSE: "+lastTense);
			return lastTense;
		}
		else{
			// If there is tense before and after the timex token,
			// return the closer one:
			if ((tid - lastid) > (nextid - tid)){
				logger.log(Level.FINE, "TENSE: "+nextTense);
				return nextTense;
			}
			else{
				logger.log(Level.FINE, "TENSE: "+lastTense);
				return lastTense;
			}
		}
	}


	/**
	 * Get the last tense used in the sentence
	 *
	 * @param timex
	 * @return
	 */
	public String getLastTense(Timex3 timex, JCas jcas) {
	
		String lastTense = "";

		// Get the sentence
		FSIterator iterSentence = jcas.getAnnotationIndex(Sentence.type).iterator();
		Sentence s = new Sentence(jcas);
		while (iterSentence.hasNext()) {
			s = (Sentence) iterSentence.next();
			if ((s.getBegin() < timex.getBegin())
					&& (s.getEnd() > timex.getEnd())) {
				break;
			}
		}

		// Get the tokens
		TreeMap<Integer, Token> tmToken = new TreeMap<Integer, Token>();
		FSIterator iterToken = jcas.getAnnotationIndex(Token.type).subiterator(s);
		while (iterToken.hasNext()) {
			Token token = (Token) iterToken.next();
			tmToken.put(token.getEnd(), token);
		}

		// Get the last VERB token
		for (Integer tokEnd : tmToken.keySet()) {
			if (tokEnd < timex.getBegin()) {
				Token token = tmToken.get(tokEnd);
				logNearTense("GET LAST TENSE", token);
				if (token.getPos() == null){
				
				}
				else if ((hmAllRePattern.containsKey("tensePos4PresentFuture")) && (token.getPos().matches(hmAllRePattern.get("tensePos4PresentFuture")))){
					lastTense = "PRESENTFUTURE";
				}
				else if ((hmAllRePattern.containsKey("tensePos4Past")) && (token.getPos().matches(hmAllRePattern.get("tensePos4Past")))){
					lastTense = "PAST";
				}
				else if ((hmAllRePattern.containsKey("tensePos4Future")) && (token.getPos().matches(hmAllRePattern.get("tensePos4Future")))){
					if (token.getCoveredText().matches(hmAllRePattern.get("tenseWord4Future"))){
						lastTense = "FUTURE";
					}
				}
				if (token.getCoveredText().equals("since")){
					lastTense = "PAST";
				}
			}
			if (lastTense.equals("")) {
				if (tokEnd > timex.getEnd()) {
					Token token = tmToken.get(tokEnd);
					logNearTense("GET NEXT TENSE", token);
					if (token.getPos() == null){
					
					}
					else if ((hmAllRePattern.containsKey("tensePos4PresentFuture")) && (token.getPos().matches(hmAllRePattern.get("tensePos4PresentFuture")))){
						lastTense = "PRESENTFUTURE";
					}
					else if ((hmAllRePattern.containsKey("tensePos4Past")) && (token.getPos().matches(hmAllRePattern.get("tensePos4Past")))){
						lastTense = "PAST";
					}
					else if ((hmAllRePattern.containsKey("tensePos4Future")) && (token.getPos().matches(hmAllRePattern.get("tensePos4Future")))){
						if (token.getCoveredText().matches(hmAllRePattern.get("tenseWord4Future"))){
							lastTense = "FUTURE";
						}
					}
				}
			}
		}
		// check for double POS Constraints (not included in the rule language, yet) TODO
		// VHZ VNN and VHZ VNN and VHP VNN and VBP VVN
		String prevPos = "";
		String longTense = "";
		if (lastTense.equals("PRESENTFUTURE")){
			for (Integer tokEnd : tmToken.keySet()) {
				if (tokEnd < timex.getBegin()) {
					Token token = tmToken.get(tokEnd);
					if ((prevPos.equals("VHZ")) || (prevPos.equals("VBZ")) || (prevPos.equals("VHP")) || (prevPos.equals("VBP"))){
						if (token.getPos().equals("VVN")){
							if ((!(token.getCoveredText().equals("expected"))) && (!(token.getCoveredText().equals("scheduled")))){
								lastTense = "PAST";
								longTense = "PAST";
							}
						}
					}
					prevPos = token.getPos();
				}
				if (longTense.equals("")) {
					if (tokEnd > timex.getEnd()) {
						Token token = tmToken.get(tokEnd);
						if ((prevPos.equals("VHZ")) || (prevPos.equals("VBZ")) || (prevPos.equals("VHP")) || (prevPos.equals("VBP"))){
							if (token.getPos().equals("VVN")){
								if ((!(token.getCoveredText().equals("expected"))) && (!(token.getCoveredText().equals("scheduled")))){
									lastTense = "PAST";
									longTense = "PAST";
								}
							}
						}
						prevPos = token.getPos();
					}
				}
			}
		}
		logger.log(Level.FINE, "TENSE: "+lastTense);
		return lastTense;
	}

	/**
	 * Under-specified values are disambiguated here. Only Timexes of types "date" and "time" can be under-specified.
	 * @param jcas
	 */
	public void specifyAmbiguousValues(JCas jcas) {

		// build up a list with all found TIMEX expressions
		List<Timex3> linearDates = new ArrayList<Timex3>();
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();

		// Create List of all Timexes of types "date" and "time"
		while (iterTimex.hasNext()) {
			Timex3 timex = (Timex3) iterTimex.next();
			if ((timex.getTimexType().equals("DATE")) || (timex.getTimexType().equals("TIME"))) {
				linearDates.add(timex);
			}
		}
	
		////////////////////////////////////////
		// IS THERE A DOCUMENT CREATION TIME? //
		////////////////////////////////////////
		boolean documentTypeNews = false;
		boolean dctAvailable = false;
		if (typeToProcess.equals("news")){
			documentTypeNews = true;
		}
		// get the dct information
		String dctValue   = "";
		int dctCentury    = 0;
		int dctYear       = 0;
		int dctDecade     = 0;
		int dctMonth      = 0;
		int dctDay        = 0;
		String dctSeason  = "";
		String dctQuarter = "";
		String dctHalf    = "";
		int dctWeekday    = 0;
		int dctWeek       = 0;
	
		//////////////////////////////////////////////
		// INFORMATION ABOUT DOCUMENT CREATION TIME //
		//////////////////////////////////////////////
		FSIterator dctIter = jcas.getAnnotationIndex(DocumentCreationTime.type).iterator();
		if (dctIter.hasNext()) {
			dctAvailable = true;
			DocumentCreationTime dct = (DocumentCreationTime) dctIter.next();
			dctValue = dct.getValue();
			// year, month, day as mentioned in the DCT
			if (dctValue.matches("\\d\\d\\d\\d\\d\\d\\d\\d")){
				dctCentury   = Integer.parseInt(dctValue.substring(0, 2));
				dctYear      = Integer.parseInt(dctValue.substring(0, 4));
				dctDecade    = Integer.parseInt(dctValue.substring(2, 3));
				dctMonth     = Integer.parseInt(dctValue.substring(4, 6));
				dctDay       = Integer.parseInt(dctValue.substring(6, 8));
				logger.log(Level.FINE, "dctCentury:"+dctCentury);
				logger.log(Level.FINE, "dctYear:"+dctYear);
				logger.log(Level.FINE, "dctDecade:"+dctDecade);
				logger.log(Level.FINE, "dctMonth:"+dctMonth);
				logger.log(Level.FINE, "dctDay:"+dctDay);
			}else{
				dctCentury   = Integer.parseInt(dctValue.substring(0, 2));
				dctYear      = Integer.parseInt(dctValue.substring(0, 4));
				dctDecade    = Integer.parseInt(dctValue.substring(2, 3));
				dctMonth     = Integer.parseInt(dctValue.substring(5, 7));
				dctDay       = Integer.parseInt(dctValue.substring(8, 10));
				logger.log(Level.FINE, "dctCentury:"+dctCentury);
				logger.log(Level.FINE, "dctYear:"+dctYear);
				logger.log(Level.FINE, "dctDecade:"+dctDecade);
				logger.log(Level.FINE, "dctMonth:"+dctMonth);
				logger.log(Level.FINE, "dctDay:"+dctDay);
			}
			dctQuarter = "Q"+normMonthInQuarter.get(normNumber.get(dctMonth+""));
			dctHalf = "H1";
			if (dctMonth > 6){
				dctHalf = "H2";
			}
		
			// season, week, weekday, have to be calculated
			dctSeason    = normMonthInSeason.get(normNumber.get(dctMonth+"")+"");
			dctWeekday   = getWeekdayOfDate(dctYear+"-"+normNumber.get(dctMonth+"")+"-"+ normNumber.get(dctDay+""));
			dctWeek      = getWeekOfDate(dctYear+"-"+normNumber.get(dctMonth+"") +"-"+ normNumber.get(dctDay+""));
			logger.log(Level.FINE, "dctQuarter:"+dctQuarter);
			logger.log(Level.FINE, "dctSeason:"+dctSeason);
			logger.log(Level.FINE, "dctWeekday:"+dctWeekday);
			logger.log(Level.FINE, "dctWeek:"+dctWeek);
		}
		else{
			logger.log(Level.FINE, "No DCT available...");
		}
	
		//////////////////////////////////////////////
		// go through list of Date and Time timexes //
		//////////////////////////////////////////////
		for (int i = 0; i < linearDates.size(); i++) {
			Timex3 t_i = (Timex3) linearDates.get(i);
			String value_i = t_i.getTimexValue();

			// check if value_i has month, day, season, week (otherwise no UNDEF-year is possible)
			Boolean viHasMonth   = false;
			Boolean viHasDay     = false;
			Boolean viHasSeason  = false;
			Boolean viHasWeek    = false;
			Boolean viHasQuarter = false;
			Boolean viHasHalf    = false;
			int viThisMonth      = 0;
			int viThisDay        = 0;
			String viThisSeason  = "";
			String viThisQuarter = "";
			String viThisHalf    = "";
			String[] valueParts  = value_i.split("-");
			// check if UNDEF-year or UNDEF-century
			if ((value_i.startsWith("UNDEF-year")) || (value_i.startsWith("UNDEF-century"))){
				if (valueParts.length > 2){
					// get vi month
					if (valueParts[2].matches("\\d\\d")) {
						viHasMonth  = true;
						viThisMonth = Integer.parseInt(valueParts[2]);
					}
					// get vi season
					else if ((valueParts[2].equals("SP")) || (valueParts[2].equals("SU")) || (valueParts[2].equals("FA")) || (valueParts[2].equals("WI"))) {
						viHasSeason  = true;
						viThisSeason = valueParts[2];
					}
					// get v1 quarter
					else if ((valueParts[2].equals("Q1")) || (valueParts[2].equals("Q2")) || (valueParts[2].equals("Q3")) || (valueParts[2].equals("Q4"))) {
						viHasQuarter  = true;
						viThisQuarter = valueParts[2];
					}
					else if ((valueParts[2].equals("H1")) || (valueParts[2].equals("H2"))){
						viHasHalf  = true;
						viThisHalf = valueParts[2];
					}
					// get vi day
					if ((valueParts.length > 3) && (valueParts[3].matches("\\d\\d"))) {
						viHasDay = true;
						viThisDay = Integer.parseInt(valueParts[3]);
					}
				}
			}
			else{
				if (valueParts.length > 1){
					// get vi month
					if (valueParts[1].matches("\\d\\d")) {
						viHasMonth  = true;
						viThisMonth = Integer.parseInt(valueParts[1]);
					}
					// get vi season
					else if ((valueParts[1].equals("SP")) || (valueParts[1].equals("SU")) || (valueParts[1].equals("FA")) || (valueParts[1].equals("WI"))) {
						viHasSeason  = true;
						viThisSeason = valueParts[1];
					}
					// get vi day
					if ((valueParts.length > 2) && (valueParts[2].matches("\\d\\d"))) {
						viHasDay = true;
						viThisDay = Integer.parseInt(valueParts[2]);
					}
				}
			}
			// get the last tense (depending on the part of speech tags used in front or behind the expression)
			String last_used_tense = getLastTense(t_i, jcas);

			//////////////////////////
			// DISAMBIGUATION PHASE //
			//////////////////////////
		
			////////////////////////////////////////////////////
			// IF YEAR IS COMPLETELY UNSPECIFIED (UNDEF-year) //
			////////////////////////////////////////////////////
			String valueNew = value_i;
			if (value_i.startsWith("UNDEF-year")){
				String newYearValue = dctYear+"";
				// vi has month (ignore day)
				if (viHasMonth == true && (viHasSeason == false)) {
					// WITH DOCUMENT CREATION TIME
					if ((documentTypeNews) && (dctAvailable)){
						//  Tense is FUTURE
						if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
							// if dct-month is larger than vi-month, than add 1 to dct-year
							if (dctMonth > viThisMonth) {
								int intNewYear = dctYear + 1;
								newYearValue = intNewYear + "";
							}
						}
						// Tense is PAST
						if ((last_used_tense.equals("PAST"))){
							// if dct-month is smaller than vi month, than substrate 1 from dct-year					
							if (dctMonth < viThisMonth) {
								int intNewYear = dctYear - 1;
								newYearValue = intNewYear + "";
							}
						}
					}
					// WITHOUT DOCUMENT CREATION TIME
					else {
						newYearValue = getLastMentionedX(linearDates, i, "year");
					}
				}
				// vi has quaurter
				if (viHasQuarter == true){
					// WITH DOCUMENT CREATION TIME
					if ((documentTypeNews) && (dctAvailable)){
						//  Tense is FUTURE
						if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
							if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))){
								int intNewYear = dctYear + 1;
								newYearValue = intNewYear + "";
							}
						}
						// Tense is PAST
						if ((last_used_tense.equals("PAST"))){
							if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))){
								int intNewYear = dctYear - 1;
								newYearValue = intNewYear + "";
							}
						}
					}
					// WITHOUT DOCUMENT CREATION TIME
					else{
						newYearValue = getLastMentionedX(linearDates, i, "year");
					}
				}
				// vi has half
				if (viHasHalf == true){
					// WITH DOCUMENT CREATION TIME
					if ((documentTypeNews) && (dctAvailable)){
						//  Tense is FUTURE
						if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
							if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))){
								int intNewYear = dctYear + 1;
								newYearValue = intNewYear + "";
							}
						}
						// Tense is PAST
						if ((last_used_tense.equals("PAST"))){
							if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))){
								int intNewYear = dctYear - 1;
								newYearValue = intNewYear + "";
							}
						}
					}
					// WITHOUT DOCUMENT CREATION TIME
					else{
						newYearValue = getLastMentionedX(linearDates, i, "year");
					}
				}
			
				// vi has season
				if ((viHasMonth == false) && (viHasDay == false) && (viHasSeason == true)) {
					// TODO check tenses?
					// WITH DOCUMENT CREATION TIME
					if ((documentTypeNews) && (dctAvailable)){
						newYearValue = dctYear+"";
					}
					// WITHOUT DOCUMENT CREATION TIME
					else{
						newYearValue = getLastMentionedX(linearDates, i, "year");
					}
				}
				// vi has week
				if (viHasWeek){
					// WITH DOCUMENT CREATION TIME
					if ((documentTypeNews) && (dctAvailable)){
						newYearValue = dctYear+"";
					}
					// WITHOUT DOCUMENT CREATION TIME
					else{
						newYearValue = getLastMentionedX(linearDates, i, "year");
					}
				}

				// REPLACE THE UNDEF-YEAR WITH THE NEWLY CALCULATED YEAR AND ADD TIMEX TO INDEXES
				if (newYearValue.equals("")){
					valueNew = value_i.replaceFirst("UNDEF-year", "XXXX");
				}
				else{
					valueNew = value_i.replaceFirst("UNDEF-year", newYearValue);
				}
			}

			///////////////////////////////////////////////////
			// just century is unspecified (UNDEF-century86) //
			///////////////////////////////////////////////////
			else if ((value_i.startsWith("UNDEF-century"))){
				String newCenturyValue = dctCentury+"";
				int viThisDecade = Integer.parseInt(value_i.substring(13, 14));
				// NEWS DOCUMENTS
				if ((documentTypeNews) && (dctAvailable)){
					logger.log(Level.FINE, "dctCentury"+dctCentury);
					newCenturyValue = dctCentury+"";
					logger.log(Level.FINE, "dctCentury"+dctCentury);
					//  Tense is FUTURE
					if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
						if (viThisDecade < dctDecade){
							newCenturyValue = dctCentury + 1+"";
						}
						else{
							newCenturyValue = dctCentury+"";
						}
					}
					// Tense is PAST
					if ((last_used_tense.equals("PAST"))){
						if (dctDecade <= viThisDecade){
							newCenturyValue = dctCentury - 1+"";
						}
						else{
							newCenturyValue = dctCentury+"";
						}
					}
				}
				// NARRATIVE DOCUMENTS
				else{
					newCenturyValue = getLastMentionedX(linearDates, i, "century");
				}
				if (newCenturyValue.equals("")){
					// always assume that sixties, twenties, and so on are 19XX (changed 2011-09-08)
					valueNew = value_i.replaceFirst("UNDEF-century", "19");
				}
				else{
					valueNew = value_i.replaceFirst("UNDEF-century", newCenturyValue+"");
				}
				// always assume that sixties, twenties, and so on are 19XX (changed 2011-09-08)
				if (valueNew.matches("\\d\\d\\dX")){
					valueNew = "19" + valueNew.substring(2);
				}
			}
		
			////////////////////////////////////////////////////
			// CHECK IMPLICIT EXPRESSIONS STARTING WITH UNDEF //
			////////////////////////////////////////////////////
			else if (value_i.startsWith("UNDEF")){
				valueNew = value_i;
			
				//////////////////
				// TO CALCULATE //
				//////////////////
				// year to calculate
				if (value_i.matches("^UNDEF-(this|REFUNIT|REF)-(.*?)-(MINUS|PLUS)-([0-9]+).*")){
					for (MatchResult mr : findMatches(Pattern.compile("^(UNDEF-(this|REFUNIT|REF)-(.*?)-(MINUS|PLUS)-([0-9]+)).*"), value_i)){
						String checkUndef = mr.group(1);
						String ltn  = mr.group(2);
						String unit = mr.group(3);
						String op   = mr.group(4);
						int diff    = Integer.parseInt(mr.group(5));
					
						// check for REFUNIT (only allowed for "year")
						if ((ltn.equals("REFUNIT")) && (unit.equals("year"))){
							String dateWithYear = getLastMentionedX(linearDates, i, "dateYear");
							if (dateWithYear.equals("")){
								valueNew = valueNew.replace(checkUndef, "XXXX");
							}
							else{
								if (op.equals("MINUS")){
									diff = diff * (-1);
								}
								int yearNew = Integer.parseInt(dateWithYear.substring(0,4)) + diff;
								String rest = dateWithYear.substring(4);
								valueNew = valueNew.replace(checkUndef, yearNew+rest);
							}
						}
					
					
					
						// REF and this are handled here
						if (unit.equals("century")){
							if ((documentTypeNews) && (dctAvailable) && (ltn.equals("this"))){
								int century = dctCentury;
								if (op.equals("MINUS")){
									century = dctCentury - diff;
								}
								else if (op.equals("PLUS")){
									century = dctCentury + diff;
								}
								valueNew = valueNew.replace(checkUndef, century+"XX");
							}
							else{
								String lmCentury = getLastMentionedX(linearDates, i, "century");
								if (lmCentury.equals("")){
									valueNew = valueNew.replace(checkUndef, "XX");
								}
								else{
									if (op.equals("MINUS")){
										lmCentury = Integer.parseInt(lmCentury) - diff + "XX";
									}
									else if (op.equals("PLUS")){
										lmCentury = Integer.parseInt(lmCentury) + diff + "XX";
									}
									valueNew = valueNew.replace(checkUndef, lmCentury);
								}
							}
						}
						else if (unit.equals("decade")){
							if ((documentTypeNews) && (dctAvailable) && (ltn.equals("this"))){
								int decade = dctDecade;
								if (op.equals("MINUS")){
									decade = dctDecade - diff;
								}
								else if (op.equals("PLUS")){
									decade = dctDecade + diff;
								}
								valueNew = valueNew.replace(checkUndef, decade+"X");
							}
							else{
								String lmDecade = getLastMentionedX(linearDates, i, "decade");
								if (lmDecade.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXX");
								}
								else{
									if (op.equals("MINUS")){
										lmDecade = Integer.parseInt(lmDecade) - diff + "X";
									}
									else if (op.equals("PLUS")){
										lmDecade = Integer.parseInt(lmDecade) + diff + "X";
									}
									valueNew = valueNew.replace(checkUndef, lmDecade);
								}
							}
						}
						else if (unit.equals("year")){
							if ((documentTypeNews) && (dctAvailable) && (ltn.equals("this"))){
								int intValue = dctYear;
								if (op.equals("MINUS")){
									intValue = dctYear - diff;
								}
								else if (op.equals("PLUS")){
									intValue = dctYear + diff;
								}
								valueNew = valueNew.replace(checkUndef, intValue + "");
							}
							else{
								String lmYear = getLastMentionedX(linearDates, i, "year");
								if (lmYear.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX");
								}
								else{
									int intValue = Integer.parseInt(lmYear);
									if (op.equals("MINUS")){
										intValue = Integer.parseInt(lmYear) - diff;
									}
									else if (op.equals("PLUS")){
										intValue = Integer.parseInt(lmYear) + diff;
									}
									valueNew = valueNew.replace(checkUndef, intValue+"");
								}
							}
						}
						else if (unit.equals("quarter")){
							if ((documentTypeNews) && (dctAvailable) && (ltn.equals("this"))){
								int intYear    = dctYear;
								int intQuarter = Integer.parseInt(dctQuarter.substring(1));
								int diffQuarters = diff % 4;
								diff = diff - diffQuarters;
								int diffYears    = diff / 4;
								if (op.equals("MINUS")){
									diffQuarters = diffQuarters * (-1);
									diffYears    = diffYears    * (-1);
								}
								intYear    = intYear + diffYears;
								intQuarter = intQuarter + diffQuarters;
								valueNew = valueNew.replace(checkUndef, intYear+"-Q"+intQuarter);
							}
							else{
								String lmQuarter = getLastMentionedX(linearDates, i, "quarter");
								if (lmQuarter.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								}
								else{
									int intYear    = Integer.parseInt(lmQuarter.substring(0, 4));
									int intQuarter = Integer.parseInt(lmQuarter.substring(6));
									int diffQuarters = diff % 4;
									diff = diff - diffQuarters;
									int diffYears    = diff / 4;
									if (op.equals("MINUS")){
										diffQuarters = diffQuarters * (-1);
										diffYears    = diffYears    * (-1);
									}
									intYear    = intYear + diffYears;
									intQuarter = intQuarter + diffQuarters;
									valueNew = valueNew.replace(checkUndef, intYear+"-Q"+intQuarter);
								}
							}
						}
						else if (unit.equals("month")){
							if ((documentTypeNews) && (dctAvailable) && (ltn.equals("this"))){
								if (op.equals("MINUS")){
									diff = diff * (-1);
								}
								valueNew = valueNew.replace(checkUndef, getXNextMonth(dctYear + "-" + normNumber.get(dctMonth+""), diff));
							}
							else{
								String lmMonth = getLastMentionedX(linearDates, i, "month");
								if (lmMonth.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								}
								else{
									if (op.equals("MINUS")){
										diff = diff * (-1);
									}
									valueNew = valueNew.replace(checkUndef, getXNextMonth(lmMonth, diff));
								}
							}
						}
						else if (unit.equals("week")){
							if ((documentTypeNews) && (dctAvailable) && (ltn.equals("this"))){
								if (op.equals("MINUS")){
									diff = diff * 7 * (-1);
								}
								else if (op.equals("PLUS")){
									diff = diff * 7;
								}
								valueNew = valueNew.replace(checkUndef, getXNextDay(dctYear + "-" + normNumber.get(dctMonth+"") + "-"	+ dctDay, diff));
							}
							else{
								String lmDay = getLastMentionedX(linearDates, i, "day");
								if (lmDay.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
								}
								else{
									if (op.equals("MINUS")){
										diff = diff * 7 * (-1);
									}
									else if (op.equals("PLUS")){
										diff = diff * 7;
									}
									valueNew = valueNew.replace(checkUndef, getXNextDay(lmDay, diff));
								}
							}
						}
						else if (unit.equals("day")){
							if ((documentTypeNews) && (dctAvailable) && (ltn.equals("this"))){
								if (op.equals("MINUS")){
									diff = diff * (-1);
								}
								valueNew = valueNew.replace(checkUndef, getXNextDay(dctYear + "-" + normNumber.get(dctMonth+"") + "-"	+ dctDay, diff));
							}
							else{
								String lmDay = getLastMentionedX(linearDates, i, "day");
								if (lmDay.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
								}
								else{
									if (op.equals("MINUS")){
										diff = diff * (-1);
									}
									valueNew = valueNew.replace(checkUndef, getXNextDay(lmDay, diff));
								}
							}
						}
					}
				}
		
				// century
				else if (value_i.startsWith("UNDEF-last-century")){
					String checkUndef = "UNDEF-last-century";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, normNumber.get(dctCentury - 1 +"") + "XX");
					}
					else{
						String lmCentury = getLastMentionedX(linearDates,i,"century");
						if (lmCentury.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, normNumber.get(Integer.parseInt(lmCentury) - 1 +"") + "XX");
						}
					}
				}
				else if (value_i.startsWith("UNDEF-this-century")){
					String checkUndef = "UNDEF-this-century";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, normNumber.get(dctCentury+"") + "XX");
					}
					else{
						String lmCentury = getLastMentionedX(linearDates,i,"century");
						if (lmCentury.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, normNumber.get(Integer.parseInt(lmCentury)+"") + "XX");
						}
					}
				}
				else if (value_i.startsWith("UNDEF-next-century")){
					String checkUndef = "UNDEF-next-century";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, normNumber.get(dctCentury + 1+"") + "XX");
					}
					else{
						String lmCentury = getLastMentionedX(linearDates,i,"century");
						if (lmCentury.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, normNumber.get(Integer.parseInt(lmCentury) + 1+"") + "XX");
						}
					}
				}

				// decade
				else if (value_i.startsWith("UNDEF-last-decade")){
					String checkUndef = "UNDEF-last-decade";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, (dctYear - 10+"").substring(0,3)+"X");
					}
					else{
						String lmDecade = getLastMentionedX(linearDates,i,"decade");
						if (lmDecade.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmDecade)-1+"X");
						}
					}
				}
				else if (value_i.startsWith("UNDEF-this-decade")){
					String checkUndef = "UNDEF-this-decade";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, (dctYear+"").substring(0,3)+"X");
					}
					else{
						String lmDecade = getLastMentionedX(linearDates,i,"decade");
						if (lmDecade.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, lmDecade+"X");					
						}
					}
				}
				else if (value_i.startsWith("UNDEF-next-decade")) {
					String checkUndef = "UNDEF-next-decade";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, (dctYear + 10+"").substring(0,3)+"X");
					}
					else{
						String lmDecade = getLastMentionedX(linearDates,i,"decade");
						if (lmDecade.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmDecade)+1+"X");
						}
					}
				}
			
				// year
				else if (value_i.startsWith("UNDEF-last-year")) {
					String checkUndef = "UNDEF-last-year";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, dctYear -1 +"");
					}
					else{
						String lmYear = getLastMentionedX(linearDates,i,"year");
						if (lmYear.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmYear)-1+"");
						}
					}
				}
				else if (value_i.startsWith("UNDEF-this-year")){
					String checkUndef = "UNDEF-this-year";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, dctYear +"");
					}
					else{
						String lmYear = getLastMentionedX(linearDates,i,"year");
						if (lmYear.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, lmYear);
						}
					}
				}
				else if (value_i.startsWith("UNDEF-next-year")) {
					String checkUndef = "UNDEF-next-year";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, dctYear +1 +"");
					}
					else{
						String lmYear = getLastMentionedX(linearDates,i,"year");
						if (lmYear.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmYear)+1+"");					
						}
					}
				}
			
				// month
				else if (value_i.startsWith("UNDEF-last-month")) {
					String checkUndef = "UNDEF-last-month";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, getXNextMonth(dctYear + "-" + normNumber.get(dctMonth+""), -1));
					}
					else{
						String lmMonth = getLastMentionedX(linearDates,i,"month");
						if (lmMonth.equals("")){
							valueNew =  valueNew.replace(checkUndef, "XXXX-XX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, getXNextMonth(lmMonth, -1));
						}
					}
				}
				else if (value_i.startsWith("UNDEF-this-month")){
					String checkUndef = "UNDEF-this-month";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, dctYear + "-" + normNumber.get(dctMonth+""));
					}
					else{
						String lmMonth = getLastMentionedX(linearDates,i,"month");
						if (lmMonth.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-XX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, lmMonth);
						}
					}
				}
				else if (value_i.startsWith("UNDEF-next-month")) {
					String checkUndef = "UNDEF-next-month";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, getXNextMonth(dctYear + "-" + normNumber.get(dctMonth+""), 1));
					}
					else{
						String lmMonth = getLastMentionedX(linearDates,i,"month");
						if (lmMonth.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-XX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, getXNextMonth(lmMonth, 1));
						}
					}
				}
			
				// day
				else if (value_i.startsWith("UNDEF-last-day")) {
					String checkUndef = "UNDEF-last-day";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, getXNextDay(dctYear + "-" + normNumber.get(dctMonth+"") + "-"+ dctDay, -1));
					}
					else{
						String lmDay = getLastMentionedX(linearDates,i,"day");
						if (lmDay.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, getXNextDay(lmDay,-1));
						}
					}
				}
				else if (value_i.startsWith("UNDEF-this-day")){
					String checkUndef = "UNDEF-this-day";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, dctYear + "-" + normNumber.get(dctMonth+"") + "-"+ normNumber.get(dctDay+""));
					}
					else{
						String lmDay = getLastMentionedX(linearDates,i,"day");
						if (lmDay.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, lmDay);
						}
						if (value_i.equals("UNDEF-this-day")){
							valueNew = "PRESENT_REF";
						}
					}				
				}
				else if (value_i.startsWith("UNDEF-next-day")) {
					String checkUndef = "UNDEF-next-day";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, getXNextDay(dctYear + "-" + normNumber.get(dctMonth+"") + "-"+ dctDay, 1));
					}
					else{
						String lmDay = getLastMentionedX(linearDates,i,"day");
						if (lmDay.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, getXNextDay(lmDay,1));
						}
					}
				}

				// week
				else if (value_i.startsWith("UNDEF-last-week")) {
					String checkUndef = "UNDEF-last-week";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, getXNextWeek(dctYear+"-W"+normNumber.get(dctWeek+""),-1));
					}
					else{
						String lmWeek = getLastMentionedX(linearDates,i,"week");
						if (lmWeek.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-WXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, getXNextWeek(lmWeek,-1));
						}
					}
				}
				else if (value_i.startsWith("UNDEF-this-week")){
					String checkUndef = "UNDEF-this-week";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef,dctYear+"-W"+normNumber.get(dctWeek+""));
					}
					else{
						String lmWeek = getLastMentionedX(linearDates,i,"week");
						if (lmWeek.equals("")){
							valueNew = valueNew.replace(checkUndef,"XXXX-WXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef,lmWeek);
						}
					}				
				}
				else if (value_i.startsWith("UNDEF-next-week")) {
					String checkUndef = "UNDEF-next-week";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, getXNextWeek(dctYear+"-W"+normNumber.get(dctWeek+""),1));
					}
					else{
						String lmWeek = getLastMentionedX(linearDates,i,"week");
						if (lmWeek.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-WXX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, getXNextWeek(lmWeek,1));
						}
					}
				}
			
				// quarter
				else if (value_i.startsWith("UNDEF-last-quarter")) {
					String checkUndef = "UNDEF-last-quarter";
					if ((documentTypeNews) && (dctAvailable)){
						if (dctQuarter.equals("Q1")){
							valueNew = valueNew.replace(checkUndef, dctYear-1+"-Q4");
						}
						else{
							int newQuarter = Integer.parseInt(dctQuarter.substring(1,2))-1;
							valueNew = valueNew.replace(checkUndef, dctYear+"-Q"+newQuarter);
						}
					}
					else{
						String lmQuarter  = getLastMentionedX(linearDates, i, "quarter");
						if (lmQuarter.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-QX");
						}
						else{
							int lmQuarterOnly = Integer.parseInt(lmQuarter.substring(6,7));
							int lmYearOnly    = Integer.parseInt(lmQuarter.substring(0,4));
							if (lmQuarterOnly == 1){
								valueNew = valueNew.replace(checkUndef, lmYearOnly-1+"-Q4");
							}
							else{
								int newQuarter = lmQuarterOnly-1;
								valueNew = valueNew.replace(checkUndef, dctYear+"-Q"+newQuarter);
							}
						}
					}
				}
				else if (value_i.startsWith("UNDEF-this-quarter")){
					String checkUndef = "UNDEF-this-quarter";
					if ((documentTypeNews) && (dctAvailable)){
						valueNew = valueNew.replace(checkUndef, dctYear+"-"+dctQuarter);
					}
					else{
						String lmQuarter = getLastMentionedX(linearDates, i, "quarter");
						if (lmQuarter.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-QX");
						}
						else{
							valueNew = valueNew.replace(checkUndef, lmQuarter);
						}
					}				
				}
				else if (value_i.startsWith("UNDEF-next-quarter")) {
					String checkUndef = "UNDEF-next-quarter";
					if ((documentTypeNews) && (dctAvailable)){
						if (dctQuarter.equals("Q4")){
							valueNew = valueNew.replace(checkUndef, dctYear+1+"-Q1");
						}
						else{
							int newQuarter = Integer.parseInt(dctQuarter.substring(1,2))+1;
							valueNew = valueNew.replace(checkUndef, dctYear+"-Q"+newQuarter);
						}					
					}
					else{
						String lmQuarter  = getLastMentionedX(linearDates, i, "quarter");
						if (lmQuarter.equals("")){
							valueNew = valueNew.replace(checkUndef, "XXXX-QX");
						}
						else{
							int lmQuarterOnly = Integer.parseInt(lmQuarter.substring(6,7));
							int lmYearOnly    = Integer.parseInt(lmQuarter.substring(0,4));
							if (lmQuarterOnly == 4){
								valueNew = valueNew.replace(checkUndef, lmYearOnly+1+"-Q1");
							}
							else{
								int newQuarter = lmQuarterOnly+1;
								valueNew = valueNew.replace(checkUndef, dctYear+"-Q"+newQuarter);
							}
						}
					}
				}
			
				// MONTH NAMES
				else if (value_i.matches("UNDEF-(last|this|next)-(january|february|march|april|may|june|july|august|september|october|november|december).*")){
					for (MatchResult mr : findMatches(Pattern.compile("(UNDEF-(last|this|next)-(january|february|march|april|may|june|july|august|september|october|november|december)).*"),value_i)){
						String checkUndef = mr.group(1);
						String ltn      = mr.group(2);
						String newMonth = normMonthName.get((mr.group(3)));
						int newMonthInt = Integer.parseInt(newMonth);
						if (ltn.equals("last")){
							if ((documentTypeNews) && (dctAvailable)){
								if (dctMonth <= newMonthInt){
									valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newMonth);
								}
								else{
									valueNew = valueNew.replace(checkUndef, dctYear+"-"+newMonth);
								}
							}
							else{
								String lmMonth = getLastMentionedX(linearDates, i, "month");
								if (lmMonth.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								}
								else{
									int lmMonthInt = Integer.parseInt(lmMonth.substring(5,7));
									if (lmMonthInt <= newMonthInt){
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmMonth.substring(0,4))-1+"-"+newMonth);
									}
									else{
										valueNew = valueNew.replace(checkUndef, lmMonth.substring(0,4)+"-"+newMonth);
									}
								}
							}
						}
						else if (ltn.equals("this")){
							if ((documentTypeNews) && (dctAvailable)){
								valueNew = valueNew.replace(checkUndef, dctYear+"-"+newMonth);
							}
							else{
								String lmMonth = getLastMentionedX(linearDates, i, "month");
								if (lmMonth.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								}
								else{
									valueNew = valueNew.replace(checkUndef, lmMonth.substring(0,4)+"-"+newMonth);
								}
							}
						}
						else if (ltn.equals("next")){
							if ((documentTypeNews) && (dctAvailable)){
								if (dctMonth >= newMonthInt){
									valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newMonth);
								}
								else{
									valueNew = valueNew.replace(checkUndef, dctYear+"-"+newMonth);
								}
							}
							else{
								String lmMonth = getLastMentionedX(linearDates, i, "month");
								if (lmMonth.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								}
								else{
									int lmMonthInt = Integer.parseInt(lmMonth.substring(5,7));
									if (lmMonthInt >= newMonthInt){
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmMonth.substring(0,4))+1+"-"+newMonth);
									}
									else{
										valueNew = valueNew.replace(checkUndef, lmMonth.substring(0,4)+"-"+newMonth);
									}
								}
							}						
						}
					}
				}
			
				// SEASONS NAMES
				else if (value_i.matches("^UNDEF-(last|this|next)-(SP|SU|FA|WI).*")){
					for (MatchResult mr : findMatches(Pattern.compile("(UNDEF-(last|this|next)-(SP|SU|FA|WI)).*"),value_i)){
						String checkUndef = mr.group(1);
						String ltn       = mr.group(2);
						String newSeason = mr.group(3);
						if (ltn.equals("last")){
							if ((documentTypeNews) && (dctAvailable)){
								if (dctSeason.equals("SP")){
									valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newSeason);
								}
								else if (dctSeason.equals("SU")){
									if (newSeason.equals("SP")){
										valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
									}
									else{
										valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newSeason);
									}
								}
								else if (dctSeason.equals("FA")){
									if ((newSeason.equals("SP")) || (newSeason.equals("SU"))){
										valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
									}
									else{
										valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newSeason);
									}
								}
								else if (dctSeason.equals("WI")){
									if (newSeason.equals("WI")){
										valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newSeason);
									}
									else{
										valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
									}
								}
							}
							else{ // NARRATVIE DOCUMENT
								String lmSeason = getLastMentionedX(linearDates, i, "season");
								if (lmSeason.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								}
								else{
									if (lmSeason.substring(5,7).equals("SP")){
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))-1+"-"+newSeason);
									}
									else if (lmSeason.substring(5,7).equals("SU")){
										if (lmSeason.substring(5,7).equals("SP")){
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
										}
										else{
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))-1+"-"+newSeason);
										}
									}
									else if (lmSeason.substring(5,7).equals("FA")){
										if ((newSeason.equals("SP")) || (newSeason.equals("SU"))){
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
										}
										else{
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))-1+"-"+newSeason);
										}
									}
									else if (lmSeason.substring(5,7).equals("WI")){
										if (newSeason.equals("WI")){
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))-1+"-"+newSeason);
										}
										else{
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
										}
									}
								}
							}
						}
						else if (ltn.equals("this")){
							if ((documentTypeNews) && (dctAvailable)){
								// TODO include tense of sentence?
								valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
							}
							else{
								// TODO include tense of sentence?
								String lmSeason = getLastMentionedX(linearDates, i, "season");
								if (lmSeason.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								}
								else{
									valueNew = valueNew.replace(checkUndef, lmSeason.substring(0,4)+"-"+newSeason);
								}
							}						
						}
						else if (ltn.equals("next")){
							if ((documentTypeNews) && (dctAvailable)){
								if (dctSeason.equals("SP")){
									if (newSeason.equals("SP")){
										valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newSeason);
									}
									else{
										valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
									}
								}
								else if (dctSeason.equals("SU")){
									if ((newSeason.equals("SP")) || (newSeason.equals("SU"))){
										valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newSeason);
									}
									else{
										valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
									}
								}
								else if (dctSeason.equals("FA")){
									if (newSeason.equals("WI")){
										valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
									}
									else{
										valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newSeason);
									}
								}
								else if (dctSeason.equals("WI")){
									valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newSeason);
								}
							}
							else{ // NARRATIVE DOCUMENT
								String lmSeason = getLastMentionedX(linearDates, i, "season");
								if (lmSeason.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								}
								else{
									if (lmSeason.substring(5,7).equals("SP")){
										if (newSeason.equals("SP")){
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+1+"-"+newSeason);
										}
										else{
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
										}
									}
									else if (lmSeason.substring(5,7).equals("SU")){
										if ((newSeason.equals("SP")) || (newSeason.equals("SU"))){
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+1+"-"+newSeason);
										}
										else{
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
										}
									}
									else if (lmSeason.substring(5,7).equals("FA")){
										if (newSeason.equals("WI")){
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
										}
										else{
											valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+1+"-"+newSeason);
										}
									}
									else if (lmSeason.substring(5,7).equals("WI")){
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+1+"-"+newSeason);
									}
								}
							}
						}
					}
				}
			
				// WEEKDAY NAMES
				// TODO the calculation is strange, but works
				// TODO tense should be included?!
				else if (value_i.matches("^UNDEF-(last|this|next|day)-(monday|tuesday|wednesday|thursday|friday|saturday|sunday).*")){
					for (MatchResult mr : findMatches(Pattern.compile("(UNDEF-(last|this|next|day)-(monday|tuesday|wednesday|thursday|friday|saturday|sunday)).*"),value_i)){
						String checkUndef = mr.group(1);
						String ltnd       = mr.group(2);
						String newWeekday = mr.group(3);
						int newWeekdayInt = Integer.parseInt(normDayInWeek.get(newWeekday));
						if (ltnd.equals("last")){
							if ((documentTypeNews) && (dctAvailable)){
								int diff = (-1) * (dctWeekday - newWeekdayInt);
								if (diff >= 0) {
									diff = diff - 7;
								}
								valueNew = valueNew.replace(checkUndef, getXNextDay(dctYear + "-" + dctMonth + "-" + dctDay, diff));
							}
							else{
								String lmDay     = getLastMentionedX(linearDates, i, "day");
								if (lmDay.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
								}
								else{
									int lmWeekdayInt = getWeekdayOfDate(lmDay);
									int diff = (-1) * (lmWeekdayInt - newWeekdayInt);
									if (diff >= 0) {
										diff = diff - 7;
									}
									valueNew = valueNew.replace(checkUndef, getXNextDay(lmDay, diff));
								}
							}
						}
						else if (ltnd.equals("this")){
							if ((documentTypeNews) && (dctAvailable)){
								// TODO tense should be included?!	
								int diff = (-1) * (dctWeekday - newWeekdayInt);
								if (diff >= 0) {
									diff = diff - 7;
								}
								if (diff == -7) {
									diff = 0;
								}

							
								valueNew = valueNew.replace(checkUndef, getXNextDay(dctYear + "-" + dctMonth + "-"+ dctDay, diff));
							}
							else{
								// TODO tense should be included?!
								String lmDay     = getLastMentionedX(linearDates, i, "day");
								if (lmDay.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
								}
								else{
									int lmWeekdayInt = getWeekdayOfDate(lmDay);
									int diff = (-1) * (lmWeekdayInt - newWeekdayInt);
									if (diff >= 0) {
										diff = diff - 7;
									}
									if (diff == -7) {
										diff = 0;
									}
									valueNew = valueNew.replace(checkUndef, getXNextDay(lmDay, diff));
								}
							}						
						}
						else if (ltnd.equals("next")){
							if ((documentTypeNews) && (dctAvailable)){
								int diff = newWeekdayInt - dctWeekday;
								if (diff <= 0) {
									diff = diff + 7;
								}
								valueNew = valueNew.replace(checkUndef, getXNextDay(dctYear + "-" + dctMonth + "-"+ dctDay, diff));
							}
							else{
								String lmDay     = getLastMentionedX(linearDates, i, "day");
								if (lmDay.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
								}
								else{
									int lmWeekdayInt = getWeekdayOfDate(lmDay);
									int diff = newWeekdayInt - lmWeekdayInt;
									if (diff <= 0) {
										diff = diff + 7;
									}
									valueNew = valueNew.replace(checkUndef, getXNextDay(lmDay, diff));
								}
							}
						}
						else if (ltnd.equals("day")){
							if ((documentTypeNews) && (dctAvailable)){
								// TODO tense should be included?!
								int diff = (-1) * (dctWeekday - newWeekdayInt);
								if (diff >= 0) {
									diff = diff - 7;
								}
								if (diff == -7) {
									diff = 0;
								}
								//  Tense is FUTURE
								if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
									diff = diff + 7;
								}
								// Tense is PAST
								if ((last_used_tense.equals("PAST"))){
							
								}
								valueNew = valueNew.replace(checkUndef, getXNextDay(dctYear + "-" + dctMonth + "-"+ dctDay, diff));
							}
							else{
								// TODO tense should be included?!
								String lmDay     = getLastMentionedX(linearDates, i, "day");
								if (lmDay.equals("")){
									valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
								}
								else{
									int lmWeekdayInt = getWeekdayOfDate(lmDay);
									int diff = (-1) * (lmWeekdayInt - newWeekdayInt);
									if (diff >= 0) {
										diff = diff - 7;
									}
									if (diff == -7) {
										diff = 0;
									}
									valueNew = valueNew.replace(checkUndef, getXNextDay(lmDay, diff));
								}
							}
						}
					}
				
				}
				else {
					logger.log(Level.WARNING, "ATTENTION: UNDEF value for: " + valueNew+" is not handled in disambiguation phase!");
				}
			}
			t_i.removeFromIndexes();
			logger.log(Level.FINE, t_i.getTimexId()+" DISAMBIGUATION PHASE: foundBy:"+t_i.getFoundByRule()+" text:"+t_i.getCoveredText()+" value:"+t_i.getTimexValue()+" NEW value:"+valueNew);
			t_i.setTimexValue(valueNew);
			t_i.addToIndexes();
			linearDates.set(i, t_i);
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

	/**
	 * The value of the x of the last mentioned Timex is calculated.
	 * @param linearDates
	 * @param i
	 * @param x
	 * @return
	 */
	public String getLastMentionedX(List<Timex3> linearDates, int i, String x){
	
		// Timex for which to get the last mentioned x (i.e., Timex i)
		Timex3 t_i = linearDates.get(i);
	
		String xValue = "";
		int j = i - 1;
		while (j >= 0){
			Timex3 timex = linearDates.get(j);
			// check that the two timexes to compare do not have the same offset:
				if (!(t_i.getBegin() == timex.getBegin())){
			
					String value = timex.getTimexValue();
					if (x.equals("century")){
						if (value.matches("^[0-9][0-9]...*")){
							xValue = value.substring(0,2);
							break;
						}
						else{
							j--;
						}
					}
					else if (x.equals("decade")){
						if (value.matches("^[0-9][0-9][0-9]..*")){
							xValue = value.substring(0,3);
							break;
						}
						else{
							j--;
						}
					}
					else if (x.equals("year")){
						if (value.matches("^[0-9][0-9][0-9][0-9].*")){
							xValue = value.substring(0,4);
							break;
						}
						else{
							j--;
						}
					}
					else if (x.equals("dateYear")){
						if (value.matches("^[0-9][0-9][0-9][0-9].*")){
							xValue = value;
							break;
						}
						else{
							j--;
						}
					}
					else if (x.equals("month")){
						if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9].*")){
							xValue = value.substring(0,7);
							break;
						}
						else{
							j--;
						}
					}
					else if (x.equals("day")){
						if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].*")){
							xValue = value.substring(0,10);
							break;
						}
						else{
							j--;
						}
					}
					else if (x.equals("week")){
						if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].*")){
							for (MatchResult r : findMatches(Pattern.compile("^(([0-9][0-9][0-9][0-9])-[0-9][0-9]-[0-9][0-9]).*"), value)){
								xValue = r.group(2)+"-W"+getWeekOfDate(r.group(1));
								break;
							}
							break;
						}
						else if (value.matches("^[0-9][0-9][0-9][0-9]-W[0-9][0-9].*")){
							for (MatchResult r : findMatches(Pattern.compile("^([0-9][0-9][0-9][0-9]-W[0-9][0-9]).*"), value)){
								xValue = r.group(1);
								break;
							}
							break;
						}
						else{
							j--;
						}
					}
					else if (x.equals("quarter")){
						if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9].*")){
							String month   = value.substring(5,7);
							String quarter = normMonthInQuarter.get(month);
							xValue = value.substring(0,4)+"-Q"+quarter;
							break;
						}
						else if (value.matches("^[0-9][0-9][0-9][0-9]-Q[1234].*")){
							xValue = value.substring(0,7);
							break;
						}
						else{
							j--;
						}
					}
					else if (x.equals("dateQuarter")){
						if (value.matches("^[0-9][0-9][0-9][0-9]-Q[1234].*")){
							xValue = value.substring(0,7);
							break;
						}
						else{
							j--;
						}
					}
					else if (x.equals("season")){
						if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9].*")){
							String month   = value.substring(5,7);
							String season = normMonthInSeason.get(month);
							xValue = value.substring(0,4)+"-"+season;
							break;
						}
						else if (value.matches("^[0-9][0-9][0-9][0-9]-(SP|SU|FA|WI).*")){
							xValue = value.substring(0,7);
							break;
						}
						else{
							j--;
						}
					}
				
				}
				else{
					j--;
				}
		}
		return xValue;
	}

	/**
	 * get the x-next day of date.
	 *
	 * @param date
	 * @param x
	 * @return
	 */
	public String getXNextDay(String date, Integer x) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		String newDate = "";
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date));
			c.add(Calendar.DAY_OF_MONTH, x);
			c.getTime();
			newDate = formatter.format(c.getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return newDate;
	}

	/**
	 * get the x-next month of date
	 *
	 * @param date
	 * @param x
	 * @return
	 */
	public String getXNextMonth(String date, Integer x) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM");
		String newDate = "";
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date));
			c.add(Calendar.MONTH, x);
			c.getTime();
			newDate = formatter.format(c.getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return newDate;
	}

	public String getXNextWeek(String date, Integer x){
		String date_no_W = date.replace("W", "");
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-w");
		String newDate = "";
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date_no_W));
			c.add(Calendar.WEEK_OF_YEAR, x);
			c.getTime();
			newDate = formatter.format(c.getTime());
			newDate = newDate.substring(0,4)+"-W"+normNumber.get(newDate.substring(5));
		} catch (ParseException e){
			e.printStackTrace();
		}
		return newDate;
	}

	/**
	 * Get the weekday of date
	 *
	 * @param date
	 */
	public int getWeekdayOfDate(String date) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		int weekday = 0;
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date));
			weekday = c.get(Calendar.DAY_OF_WEEK);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return weekday;
	}

	/**
	 * Get the week of date
	 *
	 * @param date
	 * @return
	 */
	public int getWeekOfDate(String date) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		int week = 0;
		;
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date));
			week = c.get(Calendar.WEEK_OF_YEAR);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return week;
	}

	public void readGlobalNormalizationInformation(){

		// MONTH IN QUARTER
		normMonthInQuarter.put("01","1");
		normMonthInQuarter.put("02","1");
		normMonthInQuarter.put("03","1");
		normMonthInQuarter.put("04","2");
		normMonthInQuarter.put("05","2");
		normMonthInQuarter.put("06","2");
		normMonthInQuarter.put("07","3");
		normMonthInQuarter.put("08","3");
		normMonthInQuarter.put("09","3");
		normMonthInQuarter.put("10","4");
		normMonthInQuarter.put("11","4");
		normMonthInQuarter.put("12","4");
	
		// MONTH IN SEASON
		normMonthInSeason.put("", "");
		normMonthInSeason.put("01","WI");
		normMonthInSeason.put("02","WI");
		normMonthInSeason.put("03","SP");
		normMonthInSeason.put("04","SP");
		normMonthInSeason.put("05","SP");
		normMonthInSeason.put("06","SU");
		normMonthInSeason.put("07","SU");
		normMonthInSeason.put("08","SU");
		normMonthInSeason.put("09","FA");
		normMonthInSeason.put("10","FA");
		normMonthInSeason.put("11","FA");
		normMonthInSeason.put("12","WI");
	
		// DAY IN WEEK
		normDayInWeek.put("sunday","1");
		normDayInWeek.put("monday","2");
		normDayInWeek.put("tuesday","3");
		normDayInWeek.put("wednesday","4");
		normDayInWeek.put("thursday","5");
		normDayInWeek.put("friday","6");
		normDayInWeek.put("saturday","7");
		normDayInWeek.put("Sunday","1");
		normDayInWeek.put("Monday","2");
		normDayInWeek.put("Tuesday","3");
		normDayInWeek.put("Wednesday","4");
		normDayInWeek.put("Thursday","5");
		normDayInWeek.put("Friday","6");
		normDayInWeek.put("Saturday","7");
//		normDayInWeek.put("sunday","7");
//		normDayInWeek.put("monday","1");
//		normDayInWeek.put("tuesday","2");
//		normDayInWeek.put("wednesday","3");
//		normDayInWeek.put("thursday","4");
//		normDayInWeek.put("friday","5");
//		normDayInWeek.put("saturday","6");
//		normDayInWeek.put("Sunday","7");
//		normDayInWeek.put("Monday","1");
//		normDayInWeek.put("Tuesday","2");
//		normDayInWeek.put("Wednesday","3");
//		normDayInWeek.put("Thursday","4");
//		normDayInWeek.put("Friday","5");
//		normDayInWeek.put("Saturday","6");
	
	
		// NORM MINUTE
		normNumber.put("0","00");
		normNumber.put("00","00");
		normNumber.put("1","01");
		normNumber.put("01","01");
		normNumber.put("2","02");
		normNumber.put("02","02");
		normNumber.put("3","03");
		normNumber.put("03","03");
		normNumber.put("4","04");
		normNumber.put("04","04");
		normNumber.put("5","05");
		normNumber.put("05","05");
		normNumber.put("6","06");
		normNumber.put("06","06");
		normNumber.put("7","07");
		normNumber.put("07","07");
		normNumber.put("8","08");
		normNumber.put("08","08");
		normNumber.put("9","09");
		normNumber.put("09","09");
		normNumber.put("10","10");
		normNumber.put("11","11");
		normNumber.put("12","12");
		normNumber.put("13","13");
		normNumber.put("14","14");
		normNumber.put("15","15");
		normNumber.put("16","16");
		normNumber.put("17","17");
		normNumber.put("18","18");
		normNumber.put("19","19");
		normNumber.put("20","20");
		normNumber.put("21","21");
		normNumber.put("22","22");
		normNumber.put("23","23");
		normNumber.put("24","24");
		normNumber.put("25","25");
		normNumber.put("26","26");
		normNumber.put("27","27");
		normNumber.put("28","28");
		normNumber.put("29","29");
		normNumber.put("30","30");
		normNumber.put("31","31");
		normNumber.put("32","32");
		normNumber.put("33","33");
		normNumber.put("34","34");
		normNumber.put("35","35");
		normNumber.put("36","36");
		normNumber.put("37","37");
		normNumber.put("38","38");
		normNumber.put("39","39");
		normNumber.put("40","40");
		normNumber.put("41","41");
		normNumber.put("42","42");
		normNumber.put("43","43");
		normNumber.put("44","44");
		normNumber.put("45","45");
		normNumber.put("46","46");
		normNumber.put("47","47");
		normNumber.put("48","48");
		normNumber.put("49","49");
		normNumber.put("50","50");
		normNumber.put("51","51");
		normNumber.put("52","52");
		normNumber.put("53","53");
		normNumber.put("54","54");
		normNumber.put("55","55");
		normNumber.put("56","56");
		normNumber.put("57","57");
		normNumber.put("58","58");
		normNumber.put("59","59");
		normNumber.put("60","60");
	
		// NORM MONTH
		normMonthName.put("january","01");
		normMonthName.put("february","02");
		normMonthName.put("march","03");
		normMonthName.put("april","04");
		normMonthName.put("may","05");
		normMonthName.put("june","06");
		normMonthName.put("july","07");
		normMonthName.put("august","08");
		normMonthName.put("september","09");
		normMonthName.put("october","10");
		normMonthName.put("november","11");
		normMonthName.put("december","12");
	}
}