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
import de.unihd.dbs.uima.types.heideltime.Timex3;

public class TimexRuleMatcher {
	String timexType;
	Map<Pattern, String> hmPattern;
	Map<String, String>  hmNormalization;
	Map<String, String>  hmOffset;
	Map<String, String>  hmQuant;
	Map<String, String>  hmFreq;
	Map<String, String>  hmMod;
	Map<String, String>  hmPosConstraint;
	Logger logger;

	Pattern paRuleFeature = Pattern.compile(",([A-Z_]+)=\"(.*?)\"");
	Pattern paReadRules = Pattern.compile("RULENAME=\"(.*?)\",EXTRACTION=\"(.*?)\",NORM_VALUE=\"(.*?)\"(.*)");

	public TimexRuleMatcher(String timexType, Map<Pattern,String> hmPattern,
			Map<String, String> hmNormalization, Map<String, String> hmOffset,
			Map<String, String> hmQuant, Map<String, String> hmFreq,
			Map<String, String> hmMod, Map<String, String> hmPosConstraint) {
		this.timexType = timexType;
		this.hmPattern = hmPattern;
		this.hmNormalization = hmNormalization;
		this.hmOffset = hmOffset;
		this.hmQuant = hmQuant;
		this.hmFreq = hmFreq;
		this.hmMod = hmMod;
		this.hmPosConstraint = hmPosConstraint;
		logger = UIMAFramework.getLogger(TimexRuleMatcher.class);
	}

	private TimexRuleMatcher(String timexType) {
		this(timexType, new HashMap<Pattern, String>(), 
				new HashMap<String, String>(), new HashMap<String, String>(),
				new HashMap<String, String>(), new HashMap<String, String>(),
                new HashMap<String, String>(), new HashMap<String, String>());
	}

	public TimexRuleMatcher(String timexType, InputStreamReader istream, Map<String, String> hmAllRePattern)
	throws IOException {
		this(timexType);
		BufferedReader br = new BufferedReader(istream);
		for ( String line; (line=br.readLine()) != null; ){
			if (line.startsWith("//") || line.equals("")) {
				continue;
			}
			logger.log(Level.FINE, "DEBUGGING: reading rules..."+ line);
			// check each line for the name, extraction, and normalization part
			if (!readRule(line, hmAllRePattern)) {
				logger.log(Level.WARNING, "Cannot read the following line of " + timexType + "rules \nLine: "+line);
			}
		}
	}

