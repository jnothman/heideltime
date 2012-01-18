package de.unihd.dbs.uima.annotator.heideltime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.cleartk.timeml.type.DocumentCreationTime;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;

import de.unihd.dbs.uima.types.heideltime.Timex3;

public class FullSpecifier {
	
	static final int CENTURY = TimexCalendar.CENTURY;
	static final int DECADE = TimexCalendar.DECADE;
	static final int YEAR = TimexCalendar.YEAR;
	static final int MONTH = TimexCalendar.MONTH;
	static final int HALF_YEAR = TimexCalendar.HALF_YEAR;
	static final int QUARTER_YEAR = TimexCalendar.QUARTER_YEAR;
	static final int WEEK_OF_YEAR = TimexCalendar.WEEK_OF_YEAR;
	static final int SEASON = TimexCalendar.SEASON;
	static final int DATE = TimexCalendar.DATE;
	static final int PART_OF_DAY = TimexCalendar.PART_OF_DAY;
	static final int HOUR_OF_DAY = TimexCalendar.HOUR_OF_DAY;
	static final int AM_PM = TimexCalendar.AM_PM;
	static final int HOUR = TimexCalendar.HOUR;
	static final int MINUTE = TimexCalendar.MINUTE;
	static final int DAY_OF_WEEK = TimexCalendar.DAY_OF_WEEK;
	
	private static class FieldValue {
		int field;
		int value;
		public FieldValue(int field, int value) {
			this.field = field;
			this.value = value;
		}
	}
	
	static final Map<String, Integer> FIELDS = new HashMap<String, Integer>();
	static final Map<String, FieldValue> FIELD_VALUES = new HashMap<String, FieldValue>();
	
	static {
		FIELDS.put("century", CENTURY);
		FIELDS.put("decade", DECADE);
		FIELDS.put("year", YEAR);
		FIELDS.put("half", HALF_YEAR);
		FIELDS.put("quarter", QUARTER_YEAR);
		FIELDS.put("season", SEASON);
		FIELDS.put("month", MONTH);
		FIELDS.put("week", WEEK_OF_YEAR);
		FIELDS.put("day", DATE);
		FIELDS.put("hour", HOUR_OF_DAY);
		FIELDS.put("minute", MINUTE);
		
		FIELD_VALUES.put("sunday", new FieldValue(DAY_OF_WEEK, Calendar.SUNDAY));
		FIELD_VALUES.put("monday", new FieldValue(DAY_OF_WEEK, Calendar.MONDAY));
		FIELD_VALUES.put("tuesday", new FieldValue(DAY_OF_WEEK, Calendar.TUESDAY));
		FIELD_VALUES.put("wednesday", new FieldValue(DAY_OF_WEEK, Calendar.WEDNESDAY));
		FIELD_VALUES.put("thursday", new FieldValue(DAY_OF_WEEK, Calendar.THURSDAY));
		FIELD_VALUES.put("friday", new FieldValue(DAY_OF_WEEK, Calendar.FRIDAY));
		FIELD_VALUES.put("saturday", new FieldValue(DAY_OF_WEEK, Calendar.SATURDAY));
		FIELD_VALUES.put("Sunday", new FieldValue(DAY_OF_WEEK, Calendar.SUNDAY));
		FIELD_VALUES.put("Monday", new FieldValue(DAY_OF_WEEK, Calendar.MONDAY));
		FIELD_VALUES.put("Tuesday", new FieldValue(DAY_OF_WEEK, Calendar.TUESDAY));
		FIELD_VALUES.put("Wednesday", new FieldValue(DAY_OF_WEEK, Calendar.WEDNESDAY));
		FIELD_VALUES.put("Thursday", new FieldValue(DAY_OF_WEEK, Calendar.THURSDAY));
		FIELD_VALUES.put("Friday", new FieldValue(DAY_OF_WEEK, Calendar.FRIDAY));
		FIELD_VALUES.put("Saturday", new FieldValue(DAY_OF_WEEK, Calendar.SATURDAY));
		
		FIELD_VALUES.put("january", new FieldValue(MONTH, Calendar.JANUARY));
		FIELD_VALUES.put("february", new FieldValue(MONTH, Calendar.FEBRUARY));
		FIELD_VALUES.put("march", new FieldValue(MONTH, Calendar.MARCH));
		FIELD_VALUES.put("april", new FieldValue(MONTH, Calendar.APRIL));
		FIELD_VALUES.put("may", new FieldValue(MONTH, Calendar.MAY));
		FIELD_VALUES.put("june", new FieldValue(MONTH, Calendar.JUNE));
		FIELD_VALUES.put("july", new FieldValue(MONTH, Calendar.JULY));
		FIELD_VALUES.put("august", new FieldValue(MONTH, Calendar.AUGUST));
		FIELD_VALUES.put("september", new FieldValue(MONTH, Calendar.SEPTEMBER));
		FIELD_VALUES.put("october", new FieldValue(MONTH, Calendar.OCTOBER));
		FIELD_VALUES.put("november", new FieldValue(MONTH, Calendar.NOVEMBER));
		FIELD_VALUES.put("december", new FieldValue(MONTH, Calendar.DECEMBER));
		
		// FIXME: Make this unstatic to handle southern seasons
		FIELD_VALUES.put("SP", new FieldValue(SEASON, TimexCalendar.NORTHERN_SPRING));
		FIELD_VALUES.put("SU", new FieldValue(SEASON, TimexCalendar.NORTHERN_SUMMER));
		FIELD_VALUES.put("FA", new FieldValue(SEASON, TimexCalendar.NORTHERN_FALL));
		FIELD_VALUES.put("WI", new FieldValue(SEASON, TimexCalendar.NORTHERN_WINTER));
	}
	
