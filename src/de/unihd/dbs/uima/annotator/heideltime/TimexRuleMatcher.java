package de.unihd.dbs.uima.annotator.heideltime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.cleartk.token.type.Token;
import org.cleartk.token.type.Sentence;

import de.unihd.dbs.uima.annotator.heideltime.substitutions.Expression;
import de.unihd.dbs.uima.annotator.heideltime.substitutions.SubstitutionParser;
import de.unihd.dbs.uima.types.heideltime.Timex3;

public class TimexRuleMatcher {
	String timexType;
	List<RulePattern> patterns; // Sorted by value
	Map<String, Expression>  hmNormalization;
	Map<String, Expression>  hmQuant;
	Map<String, Expression>  hmFreq;
	Map<String, Expression>  hmMod;
	Map<String, List<PosConstraint>>  hmPosConstraint;
	Map<String, OffsetPair>  hmOffset;
	Logger logger;
	
	class RulePattern implements Comparable<RulePattern> {
		Pattern pattern;
		String name;
		public RulePattern(String name, Pattern pattern) {
			this.name = name;
			this.pattern = pattern;
		}
		
		public int compareTo(RulePattern other) {
			return this.name.compareTo(other.name);
		}
	}
	
	class PosConstraint {
		int group;
		String pos;

		public PosConstraint(int group, String pos) {
			this.group = group;
			this.pos = pos;
		}
	}

	class OffsetPair {
		int beginGroup;
		int endGroup;
		
		public OffsetPair(int beginGroup, int endGroup) {
			this.beginGroup = beginGroup;
			this.endGroup = endGroup;
		}

		public OffsetPair(String input) {
			Matcher m = paOffsetPair.matcher(input);
			beginGroup = Integer.parseInt(m.group(1));
			endGroup = Integer.parseInt(m.group(1));
		}
	}

	static final Pattern paVariable = Pattern.compile("%(re[a-zA-Z0-9]*)");
	static final Pattern paRuleFeature = Pattern.compile(",([A-Z_]+)=\"(.*?)\"");
	static final Pattern paReadRules = Pattern.compile("RULENAME=\"(.*?)\",EXTRACTION=\"(.*?)\",NORM_VALUE=\"(.*?)\"(.*)");
	static final Pattern paPosConstraint = Pattern.compile("group\\(([0-9]+)\\):(.*?):");
	static final Pattern paOffsetPair = Pattern.compile("group\\(([0-9]+)\\)-group\\(([0-9]+)\\)");
	
	public TimexRuleMatcher(String timexType, List<RulePattern> patterns,
			Map<String, Expression> hmNormalization,
			Map<String, Expression> hmQuant, Map<String, Expression> hmFreq,
			Map<String, Expression> hmMod, Map<String, List<PosConstraint>> hmPosConstraint,
			Map<String, OffsetPair> hmOffset) {
		this.timexType = timexType;
		this.patterns = patterns;
		Collections.sort(patterns);
		this.hmNormalization = hmNormalization;
		this.hmOffset = hmOffset;
		this.hmQuant = hmQuant;
		this.hmFreq = hmFreq;
		this.hmMod = hmMod;
		this.hmPosConstraint = hmPosConstraint;
		logger = UIMAFramework.getLogger(TimexRuleMatcher.class);
	}

	private TimexRuleMatcher(String timexType) {
		this(timexType, new ArrayList<RulePattern>(),
				new HashMap<String, Expression>(), new HashMap<String, Expression>(),
				new HashMap<String, Expression>(), new HashMap<String, Expression>(),
                new HashMap<String, List<PosConstraint>>(), new HashMap<String, OffsetPair>());
	}