	private boolean readRule(String line, Map<String, String> hmAllRePattern) {
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
		Pattern paVariable = Pattern.compile("%(re[a-zA-Z0-9]*)");
		for (MatchResult mr : findMatches(paVariable, rule_extraction)){
			logger.log(Level.FINE, "DEBUGGING: replacing patterns..."+ mr.group());
			if (!(hmAllRePattern.containsKey(mr.group(1)))){
				logger.log(Level.SEVERE, "Error creating rule:"+rule_name + "\nThe following pattern used in this rule does not exist, does it? %"+mr.group(1));
				System.exit(-1);
			}
			rule_extraction = rule_extraction.replaceAll("%"+mr.group(1), hmAllRePattern.get(mr.group(1)));
		}
		rule_extraction = rule_extraction.replaceAll(" ", "[\\\\s]+");
		Pattern pattern = null;
		try{
			pattern = Pattern.compile(rule_extraction);
		}
		catch (java.util.regex.PatternSyntaxException e){
			logger.log(Level.SEVERE, "Compiling rules resulted in errors." +
					"\nProblematic rule is "+rule_name +
					"\nCannot compile pattern: "+rule_extraction);
			e.printStackTrace();
			System.exit(-1);
		}
		
		debugSummary += rule_extraction + "\nNorm: " + rule_normalization;
	
		// get extraction part
		hmPattern.put(pattern, rule_name);
		// get normalization part
		hmNormalization.put(rule_name, rule_normalization);
									
		/////////////////////////////////////
		// CHECK FOR ADDITIONAL CONSTRAINS //
		/////////////////////////////////////
		if (!(r.group(4) == null)){
			for (MatchResult ro : findMatches(paRuleFeature, r.group(4))){
				String key = ro.group(1);
				Map<String, String> hm;
				if ("OFFSET".equals(key)) {
					hm = hmOffset;
				} else if ("NORM_QUANT".equals(key)) {
					hm = hmQuant;
				} else if ("NORM_FREQ".equals(key)) {
					hm = hmFreq;
				} else if ("NORM_MOD".equals(key)) {
					hm = hmMod;
				} else if ("POS_CONSTRAINT".equals(key)) {
					hm = hmPosConstraint;
				} else {
					logger.log(Level.WARNING, "Unknown rule feature: " + key + " with value: \"" + ro.group(2) + "\" in features: " + r.group(4));
					continue;
				}
				hm.put(rule_name, ro.group(2));
				debugSummary += "\n" + key + ": " + rule_normalization;
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
	public int findTimexes(Sentence s, JCas jcas,
			Map<String, HashMap<String,String>> hmAllNormalization, IdGenerator idGen) {
		int nAdded = 0;
		// Iterator over the rules by sorted by the name of the rules
		// this is important since later, the timexId will be used to
		// decide which of two expressions shall be removed if both
		// have the same offset
		for (Iterator<Pattern> i = sortByValue(hmPattern).iterator(); i.hasNext(); ) {
            Pattern p = (Pattern) i.next();

			for (MatchResult r : findMatches(p, s.getCoveredText())) {
				boolean infrontBehindOK = checkInfrontBehind(r, s);

				boolean posConstraintOK = true;
				// CHECK POS CONSTRAINTS
				String ruleName = hmPattern.get(p);
				if (hmPosConstraint.containsKey(ruleName)){
					posConstraintOK = checkPosConstraint(s , hmPosConstraint.get(ruleName), r, jcas);
				}
				if ((infrontBehindOK == true) && (posConstraintOK == true)) {
				
					// Offset of timex expression (in the checked sentence)
					int timexStart = r.start();
					int timexEnd   = r.end();
				
					// Normalization from Files:
				
					// Any offset parameter?
					if (hmOffset.containsKey(ruleName)){
						String offset    = hmOffset.get(ruleName);
			
						// pattern for offset information
						Pattern paOffset = Pattern.compile("group\\(([0-9]+)\\)-group\\(([0-9]+)\\)");
						for (MatchResult mr : findMatches(paOffset,offset)){
							int startOffset = Integer.parseInt(mr.group(1));
							int endOffset   = Integer.parseInt(mr.group(2));
							timexStart = r.start(startOffset);
							timexEnd   = r.end(endOffset);
						}
					}
				
					// Normalization Parameter
					if (hmNormalization.containsKey(ruleName)){
						String[] attributes = new String[4];
						attributes = getAttributesForTimexFromFile(ruleName, r, jcas, hmAllNormalization);
						addTimexAnnotation(timexStart + s.getBegin(), timexEnd + s.getBegin(), s,
								attributes[0], attributes[1], attributes[2], attributes[3], idGen.next(), ruleName, jcas);
						nAdded++;
					}
					else{
						logger.log(Level.WARNING, "SOMETHING REALLY WRONG HERE: "+hmPattern.get(p));
					}
				}
			}
		}
		return nAdded;
	}

    public static List<Pattern> sortByValue(final Map<Pattern,String> m) {
        List<Pattern> keys = new ArrayList<Pattern>();
        keys.addAll(m.keySet());
        Collections.sort(keys, new Comparator<Object>() {
            public int compare(Object o1, Object o2) {
                Object v1 = m.get(o1);
                Object v2 = m.get(o2);
                if (v1 == null) {
                    return (v2 == null) ? 0 : 1;
                }
                else if (v1 instanceof Comparable) {
                    return ((Comparable) v1).compareTo(v2);
                }
                else {
                    return 0;
                }
            }
        });
        return keys;
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
	 * Check whether the part of speech constraint defined in a rule is satisfied.
	 * @param s
	 * @param posConstraint
	 * @param m
	 * @param jcas
	 * @return
	 */
	public boolean checkPosConstraint(Sentence s, String posConstraint, MatchResult m, JCas jcas){
		boolean constraint_ok = true;
		Pattern paConstraint = Pattern.compile("group\\(([0-9]+)\\):(.*?):");
		for (MatchResult mr : findMatches(paConstraint,posConstraint)){
			int groupNumber = Integer.parseInt(mr.group(1));
			int tokenBegin = s.getBegin() + m.start(groupNumber);
			int tokenEnd   = s.getBegin() + m.end(groupNumber);
			String pos = mr.group(2);
			String pos_as_is = getPosFromMatchResult(tokenBegin, tokenEnd ,s, jcas);
			if (pos.equals(pos_as_is)){
				logger.log(Level.FINE, "POS CONSTRAINT IS VALID: pos should be "+pos+" and is "+pos_as_is);
			}
			else {
				constraint_ok = false;
				return constraint_ok;
			}
		}
		return constraint_ok;
	}


	/**
	 * Check token boundaries of expressions.
	 */
	public Boolean checkInfrontBehind(MatchResult r, Sentence s) {
		Boolean ok = true;
		if (r.start() > 0) {
			if (((s.getCoveredText().substring(r.start() - 1, r.start()).matches("\\w"))) &&
					(!(s.getCoveredText().substring(r.start() - 1, r.start()).matches("\\(")))){
				ok = false;
			}
		}
		if (r.end() < s.getCoveredText().length()) {
			if ((s.getCoveredText().substring(r.end(), r.end() + 1).matches("[°\\w]")) &&
					(!(s.getCoveredText().substring(r.end(), r.end() + 1).matches("\\)")))){
				ok = false;
			}
			if (r.end() + 1 < s.getCoveredText().length()) {
				if (s.getCoveredText().substring(r.end(), r.end() + 2).matches(
						"[\\.,]\\d")) {
					ok = false;
				}
			}
		}
		return ok;
	}

	private String getAttributeForTimexFromFile(Map<String, String> hm, String rule, MatchResult m, JCas jcas, Map<String, HashMap<String,String>> hmAllNormalization){
		if (hm.containsKey(rule)){
			return applyRuleFunctions(hm.get(rule), m, hmAllNormalization);
		}
		return "";
	}

	public String[] getAttributesForTimexFromFile(String rule, MatchResult m, JCas jcas, Map<String, HashMap<String,String>> hmAllNormalization){
		String[] attributes = new String[4];
		attributes[0] = getAttributeForTimexFromFile(hmNormalization, rule, m, jcas, hmAllNormalization);
		// For example "P24H" -> "P1D"
		attributes[0] = correctDurationValue(attributes[0]);
		attributes[1] = getAttributeForTimexFromFile(hmQuant, rule, m, jcas, hmAllNormalization);
		attributes[2] = getAttributeForTimexFromFile(hmFreq, rule, m, jcas, hmAllNormalization);
		attributes[3] = getAttributeForTimexFromFile(hmMod, rule, m, jcas, hmAllNormalization);
		return attributes;
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
	 * Identify the part of speech (POS) of a MarchResult.
	 * @param tokBegin
	 * @param tokEnd
	 * @param s
	 * @param jcas
	 * @return
	 */
	public String getPosFromMatchResult(int tokBegin, int tokEnd, Sentence s, JCas jcas){
		// get all tokens in sentence
		HashMap<Integer, Token> hmTokens = new HashMap<Integer, Token>();
		FSIterator iterTok = jcas.getAnnotationIndex(Token.type).subiterator(s);
		while (iterTok.hasNext()){
			Token token = (Token) iterTok.next();
			hmTokens.put(token.getBegin(), token);
		}
		// get correct token
		String pos = "";
		if (hmTokens.containsKey(tokBegin)){
			Token tokenToCheck = hmTokens.get(tokBegin);
			pos = tokenToCheck.getPos();
		}
		return pos;
	}


	public String applyRuleFunctions(String tonormalize, MatchResult m, Map<String, HashMap<String,String>> hmAllNormalization){
		String normalized = "";
		// pattern for normalization functions + group information
		// pattern for group information
		Pattern paNorm  = Pattern.compile("%([A-Za-z0-9]+?)\\(group\\(([0-9]+)\\)\\)");
		Pattern paGroup = Pattern.compile("group\\(([0-9]+)\\)");
		while ((tonormalize.contains("%")) || (tonormalize.contains("group"))){
			// replace normalization functions
			for (MatchResult mr : findMatches(paNorm, tonormalize)){
				logger.log(Level.FINE, "-----------------------------------" + "\n" +
						"DEBUGGING: tonormalize:"+tonormalize + "\n" +
						"DEBUGGING: mr.group():"+mr.group() + "\n" +
						"DEBUGGING: mr.group(1):"+mr.group(1) + "\n" +
						"DEBUGGING: mr.group(2):"+mr.group(2) + "\n" +
						"DEBUGGING: m.group():"+m.group() + "\n" +
						"DEBUGGING: m.group("+Integer.parseInt(mr.group(2))+"):"+m.group(Integer.parseInt(mr.group(2))) + "\n" +
						"DEBUGGING: hmR...:"+hmAllNormalization.get(mr.group(1)).get(m.group(Integer.parseInt(mr.group(2)))) + "\n" +
						"-----------------------------------");
				if (! (m.group(Integer.parseInt(mr.group(2))) == null)){
					String partToReplace = m.group(Integer.parseInt(mr.group(2))).replaceAll("[\n\\s]+", " ");
					if (!(hmAllNormalization.get(mr.group(1)).containsKey(partToReplace))){
						logger.log(Level.WARNING, "Maybe problem with normalization of the resource: "+mr.group(1) + "\n" +
								"Maybe problem with part to replace? "+partToReplace);
					}
					tonormalize = tonormalize.replace(mr.group(), hmAllNormalization.get(mr.group(1)).get(partToReplace));
				}
				else{
					logger.log(Level.FINE, "Empty part to normalize in "+mr.group(1));
					tonormalize = tonormalize.replace(mr.group(), "");
				}
			}
			// replace other groups
			for (MatchResult mr : findMatches(paGroup,tonormalize)){
				logger.log(Level.FINE, "-----------------------------------" + "\n" +
						"DEBUGGING: tonormalize:"+tonormalize + "\n" +
						"DEBUGGING: mr.group():"+mr.group() + "\n" +
						"DEBUGGING: mr.group(1):"+mr.group(1) + "\n" +
						"DEBUGGING: m.group():"+m.group() + "\n" +
						"DEBUGGING: m.group("+Integer.parseInt(mr.group(1))+"):"+m.group(Integer.parseInt(mr.group(1))) + "\n" +
						"-----------------------------------");
				tonormalize = tonormalize.replace(mr.group(), m.group(Integer.parseInt(mr.group(1))));
			}
			// replace substrings
			Pattern paSubstring = Pattern.compile("%SUBSTRING%\\((.*?),([0-9]+),([0-9]+)\\)");
			for (MatchResult mr : findMatches(paSubstring,tonormalize)){
				String substring = mr.group(1).substring(Integer.parseInt(mr.group(2)), Integer.parseInt(mr.group(3)));
				tonormalize = tonormalize.replace(mr.group(),substring);
			}
			// replace lowercase
			Pattern paLowercase = Pattern.compile("%LOWERCASE%\\((.*?)\\)");
			for (MatchResult mr : findMatches(paLowercase,tonormalize)){
				String substring = mr.group(1).toLowerCase();
				tonormalize = tonormalize.replace(mr.group(),substring);
			}
			// replace uppercase
			Pattern paUppercase = Pattern.compile("%UPPERCASE%\\((.*?)\\)");
			for (MatchResult mr : findMatches(paUppercase,tonormalize)){
				String substring = mr.group(1).toUpperCase();
				tonormalize = tonormalize.replace(mr.group(),substring);
			}
			// replace sum, concatenation
			Pattern paSum = Pattern.compile("%SUM%\\((.*?),(.*?)\\)");
			for (MatchResult mr : findMatches(paSum,tonormalize)){
				int newValue = Integer.parseInt(mr.group(1)) + Integer.parseInt(mr.group(2));
				tonormalize = tonormalize.replace(mr.group(), newValue+"");
			}
			// replace normalization function without group
			Pattern paNormNoGroup = Pattern.compile("%([A-Za-z0-9]+?)\\((.*?)\\)");
			for (MatchResult mr : findMatches(paNormNoGroup, tonormalize)){
				tonormalize = tonormalize.replace(mr.group(), hmAllNormalization.get(mr.group(1)).get(mr.group(2)));
			}
		}
		normalized = tonormalize;
		return normalized;
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