	Logger logger;
	Map<String, String> hmAllRePattern;
	TimexCalendar unsetTimex;
	
	public FullSpecifier(Map<String, String> hmAllRePattern) {
		super();
		this.hmAllRePattern = hmAllRePattern;
		this.unsetTimex = new TimexCalendar("");
		logger = UIMAFramework.getLogger(FullSpecifier.class);
	}
	
	private int getOffsetForTense(String tense, int refValue, int timeValue) {
		return getOffsetForTense(tense, new Integer(refValue).compareTo(timeValue));
	}
	
	private int getOffsetForTense(String tense, int refCmp) {
		if ("PAST".equals(tense) && refCmp < 0) {
			return -1;
		}
		if (("FUTURE".equals(tense) || "PRESENTFUTURE".equals(tense)) && refCmp > 0) {
			return +1;
		}
		return 0;
	}
	
	private class UndefValues {

		public static final int UNKNOWN = 0;
		public static final int AUTHOR_TIME = 1;
		public static final int MENTIONED_TIME = 2;
		public static final int MENTIONED_UNIT = 3;
		
		final TimexCalendar calendar;
		int field;
		int diff;
		int withRespectTo = UNKNOWN;
		boolean byValue = false;
		
		public String toString() {
			return String.format("UndefValues<cal=%s, '%s', field=%d, diff=%d, byval=%s>",
					calendar.toString(), withRespectTo, field, diff, byValue);
		}
		
		public boolean canUseDct() {
			return withRespectTo < MENTIONED_TIME;
		}
		
		public UndefValues(String timex) {
			int value = 0;
			if (timex.startsWith("UNDEF-")) {
				timex = timex.substring(6);
			}
			String remaining;
			
			if (timex.startsWith("century")) {
				field = CENTURY;
				remaining = timex.substring(7);
			}
			else if (timex.startsWith("year")) {
				field = YEAR;
				remaining = timex.substring(4);
			}
			else if (timex.startsWith("day-")) {
				field = DAY_OF_WEEK;
				Matcher m = Pattern.compile("^day-([a-z]+day).*").matcher(timex);
				if (!m.find()) {
					throw new IllegalArgumentException("Bad UNDEF: " + timex);
				}
				value = FIELD_VALUES.get(m.group(1)).value;
				remaining = timex.substring(m.end(1));
				byValue = true;
			}
			else if (timex.contains("PLUS") || timex.contains("MINUS")) {
				Matcher m = Pattern.compile("^(this|REFUNIT|REF)-(.*?)-(MINUS|PLUS)-([0-9]+).*").matcher(timex);
				if (!m.find()) {
					throw new IllegalArgumentException("Bad UNDEF: " + timex);
				}
				String wrt = m.group(1);
				if ("this".equals(wrt)) {
					withRespectTo = AUTHOR_TIME;
				}
				else if ("REF".equals(wrt)) {
					withRespectTo = MENTIONED_TIME;
				}
				else {
					withRespectTo = MENTIONED_UNIT;
				}
				field = FIELDS.get(m.group(2));
				diff = Integer.parseInt(m.group(4));
				if (m.group(3).equals("MINUS")) {
					diff = -diff;
				}
				remaining = timex.substring(m.end(4));
			}
			else {
				Matcher m = Pattern.compile("^(this|last|next)-(WI|SP|SU|FA|[A-Za-z][a-z]+)").matcher(timex);
				if (!m.find()) {
					throw new IllegalArgumentException("Bad UNDEF: " + timex);
				}
				
				withRespectTo = AUTHOR_TIME;
				String rel = m.group(1);
				diff = 0;
				if (rel.equals("last")) {
					diff = -1;
				}
				else if (rel.equals("next")) {
					diff = 1;
				}
				
				if (FIELDS.containsKey(m.group(2))) {
					field = FIELDS.get(m.group(2));
				}
				else {
					FieldValue fv = FIELD_VALUES.get(m.group(2));
					field = fv.field;
					value = fv.value;
					byValue = true;
				}
				remaining = timex.substring(m.end(2));
			}
			
			if (remaining.equals("")) {
				calendar = (TimexCalendar) unsetTimex.clone();
				calendar.setLowestField(field);
			}
			else {
				calendar = new TimexCalendar(unsetTimex.toString(field) + remaining);
			}
			if (byValue) {
				calendar.set(field, value);
			}
		}
	}