	public TimexRuleMatcher(String timexType, InputStreamReader istream,
			Map<String, String> hmAllRePattern, Map<String, HashMap<String, String>> hmAllNormalization)
	throws IOException {
		this(timexType);
		SubstitutionParser subParser = new SubstitutionParser(hmAllNormalization);
		BufferedReader br = new BufferedReader(istream);
		for ( String line; (line=br.readLine()) != null; ){
			if (line.startsWith("//") || line.equals("")) {
				continue;
			}
			logger.log(Level.FINE, "DEBUGGING: reading rules..."+ line);
			// check each line for the name, extraction, and normalization part
			if (!readRule(line, hmAllRePattern, subParser)) {
				logger.log(Level.WARNING, "Cannot read the following line of " + timexType + "rules \nLine: "+line);
			}
		}
		Collections.sort(patterns);
	}
	
	private Pattern buildExtractionPattern(String rule_extraction, Map<String, String> hmAllRePattern) {
		// Substitute %xxxx expressions
		for (MatchResult mr : findMatches(paVariable, rule_extraction)){
			logger.log(Level.FINE, "DEBUGGING: replacing patterns..."+ mr.group());
			String repl = hmAllRePattern.get(mr.group(1));
			if (repl == null) {
				throw new IllegalArgumentException("Pattern not found: " + mr.group(1));
			}
			rule_extraction = rule_extraction.replaceAll("%"+mr.group(1), hmAllRePattern.get(mr.group(1)));
		}
		// Spaces match all whitespace
		rule_extraction = rule_extraction.replaceAll(" ", "[\\\\s]+");
		
		// Ensure word boundaries
		rule_extraction = "\\b" + rule_extraction + "\\b(?![\\.,]\\d)";
		return Pattern.compile(rule_extraction);
	}

	private boolean readRule(String line, Map<String, String> hmAllRePattern, SubstitutionParser subParser) {
		Matcher r = paReadRules.matcher(line);
		if (!r.find()) {
			return false;
		}
		String rule_name          = r.group(1);
		String rule_extraction    = r.group(2);
		String rule_normalization = r.group(3);
		String debugSummary = rule_name + ": ";
	
		////////////////////////////////////////////////////////////////////
		// RULE EXTRACTION PARTS ARE TRANSLATED INTO REGULAR EXPRESSSIONS //
		////////////////////////////////////////////////////////////////////
		// create pattern for rule extraction part
	
		// get extraction part
		patterns.add(new RulePattern(rule_name, buildExtractionPattern(rule_extraction, hmAllRePattern)));
		// get normalization part
		try {
			hmNormalization.put(rule_name, subParser.parse(rule_normalization));
		} catch (RuntimeException e) {
			logger.log(Level.SEVERE, "Error constructing extraction pattern for rule " + rule_name);
			throw e;
		}
		debugSummary += rule_extraction + "\nNorm: " + rule_normalization;
									
		/////////////////////////////////////
		// CHECK FOR ADDITIONAL CONSTRAINS //
		/////////////////////////////////////
		if (!(r.group(4) == null)){
			for (MatchResult ro : findMatches(paRuleFeature, r.group(4))){
				String key = ro.group(1);
				String value = ro.group(2);
				if ("OFFSET".equals(key)) {
					hmOffset.put(rule_name, new OffsetPair(value));
				} else if ("POS_CONSTRAINT".equals(key)) {
					hmPosConstraint.put(rule_name, parsePosConstraintList(value));
				} else {
					Map<String, Expression> hm;
					if ("NORM_QUANT".equals(key)) {
						hm = hmQuant;
					} else if ("NORM_FREQ".equals(key)) {
						hm = hmFreq;
					} else if ("NORM_MOD".equals(key)) {
						hm = hmMod;
					} else {
						logger.log(Level.WARNING, "Unknown rule feature: " + key + " with value: \"" + value + "\" in features: " + r.group(4));
						continue;
					}
					hm.put(rule_name, subParser.parse(value));
				}
				debugSummary += "\n" + key + ": " + value;
			}
		}
		logger.log(Level.FINER, debugSummary);
		return true;
	}

