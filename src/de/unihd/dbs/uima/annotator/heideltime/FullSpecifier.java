package de.unihd.dbs.uima.annotator.heideltime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
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
	Map<String, String> hmAllRePattern;
	static final Map<String, String> normDayInWeek = new HashMap<String, String>();
	static final Map<String, String> normNumber = new HashMap<String, String>();
	static final Map<String, String> normMonthName = new HashMap<String, String>();
	static final Map<String, String> normMonthInSeason = new HashMap<String, String>();
	static final Map<String, String> normMonthInQuarter = new HashMap<String, String>();
	static final List<String> normSeasons = new ArrayList<String>();
	
	Logger logger;
	
	public FullSpecifier(Map<String, String> hmAllRePattern) {
		super();
		this.hmAllRePattern = hmAllRePattern;
		logger = UIMAFramework.getLogger(FullSpecifier.class);
	}
	
	static class Unit {
		Pattern extractor;
		
		public Unit(String extractor) {
			this.extractor = Pattern.compile(extractor);
		}
	}
	
	static final Map<String, Unit> UNITS = new HashMap<String, Unit>();
	
	static {
		initNormalizations();
		initUnits();
	}
	
	private static void initUnits() {
		UNITS.put("century", new Unit("^(\\d{2})...*"));
		UNITS.put("decade", new Unit("^(\\d{3})..*"));
		UNITS.put("year", new Unit("^(\\d{4}).*"));
		UNITS.put("dateYear", new Unit("^(\\d{4}.*)"));
		UNITS.put("month", new Unit("^(\\d{4}-\\d{2}).*"));
		UNITS.put("day", new Unit("^(\\d{4}-\\d{2}-\\d{2}).*"));
		UNITS.put("week", new Unit("^(\\d{4}-(?:\\d{2}-\\d{2}|W\\d{2})).*"));
		UNITS.put("quarter", new Unit("^(\\d{4}-(?:\\d{2}|Q[1-4])).*"));
		UNITS.put("dateQuarter", new Unit("^(\\d{4}-Q[1-4]).*"));
		UNITS.put("season", new Unit("^(\\d{4}-(?:\\d{2}|SP|SU|FA|WI)).*"));
	}
	
	private static void initNormalizations() {
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
		// FIXME: this is broken for the Southern Hemisphere
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
		
		normSeasons.add("SP");
		normSeasons.add("SU");
		normSeasons.add("FA");
		normSeasons.add("WI");
	
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
//				normDayInWeek.put("sunday","7");
//				normDayInWeek.put("monday","1");
//				normDayInWeek.put("tuesday","2");
//				normDayInWeek.put("wednesday","3");
//				normDayInWeek.put("thursday","4");
//				normDayInWeek.put("friday","5");
//				normDayInWeek.put("saturday","6");
//				normDayInWeek.put("Sunday","7");
//				normDayInWeek.put("Monday","1");
//				normDayInWeek.put("Tuesday","2");
//				normDayInWeek.put("Wednesday","3");
//				normDayInWeek.put("Thursday","4");
//				normDayInWeek.put("Friday","5");
//				normDayInWeek.put("Saturday","6");
	
	
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
	
	private int getOffsetForTense(String tense, int referenceValue, int timeValue) {
		if ("PAST".equals(tense) && referenceValue < timeValue) {
			return -1;
		}
		if (("FUTURE".equals(tense) || "PRESENTFUTURE".equals(tense)) && referenceValue > timeValue) {
			return +1;
		}
		return 0;
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
		boolean dctAvailable = false;
		// get the dct information
		String dctValue   = "";
		int dctCentury    = 0;
		int dctYear       = 0;
		int dctDecade     = 0;
		int dctMonth      = 0;
		int dctDay	= 0;
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
			}else{
				dctCentury   = Integer.parseInt(dctValue.substring(0, 2));
				dctYear      = Integer.parseInt(dctValue.substring(0, 4));
				dctDecade    = Integer.parseInt(dctValue.substring(2, 3));
				dctMonth     = Integer.parseInt(dctValue.substring(5, 7));
				dctDay       = Integer.parseInt(dctValue.substring(8, 10));
			}
			logger.log(Level.FINE, "dctCentury:"+dctCentury);
			logger.log(Level.FINE, "dctYear:"+dctYear);
			logger.log(Level.FINE, "dctDecade:"+dctDecade);
			logger.log(Level.FINE, "dctMonth:"+dctMonth);
			logger.log(Level.FINE, "dctDay:"+dctDay);
			
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
		boolean useDct = typeToProcess.equals("news") && dctAvailable;
	
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
			if (value_i.startsWith("UNDEF-year") || value_i.startsWith("UNDEF-century")){
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
			try {
				if (value_i.startsWith("UNDEF-year")){
					String newYearValue = dctYear+"";
					if (useDct) {
						if (viHasMonth && !viHasSeason) {
							newYearValue = dctYear + getOffsetForTense(last_used_tense, dctMonth, viThisMonth) + "";
						}
						if (viHasQuarter){
							newYearValue = dctYear + getOffsetForTense(last_used_tense,
									Integer.parseInt(dctQuarter.substring(1)),
									Integer.parseInt(viThisQuarter.substring(1))) + "";
						}
						if (viHasHalf){
							newYearValue = dctYear + getOffsetForTense(last_used_tense,
									Integer.parseInt(dctHalf.substring(1)),
									Integer.parseInt(viThisHalf.substring(1))) + "";
						}
						if (!viHasMonth && !viHasDay && viHasSeason) {
							// TODO check tenses?
							newYearValue = dctYear+"";
						}
						// vi has week
						if (viHasWeek){
							newYearValue = dctYear+"";
						}
					}
					else {
						newYearValue = getLastMentionedX(linearDates, i, "year");
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
				else if (value_i.startsWith("UNDEF-century")){
					String newCenturyValue = dctCentury+"";
					int viThisDecade = Integer.parseInt(value_i.substring(13, 14));
					// NEWS DOCUMENTS
					if (useDct){
						logger.log(Level.FINE, "dctCentury"+dctCentury);
						newCenturyValue = dctCentury+"";
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
						for (MatchResult mr : HeidelTime.findMatches(Pattern.compile("^(UNDEF-(this|REFUNIT|REF)-(.*?)-(MINUS|PLUS)-([0-9]+)).*"), value_i)){
							String ltn = mr.group(2);
							String unit = mr.group(3);
							int diff    = Integer.parseInt(mr.group(5));
							if ("MINUS".equals(mr.group(4))) {
								diff = -diff;
							}
							
							// check for REFUNIT (only allowed for "year")
							if (ltn.equals("REFUNIT") && unit.equals("year")){
								String dateWithYear = getLastMentionedX(linearDates, i, "dateYear");
								if (dateWithYear.equals("")){
									valueNew = valueNew.replace(mr.group(1), "XXXX");
								}
								else{
									int yearNew = Integer.parseInt(dateWithYear.substring(0,4)) + diff;
									valueNew = valueNew.replace(mr.group(1), yearNew + dateWithYear.substring(4));
								}
							} else {
								valueNew = valueNew.replace(mr.group(1), calculateOffsetDate(linearDates, i,
										unit, diff, useDct && ltn.equals("this"),
										dctCentury, dctDecade, dctYear,
										dctQuarter, dctMonth, dctWeek, dctDay));
							}
						}
					}
					
					else if (value_i.startsWith("UNDEF-day-")) {
						for (MatchResult mr : HeidelTime.findMatches(Pattern.compile("^(UNDEF-day-([a-z]+day)).*"), value_i)){
							valueNew = valueNew.replace(mr.group(1), calculateNamedDayUndirected(linearDates, i,
									mr.group(2), last_used_tense, useDct,
									dctYear, dctMonth, dctDay, dctWeekday));
						}
					}

					else {
						Matcher mr = Pattern.compile("^UNDEF-(this|last|next)-(WI|SP|SU|FA|[A-Za-z][a-z]+)").matcher(value_i);
						if (!mr.find()) {
							logger.log(Level.WARNING, "ATTENTION: UNDEF value for: " + valueNew+" is not handled in disambiguation phase!");
							continue;
						}
						
						int diff = relativeTermOffset(mr.group(1));
						
						String unit = mr.group(2);
						
						if (UNITS.containsKey(unit)) {
							valueNew = valueNew.replace(mr.group(), calculateOffsetDate(linearDates, i,
									unit, diff, useDct,
									dctCentury, dctDecade, dctYear,
									dctQuarter, dctMonth, dctWeek, dctDay));
						}
						else if (normMonthName.containsKey(unit)) {
							valueNew = valueNew.replace(mr.group(), calculateNamedMonth(linearDates, i,
									unit, diff, useDct,
									dctYear, dctMonth));
						}
						else if (normMonthInSeason.containsValue(unit)) { // TODO: avoid containsValue
							valueNew = valueNew.replace(mr.group(), calculateNamedSeason(linearDates, i,
									unit, diff, useDct,
									dctYear, dctSeason));
						}	
						else if (normDayInWeek.containsKey(unit)) {
							valueNew = valueNew.replace(mr.group(), calculateNamedDay(linearDates, i,
									unit, diff, useDct,
									dctYear, dctMonth, dctDay, dctWeekday));
						}
						else {
							throw new IllegalArgumentException("Unknown unit/reference: " + unit + " in (" + mr.group() + ")");
						}				
					}
				}
			} catch (RuntimeException e) {
				logger.log(Level.WARNING, "Error while processing: " + value_i);
				e.printStackTrace();
			}
			t_i.removeFromIndexes();
			logger.log(Level.FINE, t_i.getTimexId()+" DISAMBIGUATION PHASE: foundBy:"+t_i.getFoundByRule()+" text:"+t_i.getCoveredText()+" value:"+t_i.getTimexValue()+" NEW value:"+valueNew);
			t_i.setTimexValue(valueNew);
			t_i.addToIndexes();
			linearDates.set(i, t_i);
		}
	}
	
	private CharSequence calculateNamedDay(List<Timex3> linearDates, int i,
			String newWeekday, int direction, boolean useDct, int dctYear, int dctMonth,
			int dctDay, int dctWeekday) {
		int newWeekdayInt = Integer.parseInt(normDayInWeek.get(newWeekday));

		// TODO the calculation is strange, but works
		// TODO tense should be included?!
		// TODO should "last Tuesday" mean immediately preceding, or Tuesday in preceding week? 
		
		String refDate = dctYear + "-" + dctMonth + "-" + dctDay;
		int refWeekday = dctWeekday;
		
		if (!useDct) {
			refDate = getLastMentionedX(linearDates, i, "day");
			if (refDate.equals("")){
				return "XXXX-XX-XX";
			}
			refWeekday = getWeekdayOfDate(refDate);
		}
		
		int diff = newWeekdayInt - refWeekday;
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
		return getXNextDay(refDate, diff);
	}
	
	private String calculateNamedDayUndirected(List<Timex3> linearDates, int i,
			String newWeekday, String last_used_tense, boolean useDct, int dctYear, int dctMonth,
			int dctDay, int dctWeekday) {
		
		int newWeekdayInt = Integer.parseInt(normDayInWeek.get(newWeekday));
		if (useDct){
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
			return getXNextDay(dctYear + "-" + dctMonth + "-"+ dctDay, diff);
		}
		else{
			// TODO tense should be included?!
			String lmDay     = getLastMentionedX(linearDates, i, "day");
			if (lmDay.equals("")){
				return "XXXX-XX-XX";
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
				return getXNextDay(lmDay, diff);
			}
		}
	}

	private String calculateNamedSeason(List<Timex3> linearDates, int i,
			String newSeason, int direction, boolean useDct, int dctYear, String dctSeason) {
		
		int refYear = dctYear;
		int refSeason = normSeasons.indexOf(dctSeason);
		int newSeasonInt = normSeasons.indexOf(newSeason);
		if (!useDct) {
			String lmSeason = getLastMentionedX(linearDates, i, "season");
			if (lmSeason.equals("")){
				return "XXXX-XX";
			}
			refYear = Integer.parseInt(lmSeason.substring(0,4));
			refSeason = normSeasons.indexOf(lmSeason.substring(5,7));
		}
		if (direction != 0 && refSeason * direction >= newSeasonInt * direction) {
			return refYear + direction + "-" + newSeason;
		}
		return refYear + "-" + newSeason;
	}

	private String calculateNamedMonth(List<Timex3> linearDates, int i,
			String monthName, int direction, boolean useDct, int dctYear, int dctMonth) {
		String newMonth = normMonthName.get((monthName));
		int newMonthInt = Integer.parseInt(newMonth);
		
		int refYear = dctYear;
		int refMonth = dctMonth;
		if (!useDct) {
			String lmMonth = getLastMentionedX(linearDates, i, "month");
			if (lmMonth.equals("")){
				return "XXXX-XX";
			}
			refYear = Integer.parseInt(lmMonth.substring(0,4));
			refMonth = Integer.parseInt(lmMonth.substring(5,7));
		}
		if (direction != 0 && refMonth * direction >= newMonthInt * direction) {
			return refYear + direction + "-" + newMonth;
		}
		return refYear + "-" + newMonth;
	}

	private int relativeTermOffset(String val) {
		if (val.equals("last")) {
			return -1;
		}
		else if (val.equals("next")) {
			return 1;
		}
		return 0;
	}

	private String calculateOffsetDate(List<Timex3> linearDates, int i, String unit, int diff,
			boolean useDct, int dctCentury, int dctDecade, int dctYear,
			String dctQuarter, int dctMonth, int dctWeek, int dctDay) {
		
		// TODO: make sure diff == 0 is handled quickly
	
		if (unit.equals("century")){
			if (useDct){
				return dctCentury + diff + "XX";
			}
			else{
				String lmCentury = getLastMentionedX(linearDates, i, "century");
				if (lmCentury.equals("")){
					return "XX";
				}
				else{
					lmCentury = Integer.parseInt(lmCentury) + diff + "XX";
					return lmCentury;
				}
			}
		}
		else if (unit.equals("decade")){
			if (useDct){
				return dctYear / 10 + diff +"X";
			}
			else{
				String lmDecade = getLastMentionedX(linearDates, i, "decade");
				if (lmDecade.equals("")){
					return "XXX";
				}
				else{
					lmDecade = Integer.parseInt(lmDecade) + diff + "X";
					return lmDecade;
				}
			}
		}
		else if (unit.equals("year")){
			if (useDct){
				return dctYear + diff + "";
			}
			else{
				String lmYear = getLastMentionedX(linearDates, i, "year");
				if (lmYear.equals("")){
					return "XXXX";
				}
				else{
					return Integer.parseInt(lmYear) + diff + "";
				}
			}
		}
		else if (unit.equals("quarter")){
			int intYear = 0;
			int intQuarter = 0;
			if (useDct){
				intYear    = dctYear;
				intQuarter = Integer.parseInt(dctQuarter.substring(1));
			}
			else{
				String lmQuarter = getLastMentionedX(linearDates, i, "quarter");
				if (lmQuarter.equals("")){
					return "XXXX-XX";
				}
				else{
					intYear    = Integer.parseInt(lmQuarter.substring(0, 4));
					intQuarter = Integer.parseInt(lmQuarter.substring(6));
				}
			}
			int diffQuarters = (Math.abs(diff) % 4) * (diff < 0 ? -1 : 1);
			diff -= diffQuarters;
			int diffYears    = diff / 4;
			intYear    = intYear + diffYears;
			intQuarter = intQuarter + diffQuarters;
			return intYear+"-Q"+intQuarter;
		}
		else if (unit.equals("month")){
			if (useDct){
				return getXNextMonth(dctYear + "-" + normNumber.get(dctMonth+""), diff);
			}
			else{
				String lmMonth = getLastMentionedX(linearDates, i, "month");
				if (lmMonth.equals("")){
					return "XXXX-XX";
				}
				else{
					return getXNextMonth(lmMonth, diff);
				}
			}
		}
		else if (unit.equals("week")){
			if (useDct){
//				return getXNextDay(dctYear + "-" + normNumber.get(dctMonth+"") + "-"	+ dctDay, diff * 7);
				return getXNextWeek(dctYear+"-W"+normNumber.get(dctWeek+""), diff);
			}
			else{
				String lmWeek = getLastMentionedX(linearDates, i, "day");
				if (lmWeek.equals("")){
					return "XXXX-WXX";
				}
				else{
//					return getXNextDay(lmDay, diff * 7);
					return getXNextWeek(lmWeek, diff);
				}
			}
		}
		else if (unit.equals("day")){
			if (useDct){
				return getXNextDay(dctYear + "-" + normNumber.get(dctMonth+"") + "-"	+ dctDay, diff);
			}
			else{
				String lmDay = getLastMentionedX(linearDates, i, "day");
				if (lmDay.equals("")){
					return "XXXX-XX-XX";
				}
				else{
					return getXNextDay(lmDay, diff);
				}
			}
		}
		throw new IllegalArgumentException("Could not determine date offset by " + diff + " " + unit + " (useDct=" + useDct + ")");
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

	/**
	 * The value of the x of the last mentioned Timex is calculated.
	 * @param linearDates
	 * @param i
	 * @param x
	 * @return
	 */
	public String getLastMentionedX(List<Timex3> linearDates, int i, String x){
	
		String xValue = getLastMentionedX(linearDates.get(i),
				linearDates.listIterator(i), UNITS.get(x).extractor);
		
		// Change full date to W/Q/S representation
		if ("week".equals(x) && !xValue.contains("W")) {
			xValue = xValue.substring(0, 4) + "-W" + getWeekOfDate(xValue);
		}
		else if ("quarter".equals(x) && !xValue.contains("Q")) {
			xValue = xValue.substring(0, 4) + "-Q" + normMonthInQuarter.get(xValue.substring(5, 7));
		}
		else if ("season".equals(x) && !xValue.contains("S")) {
			xValue = xValue.substring(0, 4) + "-S" + normMonthInSeason.get(xValue.substring(5, 7));
		}
		return xValue;
	}

	private String getLastMentionedX(Timex3 t_i, ListIterator<Timex3> iter, Pattern xPattern) {
		while (iter.hasPrevious()) {
			Timex3 timex = iter.previous();
			if (timex.getBegin() == t_i.getBegin()) {
				continue;
			}
			Matcher m = xPattern.matcher(timex.getTimexValue());
			if (m.find()) {
				return m.group(1);
			}
		}
		return "";
	}
	
	private String getXNext(String date, String fmt, int field, int offset) {
		SimpleDateFormat formatter = new SimpleDateFormat(fmt);
		Calendar c = Calendar.getInstance();
		try {
			c.setTime(formatter.parse(date));
			c.add(field, offset);
			c.getTime();
			return formatter.format(c.getTime());
		} catch (ParseException e) {
			e.printStackTrace();
			return "";
		}
	}

	/**
	 * get the x-next day of date.
	 *
	 * @param date
	 * @param x
	 * @return
	 */
	public String getXNextDay(String date, Integer x) {
		return getXNext(date, "yyyy-MM-dd", Calendar.DAY_OF_MONTH, x);
	}

	/**
	 * get the x-next month of date
	 *
	 * @param date
	 * @param x
	 * @return
	 */
	public String getXNextMonth(String date, Integer x) {
		return getXNext(date, "yyyy-MM", Calendar.MONTH, x);
	}

	public String getXNextWeek(String date, Integer x){
		String res = getXNext(date.replace("-W", "-"), "yyyy-w", Calendar.WEEK_OF_YEAR, x);
		return res.substring(0,4) + "-W" + normNumber.get(res.substring(5));
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
}