	public void process(JCas jcas, String typeToProcess) {
		
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
		TimexCalendar dct = null;
	
		//////////////////////////////////////////////
		// INFORMATION ABOUT DOCUMENT CREATION TIME //
		//////////////////////////////////////////////
		FSIterator dctIter = jcas.getAnnotationIndex(DocumentCreationTime.type).iterator();
		if (dctIter.hasNext()) {
			DocumentCreationTime dctAnnotation = (DocumentCreationTime) dctIter.next();
			String dctValue = dctAnnotation.getValue();
			if (dctValue.matches("\\d\\d\\d\\d\\d\\d\\d\\d")){
				dctValue = dctValue.substring(0, 4) + '-' + dctValue.substring(4, 6) + '-' + dctValue.substring(6, 8);
			}
			dct = new TimexCalendar(dctValue);
		}
		else{
			logger.log(Level.FINE, "No DCT available...");
		}
		boolean useDct = typeToProcess.equals("news") && (dct != null);
	
		List<TimexCalendar> previousDates = new LinkedList<TimexCalendar>();
		
		//////////////////////////////////////////////
		// go through list of Date and Time timexes //
		//////////////////////////////////////////////
		for (int i = 0; i < linearDates.size(); i++) {
			Timex3 t_i = (Timex3) linearDates.get(i);
			String value_i = t_i.getTimexValue();
			// get the last tense (depending on the part of speech tags used in front or behind the expression)
			String last_used_tense = getLastTense(t_i, jcas);

			//////////////////////////
			// DISAMBIGUATION PHASE //
			//////////////////////////
			String valueNew = value_i;
			try {
				if (value_i.startsWith("UNDEF")) {
					logger.log(Level.FINE, "\"" + t_i.getCoveredText() + "\" - " + value_i);
					TimexCalendar cal_i = processUndef(previousDates, dct, useDct, last_used_tense, value_i);
					previousDates.add(0, cal_i);
					valueNew = cal_i.toString();
				}
				else if (value_i.matches("^\\d\\d\\d\\d.*")) {
					previousDates.add(0, new TimexCalendar(value_i));
				}
			} catch (RuntimeException e) {
				logger.log(Level.WARNING, "Error while processing: " + value_i);
				e.printStackTrace();
				valueNew = value_i;
			}
			t_i.removeFromIndexes();
			logger.log(Level.FINE, t_i.getTimexId()+" DISAMBIGUATION PHASE: foundBy:"+t_i.getFoundByRule()+" text:"+t_i.getCoveredText()+" value:"+t_i.getTimexValue()+" NEW value:"+valueNew);
			t_i.setTimexValue(valueNew);
			t_i.addToIndexes();
			linearDates.set(i, t_i);
		}
	}
	