	/**
	 * Apply the extraction rules, normalization rules
	 * @param s
	 * @param jcas
	 * @return the number of timexes found
	 */
	public int findTimexes(Sentence s, JCas jcas, IdGenerator idGen) {
		int nAdded = 0;
		// Iterator over the rules by sorted by the name of the rules
		// this is important since later, the timexId will be used to
		// decide which of two expressions shall be removed if both
		// have the same offset
		for (RulePattern rulePattern : patterns) {
			for (MatchResult r : findMatches(rulePattern.pattern, s.getCoveredText())) {
				String ruleName = rulePattern.name;
				if (!checkPosConstraint(s, hmPosConstraint.get(ruleName), r, jcas)) {
					continue;
				}
				// Offset of timex expression (in the checked sentence)
				int timexStart = r.start();
				int timexEnd   = r.end();
			
				// Any offset parameter?
				if (hmOffset.containsKey(ruleName)){
					OffsetPair offset = hmOffset.get(ruleName);
					timexStart = r.start(offset.beginGroup);
					timexEnd   = r.end(offset.endGroup);
				}
			
				// Normalization Parameter
				if (!hmNormalization.containsKey(ruleName)) {
					logger.log(Level.WARNING, "SOMETHING REALLY WRONG HERE (could not find normalization pattern): "+rulePattern.name);
					continue;
				}
				addTimexAnnotation(timexStart + s.getBegin(), timexEnd + s.getBegin(), s,
						correctDurationValue(evaluateAttribute(hmNormalization, ruleName, r)),
						evaluateAttribute(hmQuant, ruleName, r),
						evaluateAttribute(hmFreq, ruleName, r),
						evaluateAttribute(hmMod, ruleName, r),
						idGen.next(), ruleName, jcas);
				nAdded++;
			}
		}
		return nAdded;
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
	
	public List<PosConstraint> parsePosConstraintList(String input) {
		List<PosConstraint> res = new ArrayList<PosConstraint>();
		for (MatchResult mr : findMatches(paPosConstraint, input)){
			res.add(new PosConstraint(Integer.parseInt(mr.group(1)), mr.group(2)));
		}
		return res;
	}

	/**
	 * Check whether the part of speech constraint defined in a rule is satisfied.
	 * @param s
	 * @param posConstraint
	 * @param m
	 * @param jcas
	 * @return
	 */
	public boolean checkPosConstraint(Sentence s, List<PosConstraint> constraints, MatchResult m, JCas jcas){
		if (constraints == null) {
			return true;
		}
		// All of one or more constraints must hold
		for (PosConstraint constraint : constraints) {
			int tokenBegin = s.getBegin() + m.start(constraint.group);
			int tokenEnd   = s.getBegin() + m.end(constraint.group);
			String actualPos = getPosFromMatchResult(tokenBegin, tokenEnd, s, jcas);
			if (constraint.pos.equals(actualPos)){
				logger.log(Level.FINE, "POS CONSTRAINT IS VALID: pos should be "+ constraint.pos +" and is "+actualPos);
			}
			else {
				return false;
			}
		}
		return true;
	}

	private String evaluateAttribute(Map<String, Expression> hm, String rule, MatchResult m){
		if (hm.containsKey(rule)){
			return hm.get(rule).evaluate(m).toString();
		}
		return "";
	}

	/**
	 * Durations of a finer granularity are mapped to a coarser one if possible, e.g., "PT24H" -> "P1D".
	 * One may add several further corrections.
	 */
	public String correctDurationValue(String value) {
		if (value.matches("PT[0-9]+H")){
			for (MatchResult mr : findMatches(Pattern.compile("PT([0-9]+)H"), value)){
				int hours = Integer.parseInt(mr.group(1));
				if ((hours % 24) == 0){
					int days = hours / 24;
					value = "P"+days+"D";
				}
			}
		}
		else if (value.matches("PT[0-9]+M")){
			for (MatchResult mr : findMatches(Pattern.compile("PT([0-9]+)M"), value)){
				int minutes = Integer.parseInt(mr.group(1));
				if ((minutes % 60) == 0){
					int hours = minutes / 60;
					value = "PT"+hours+"H";
				}
			}
		}
		else if (value.matches("P[0-9]+M")){
			for (MatchResult mr : findMatches(Pattern.compile("P([0-9]+)M"), value)){
				int months = Integer.parseInt(mr.group(1));
				if ((months % 12) == 0){
					int years = months / 12;
					value = "P"+years+"Y";
				}
			}
		}
		return value;
	}

	/**
	 * Identify the part of speech (POS) of a MatchResult.
	 * @param tokBegin
	 * @param tokEnd
	 * @param s
	 * @param jcas
	 * @return
	 */
	public String getPosFromMatchResult(int tokBegin, int tokEnd, Sentence s, JCas jcas){
		// TODO: precalculate tok.getBegin() -> tok.getPos() mapping for entire jcas to avoid repeated iteration
		FSIterator iterTok = jcas.getAnnotationIndex(Token.type).subiterator(s);
		while (iterTok.hasNext()){
			Token token = (Token) iterTok.next();
			if (token.getBegin() == tokBegin) {
				return token.getPos();
			}
		}
		logger.log(Level.WARNING, "POS not found at " + tokBegin + " in: \"" + s.getCoveredText() + "\"");
		return "";
	}

	/**
	 * Add timex annotation to CAS object.
	 *
	 * @param timexType
	 * @param begin
	 * @param end
	 * @param timexValue
	 * @param timexId
	 * @param foundByRule
	 * @param jcas
	 */
	public void addTimexAnnotation(int begin, int end, Sentence sentence, String timexValue, String timexQuant,
			String timexFreq, String timexMod, String timexId, String foundByRule, JCas jcas) {
	
		Timex3 annotation = new Timex3(jcas);
		annotation.setBegin(begin);
		annotation.setEnd(end);

//		annotation.setFilename(sentence.getFilename());
//		annotation.setSentId(sentence.getSentenceId());

//		FSIterator iterToken = jcas.getAnnotationIndex(Token.type).subiterator(
//				sentence);
//		String allTokIds = "";
//		while (iterToken.hasNext()) {
//			Token tok = (Token) iterToken.next();
//			if (tok.getBegin() == begin) {
//				annotation.setFirstTokId(tok.getTokenId());
//				allTokIds = "BEGIN<-->" + tok.getTokenId();
//			}
//			if ((tok.getBegin() > begin) && (tok.getEnd() <= end)) {
//				allTokIds = allTokIds + "<-->" + tok.getTokenId();
//			}
//		}
//		annotation.setAllTokIds(allTokIds);
		annotation.setTimexType(timexType);
		annotation.setTimexValue(timexValue);
		annotation.setTimexId(timexId);
		annotation.setFoundByRule(foundByRule);
		if ((timexType.equals("DATE")) || (timexType.equals("TIME"))){
			if ((timexValue.startsWith("X")) || (timexValue.startsWith("UNDEF"))){
				annotation.setFoundByRule(foundByRule+"-relative");
			}else{
				annotation.setFoundByRule(foundByRule+"-explicit");
			}
		}
		if (!(timexQuant == null)) {
			annotation.setTimexQuant(timexQuant);
		}
		if (!(timexFreq == null)) {
			annotation.setTimexFreq(timexFreq);
		}
		if (!(timexMod == null)) {
			annotation.setTimexMod(timexMod);
		}
		annotation.addToIndexes();
		logger.log(Level.FINE, annotation.getTimexId()+"NORMALIZATION PHASE:"+" found by:"+annotation.getFoundByRule()+" text: \""+annotation.getCoveredText()+"\" value:"+annotation.getTimexValue());
	}
}