	private TimexCalendar processUndef(Collection<TimexCalendar> previousDates, TimexCalendar dct, boolean useDct, String last_used_tense, String value_i) {
		
		// Parse the different forms of UNDEF strings
		UndefValues undef = new UndefValues(value_i);
		logger.log(Level.FINE, value_i + " " + undef + " tense=" + last_used_tense + " dct=" + dct);

		TimexCalendar thisDate = undef.calendar;
		int field = undef.field;
		
		// Find a reference time: DCT or previous specified (or resolved) date
		TimexCalendar refDate;
		if (undef.canUseDct() && useDct) {
			refDate = dct;
		}
		else {
			refDate = findHavingField(previousDates, field);
		}
		
		// Resolve the uncertainties marked in undef
		if (undef.withRespectTo == UndefValues.UNKNOWN && !undef.byValue) {
			// Filling in a single field from context (century or year)
			if (useDct) {
				// Determine offset from tense
				if (field == CENTURY) {
					undef.diff = getOffsetForTense(last_used_tense,
							refDate.compareFieldsTo(thisDate, DECADE));
				}
				else {
					undef.diff = getOffsetForTense(last_used_tense,
							refDate.compareFieldsTo(thisDate, MONTH, QUARTER_YEAR, HALF_YEAR, WEEK_OF_YEAR));
				}
			}
			if (refDate != null) {
				// Set value offset from reference point
				thisDate.set(field, refDate.get(field) + undef.diff);
			}
			else if (field == CENTURY && !thisDate.has(CENTURY)) {
				thisDate.set(CENTURY, 19);
			}
			return thisDate;
		}
		
		if (undef.withRespectTo == UndefValues.MENTIONED_UNIT) {
			// e.g. "a year later" => adopt the level of detail of the reference date
			assert field == YEAR;
			if (refDate != null) {
				thisDate = (TimexCalendar) refDate.clone();
				thisDate.add(YEAR, undef.diff);
			}
			return thisDate;
		}
		
		TimexCalendar updateFrom;
		if (undef.byValue) {
			// Named season, month or day of week
			if (undef.withRespectTo == UndefValues.AUTHOR_TIME) {
				updateFrom = calculateByValue(refDate, field, undef.diff, thisDate.get(field));
			}
			else {
				// "this", "next" or "last" unknown 
				assert field == DAY_OF_WEEK;
				updateFrom = calculateUngroundedDayByValue(refDate, last_used_tense, thisDate.get(field), useDct);
			}
		}
		else if (undef.withRespectTo == UndefValues.AUTHOR_TIME || undef.withRespectTo == UndefValues.MENTIONED_TIME) {
			// this, next, last or n Xs ago (AUTHOR_TIME); or
			// same, following, previous or n Xs before (MENTIONED_TIME)
			// for year, month, day, etc. 
			updateFrom = calculateOffsetDate(refDate, field, undef.diff);
		}
		else {
			throw new IllegalArgumentException("Unhandled UNDEF case: " + undef + " (" + value_i + ")");
		}
		
		if (updateFrom != null) {
			thisDate.update(updateFrom, field);
		}
		if (field == CENTURY || field == DECADE) {
			thisDate.setLowestField(YEAR);
		}
		return thisDate;
	}

	private TimexCalendar findHavingField(Collection<TimexCalendar> list, int field) {
		for (TimexCalendar cal : list) {
			if (cal.has(field)) {
				return cal;
			}
		}
		return null;
	}
	

	private TimexCalendar calculateByValue(TimexCalendar ref, int field,
			int direction, int newValue) {
		if (ref == null) {
			return null;
		}
		TimexCalendar res = (TimexCalendar) ref.clone();
		int refValue = ref.get(field);
		
		if (field == DAY_OF_WEEK) {
			int diff = newValue - refValue;
			if (direction < 0){
				if (diff >= 0) {
					diff = diff - 7;
				}
			}
			else if (direction == 0){
				// TODO tense should be included?!	
				if (diff >= 0) {
					diff = diff - 7;
				}
				if (diff == -7) {
					diff = 0;
				}
			}
			else if (direction > 0){
				if (diff <= 0) {
					diff = diff + 7;
				}
			}
			res.add(DATE, diff);
		}
		else if (field == MONTH || field == SEASON) {
			res.set(field, newValue);
			if (direction != 0 && refValue * direction >= newValue * direction) {
				res.add(YEAR, direction);
			}
		}
		else {
			throw new IllegalArgumentException("Unhandled field: " + field);
		}
		return res;
	}
	
	private TimexCalendar calculateUngroundedDayByValue(TimexCalendar ref,
			String last_used_tense, int newValue, boolean useDct) {
		
		if (ref == null) {
			return null;
		}
		
		int refValue = ref.get(DAY_OF_WEEK);
		int diff = newValue - refValue;
		
		if (diff >= 0) {
			diff = diff - 7;
		}
		if (diff == -7) {
			diff = 0;
		}
		
		if (useDct){
			// TODO tense should be included?!
			//  Tense is FUTURE
			if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
				diff = diff + 7;
			}
		}
		
		TimexCalendar res = (TimexCalendar) ref.clone();
		res.add(DATE, diff);
		return res;
	}

	private TimexCalendar calculateOffsetDate(TimexCalendar ref, int field, int diff) {
		
		// TODO: make sure diff == 0 is handled quickly
	
		if (ref == null) {
			return null;
		}
		
		TimexCalendar res = (TimexCalendar) ref.clone();
		res.add(field, diff);
		return res;
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

	private void logNearTense(String title, Token token) {
		logger.log(Level.FINE, title + ": string:"+token.getCoveredText()+" pos:"+token.getPos() + "\n" +
				"hmAllRePattern.containsKey(tensePos4PresentFuture):"+hmAllRePattern.get("tensePos4PresentFuture") + "\n" +
				"hmAllRePattern.containsKey(tensePos4Future):"+hmAllRePattern.get("tensePos4Future") + "\n" +
				"hmAllRePattern.containsKey(tensePos4Past):"+hmAllRePattern.get("tensePos4Past") + "\n" +
				"CHECK TOKEN:"+token.getPos());
	}
}
