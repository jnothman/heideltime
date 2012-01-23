package de.unihd.dbs.uima.annotator.heideltime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimexCalendar extends PartialCalendar {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3420368412349540982L;
	
	public static final int CENTURY = FIELD_COUNT;
	public static final int DECADE = FIELD_COUNT + 1;
	public static final int YEAR_UNIT = FIELD_COUNT + 2;
	public static final int HALF_YEAR = FIELD_COUNT + 3;
	public static final int QUARTER_YEAR = FIELD_COUNT + 4;
	public static final int SEASON = FIELD_COUNT + 5;
	public static final int PART_OF_DAY = FIELD_COUNT + 6;
	public static final int PART_OF_WEEK = FIELD_COUNT + 7;
	
	public static final int H1 = 1;
	public static final int H2 = 2;
	
	public static final int Q1 = 1;
	public static final int Q2 = 2;
	public static final int Q3 = 3;
	public static final int Q4 = 4;
	
	public static final int WEEKDAY = 1;
	public static final int WEEKEND = 2;
	
	public static final int MORNING = 0;
	public static final int MIDDAY = 1;
	public static final int AFTERNOON = 2;
	public static final int EVENING = 3;
	public static final int NIGHT = 4;
	
	protected static final List<String> partOfDayCodes;
	static {
		String[] tmp = {"MO", "MD", "AF", "EV", "NI"};
		partOfDayCodes = Arrays.asList(tmp);
	}
	
	public static final int NORTHERN_SPRING = 0;
	public static final int NORTHERN_SUMMER = 1;
	public static final int NORTHERN_FALL = 2;
	public static final int NORTHERN_WINTER = 3;
	
	protected static final List<String> seasonCodes;
	static {
		String[] tmp = {"SP", "SU", "FA", "WI"};
		seasonCodes = Arrays.asList(tmp);
	}
	
	public static final int SOUTHERN_SPRING = NORTHERN_FALL;
	public static final int SOUTHERN_SUMMER = NORTHERN_WINTER;
	public static final int SOUTHERN_FALL = NORTHERN_SPRING;
	public static final int SOUTHERN_WINTER = NORTHERN_SUMMER;
	
	public static final List<Implicature> TIMEX_IMPLICATURES = new ArrayList<Implicature>();
	static {
		TIMEX_IMPLICATURES.addAll(DEFAULT_IMPLICATURES);
		TIMEX_IMPLICATURES.add(new Implicature(makeMask(YEAR), makeMask(CENTURY, DECADE, YEAR_UNIT)));
		TIMEX_IMPLICATURES.add(new Implicature(makeMask(CENTURY, DECADE, YEAR_UNIT), makeMask(YEAR)));
		TIMEX_IMPLICATURES.add(new Implicature(makeMask(MONTH), makeMask(HALF_YEAR, QUARTER_YEAR, SEASON)));
		TIMEX_IMPLICATURES.add(new Implicature(makeMask(HOUR), makeMask(PART_OF_DAY)));
		TIMEX_IMPLICATURES.add(new Implicature(makeMask(DAY_OF_WEEK), makeMask(PART_OF_WEEK)));
	}
	
	private static final int PART_OF_YEAR_MASK = makeMask(MONTH, HALF_YEAR, QUARTER_YEAR, SEASON, WEEK_OF_YEAR);
	private static final int PART_OF_WEEK_MASK = makeMask(PART_OF_WEEK);
	private static final int PART_OF_DAY_MASK = makeMask(HOUR_OF_DAY, HOUR, PART_OF_DAY, AM_PM);
	private static final int EARLY_END_MASK = makeMask(HALF_YEAR, QUARTER_YEAR, SEASON, WEEK_OF_YEAR, DAY_OF_WEEK, WEEK_OF_MONTH, PART_OF_DAY, AM_PM);
	
	private int seasonOffset = 0;
	private int lowestField; 
	
	public int getLowestField() {
		return lowestField;
	}

	public void setLowestField(int lowestField) {
		this.lowestField = lowestField;
	}

	public int getSeasonOffset() {
		return seasonOffset;
	}

	protected TimexCalendar(ExtendedCalendar cal, int fieldMask, int lowestField, int seasonOffset) {
		super(TIMEX_IMPLICATURES, cal, fieldMask);
		this.seasonOffset = seasonOffset;
		this.lowestField = lowestField;
	}
	
	public TimexCalendar(String timexValue) {
		this(timexValue, true);
	}
	
	public TimexCalendar(String timexValue, boolean northernSeasons) {
		super(TIMEX_IMPLICATURES, new ExtendedCalendar());
		if (!northernSeasons) {
			seasonOffset = 2;
		}
		set(ERA, GregorianCalendar.AD);
		lowestField = parseTimex(timexValue);
	}
	
	public Object clone() {
		return new TimexCalendar((ExtendedCalendar) wrapped.clone(), fieldMask, lowestField, seasonOffset);
	}
	
	/**
	 * 
	 * @param value
	 * @return the field id of the lowest field parsed (even if unset)
	 */
	public int parseTimex(String value) {
		if (value.matches("^\\d{4}.*")) {
			set(YEAR, Integer.parseInt(value.substring(0, 4)));
		}
		else {
			if (value.matches("^\\d{2}.*")) {
				set(CENTURY, Integer.parseInt(value.substring(0, 2)));
			}
			if (value.matches("^[X0-9]{2}\\d.*")) {
				set(DECADE, Integer.parseInt(value.substring(2, 3)));
			}
			if (value.matches("^[X0-9]{3}\\d.*")) {
				set(YEAR_UNIT, Integer.parseInt(value.substring(3, 4)));
			}
		}
		if (value.length() < 5) {
			return YEAR;
		}
		
		// Trim off processed data
		value = value.substring(5);
		if (value.matches("^\\d{2}.*")) {
			set(MONTH, Integer.parseInt(value.substring(0, 2)) - 1);
		}
		else if (value.matches("^W\\d{2}.*")) {
			set(WEEK_OF_YEAR, Integer.parseInt(value.substring(1, 3)));
		}
		else if (value.matches("^H[1-2].*")) {
			set(HALF_YEAR, "1".equals(value.substring(1, 2)) ? H1 : H2);
		}
		else if (value.matches("^Q[1-4].*")) {
			set(QUARTER_YEAR, Integer.parseInt(value.substring(1, 2)));
		}
		else if (value.matches("^(SP|SU|FA|WI)")) {
			set(SEASON, seasonToInt(value.substring(0, 2)));
			return SEASON;
		}
		
		if (value.indexOf('-') < 0) {
			switch(value.charAt(0)) {
			case 'H':
				return HALF_YEAR;
			case 'Q':
				return QUARTER_YEAR;
			case 'W':
				return WEEK_OF_YEAR;
			default:
				return MONTH;
			}
		}
		
		// Trim off processed data
		value = value.substring(value.indexOf('-') + 1);
		if (value.matches("^\\d{2}.*")) {
			set(DAY_OF_MONTH, Integer.parseInt(value.substring(0, 2)));
		}
		if (value.matches("WE.*")) {
			set(PART_OF_WEEK, WEEKEND);
			return PART_OF_WEEK;
		}
		value = value.substring(2);
		
		if (!value.startsWith("T")) {
			return DATE;
		}
		if (value.matches("^T\\d{2}.*")) {
			set(HOUR_OF_DAY, Integer.parseInt(value.substring(1, 3)));
		}
		else if (value.matches("^T(MO|MD|AF|EV|NI).*")) {
			set(PART_OF_DAY, partOfDayToInt(value.substring(1, 3)));
			return PART_OF_DAY;
		}
		if (value.matches("^T\\d{2}:\\d{2}.*")) {
			set(MINUTE, Integer.parseInt(value.substring(4, 6)));
		}
		else {
			return HOUR_OF_DAY;
		}
		// TODO: handle second, millisecond
		return MINUTE;
	}
	
	public void update(TimexCalendar other, int lowestField) {
		parseTimex(other.toString(lowestField));
	}
	
	private int partOfDayToInt(String pod) {
		int res = partOfDayCodes.indexOf(pod);
		if (res < 0) {
			throw new IllegalArgumentException("Invalid part of day string: " + pod);
		}
		return res;
	}

	public int seasonToInt(String season) {
		int res = seasonCodes.indexOf(season);
		if (res < 0) {
			throw new IllegalArgumentException("Invalid season string: " + season);
		}
		return (res + seasonOffset) % 4;
	}
	
	public String seasonToString(int value) {
		value += (4 - seasonOffset);
		return seasonCodes.get(value % 4); 
	}
	
	public String partOfDayToString(int value) {
		return partOfDayCodes.get(value); 
	}
	
	private String pad2(int val) {
		return String.format("%02d", val);
	}
	
	public String toFullString() {
		StringBuilder res = new StringBuilder();
		if (has(YEAR)) {
			res.append(String.format("%04d", get(YEAR)));
		}
		else {
			res.append(has(CENTURY) ? pad2(get(CENTURY)) : "XX");
			res.append(has(DECADE) ? get(DECADE) : "X");
			res.append(has(YEAR_UNIT) ? get(YEAR_UNIT) : "X");
		}
		res.append('-');
		if (has(MONTH)) {
			res.append(pad2(get(MONTH) + 1));
		}
		else if (has(WEEK_OF_YEAR)) {
			res.append("W" + pad2(get(WEEK_OF_YEAR)));
			if (has(PART_OF_WEEK) && get(PART_OF_WEEK) == WEEKEND) { 
				res.append("-WE");
			}
			return res.toString();
		}
		else if (has(SEASON)) {
			res.append(seasonToString(get(SEASON)));
			return res.toString();
		}
		else if (has(QUARTER_YEAR)) {
			res.append("Q" + get(QUARTER_YEAR));
			return res.toString();
		}
		else if (has(HALF_YEAR)) {
			res.append("H" + get(HALF_YEAR));
			return res.toString();
		}
		else {
			res.append("XX");
		}

		res.append('-' + (has(DATE) ? pad2(get(DATE)) : "XX"));
		
		if (has(HOUR_OF_DAY)) {
			res.append('T' + pad2(get(HOUR_OF_DAY)));
		}
		else if (has(PART_OF_DAY)) {
			res.append("T" + partOfDayToString(get(PART_OF_DAY)));
			return res.toString();
		}
		else {
			res.append("TXX");
		}
		
		res.append(':' + (has(MINUTE) ? pad2(get(MINUTE)) : "XX"));
		res.append(':' + (has(SECOND) ? pad2(get(SECOND)) : "XX"));
		return res.toString();
	}
	
	public String toString() {
		return toString(lowestField);
	}
	
	private static final Map<Integer, Integer> endOffset;
	static {
		endOffset = new HashMap<Integer, Integer>();
		endOffset.put(CENTURY, 2);
		endOffset.put(DECADE, 3);
		endOffset.put(YEAR, 4);
		endOffset.put(YEAR_UNIT, 4);
		endOffset.put(MONTH, 7);
		endOffset.put(DATE, 10);
		endOffset.put(DAY_OF_WEEK, 10); //?
		endOffset.put(HOUR_OF_DAY, 13);
		endOffset.put(MINUTE, 16);
	}
	
	private String toStringMasked(int mask) {
		int tmpFieldMask = fieldMask;
		fieldMask &= mask;
		String res = toFullString();
		fieldMask = tmpFieldMask;
		return res;
	}
	
	public String toString(int lowestField) {
		int lowestMask = makeMask(lowestField);
		if (endOffset.containsKey(lowestField)) {
			return toStringMasked(~EARLY_END_MASK).substring(0, endOffset.get(lowestField));
		}
		else if ((lowestMask & PART_OF_YEAR_MASK) != 0) {
			String res = toStringMasked(~(PART_OF_YEAR_MASK | PART_OF_WEEK_MASK) | lowestMask);
			if (res.charAt(5) == 'X') {
				switch (lowestField) {
				case WEEK_OF_YEAR:
					return res.substring(0, 5) + "WXX";
				case SEASON:
					return res.substring(0, 5) + "XX"; // ??
				case HALF_YEAR:
					return res.substring(0, 5) + "HX";
				case QUARTER_YEAR:
					return res.substring(0, 5) + "QX";
				}
			}
			return res;
		}
		else if (lowestField == PART_OF_WEEK) {
			return toStringMasked(~(PART_OF_YEAR_MASK | PART_OF_WEEK_MASK) | (1 << WEEK_OF_YEAR) | lowestMask);
		}
		else if ((lowestMask & PART_OF_DAY_MASK) != 0) {
			String res = toStringMasked(~(PART_OF_DAY_MASK) | lowestMask);
			// ??
			return res;
		}
		throw new IllegalArgumentException("Unsupported lowest field: " + lowestField);
	}
	
	/* Note: compares only on commonly set fields */
	public int compareTo(TimexCalendar other) {
		return compareFieldsTo(other, ERA, CENTURY, DECADE, YEAR, HALF_YEAR, QUARTER_YEAR,
				MONTH, WEEK_OF_YEAR, SEASON, WEEK_OF_MONTH, PART_OF_WEEK, DATE, DAY_OF_WEEK,
				AM_PM, PART_OF_DAY, HOUR_OF_DAY, HOUR, MINUTE, SECOND, MILLISECOND);
	}
	
	private static class ExtendedCalendar extends GregorianCalendar {
		private static final long serialVersionUID = -1135117740051760325L;

		@Override
		public void add(int field, int value) {
			if (value == 0) {
				return;
			}
			if (field < FIELD_COUNT) {
				super.add(field, value);
				return;
			}
			switch (field) {
			case CENTURY:
				super.add(YEAR, value * 100);
				break;
			case DECADE:
				super.add(YEAR, value * 10);
				break;
			case HALF_YEAR:
				super.add(MONTH, value * 6);
				break;
			case QUARTER_YEAR:
			case SEASON:
				super.add(MONTH, value * 3);
				break;
			case YEAR_UNIT:
			case PART_OF_DAY:
			default:
				throw new IllegalArgumentException("Unhandled field: " + field);
			}
		}

		@Override
		public void roll(int field, boolean value) {
			if (field >= FIELD_COUNT) {
				throw new UnsupportedOperationException("roll is not yet available for field " + field);
			}
			super.roll(field, value);
		}

		@Override
		public void roll(int field, int value) {
			if (field >= FIELD_COUNT) {
				throw new UnsupportedOperationException("roll is not yet available for field " + field);
			}			
			super.roll(field, value);
		}

		@Override
		public int get(int field) {
			if (field < FIELD_COUNT) {
				return super.get(field);
			}
			switch (field) {
			case CENTURY:
				return super.get(YEAR) / 100;
				
			case DECADE:
				return (super.get(YEAR) % 100) / 10;
				
			case YEAR_UNIT:
				return super.get(YEAR) % 10;
				
			case HALF_YEAR:
				return super.get(MONTH) < JULY ? H1 : H2;
				
			case QUARTER_YEAR:
				int month = super.get(MONTH);
				if (month < APRIL) {
					return Q1;
				}
				else if (month < JULY) {
					return Q2;
				}
				else if (month < OCTOBER) {
					return Q3;
				}
				else {
					return Q4;
				}
				
			case SEASON:
				month = super.get(MONTH);
				return ((month + 10) / 3) % 4;
				
			case PART_OF_WEEK:
				switch(super.get(DAY_OF_WEEK)) {
				case MONDAY:
				case TUESDAY:
				case WEDNESDAY:
				case THURSDAY:
				case FRIDAY:
					return WEEKDAY;
				case SATURDAY:
				case SUNDAY:
					return WEEKEND;
				}
				
			case PART_OF_DAY:
				int hour = super.get(HOUR_OF_DAY);
				if (hour < 11) {
					return MORNING;
				} else if (hour < 13) {
					return MIDDAY;
				} else if (hour < 17) {
					return AFTERNOON;
				} else if (hour < 20) {
					return EVENING;
				} else {
					return NIGHT;
				}
			}
			throw new IllegalArgumentException("Unhandled field: " + field);
		}

		@Override
		public void set(int field, int value) {
			if (field < FIELD_COUNT) {
				super.set(field, value);
				return;
			}
			switch (field) {
			case CENTURY:
				super.set(YEAR, super.get(YEAR) % 100 + value * 100);
				break;
				
			case DECADE:
				int year = super.get(YEAR);
				super.set(YEAR, (year / 100) * 100 + value * 10 + year % 10);
				break;
				
			case YEAR_UNIT:
				super.set(YEAR, (super.get(YEAR) / 10) * 10 + value);
				break;
				
			case HALF_YEAR:
				int month = super.get(MONTH);
				if (value == H1 && month > JUNE) {
					super.set(MONTH, JANUARY);
				}
				else if (value == H2 && month < JULY) {
					super.set(MONTH, JULY);
				}
				break;
				
			case QUARTER_YEAR:
				month = super.get(MONTH);
				// always set to a 31-day month
				if (value == Q1 && month > MARCH) {
					super.set(MONTH, JANUARY);
				}
				else if (value == Q2 && (month < APRIL || month > JUNE)) {
					super.set(MONTH, MAY);
				}
				else if (value == Q3 && (month < JULY || month > SEPTEMBER)) {
					super.set(MONTH, JULY);
				}
				else if (value == Q4 && month < OCTOBER) {
					super.set(MONTH, OCTOBER);
				}
				break;
				
			case SEASON:
				month = super.get(MONTH);
				int seasonStart = value * 3 + 2;
				if (month < seasonStart || month > seasonStart + 2) {
					super.set(MONTH, seasonStart);
				}
				break;
				
			case PART_OF_WEEK:
				int dow = super.get(DAY_OF_WEEK);
				if (dow == SATURDAY || dow == SUNDAY) {
					if (value == WEEKDAY) {
						super.set(DAY_OF_WEEK, MONDAY);
					}
				}
				else if (value == WEEKEND) {
					super.set(DAY_OF_WEEK, SATURDAY);
				}
				
			case PART_OF_DAY:
				switch (value) {
				case MORNING:
					super.set(HOUR_OF_DAY, 9); break;
				case MIDDAY:
					super.set(HOUR_OF_DAY, 12); break;
				case AFTERNOON:
					super.set(HOUR_OF_DAY, 16); break;
				case EVENING:
					super.set(HOUR_OF_DAY, 18); break;
				case NIGHT:
					super.set(HOUR_OF_DAY, 22); break;
				}
				break;
				
			default:
				throw new IllegalArgumentException("Unhandled field: " + field);
			}
		}
		
//		switch (field) {
//		case CENTURY:
//		case DECADE:
//		case YEAR_UNIT:
//		case HALF_YEAR:
//		case QUARTER_YEAR:
//		case SEASON:
//		case PART_OF_WEEK:
//		case PART_OF_DAY:
//		default:
//			throw new IllegalArgumentException("Unhandled field: " + field);
//		}
	}
	
	public static void main(String[] args) {
		System.out.println(new GregorianCalendar(2009, 10, 22).get(WEEK_OF_YEAR));
		
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
		assert fmt.format(new TimexCalendar("2023-05-07").getTime()).equals("2023-05-07");
		
		System.out.println(new TimexCalendar("2123").toString(YEAR));
		System.out.println(new TimexCalendar("20XX").toString(YEAR));
		System.out.println(new TimexCalendar("2023-XX").toString(MONTH));
		System.out.println(new TimexCalendar("2023-H1").toString(HALF_YEAR));
		System.out.println(new TimexCalendar("2023-H1").toString(MONTH));
		System.out.println(new TimexCalendar("2023-12-01").toString(QUARTER_YEAR));
		System.out.println(new TimexCalendar("2023-Q2").toString(QUARTER_YEAR));
		System.out.println(new TimexCalendar("2023-WI").toString(SEASON));
		System.out.println(new TimexCalendar("2023-W04").toString(WEEK_OF_YEAR));
		System.out.println(new TimexCalendar("2023").toString(WEEK_OF_YEAR));
		System.out.println(new TimexCalendar("2023-05-07").toString(WEEK_OF_YEAR));
		System.out.println(new TimexCalendar("2023-05").toString(MONTH));
		System.out.println(new TimexCalendar("2023-05-07").toString(DATE));
		System.out.println(new TimexCalendar("XX93-04-15TNI"));
		System.out.println(new TimexCalendar("XX93-04-15T22").toString(PART_OF_DAY));
		System.out.println(new TimexCalendar("XX93-04-15T22").toString(HOUR_OF_DAY));
		System.out.println(new TimexCalendar("2123-W42-WE"));
		System.out.println(new TimexCalendar("2123-03-03").toString(WEEK_OF_YEAR));
		System.out.println(new TimexCalendar("2123-03-03").toString(PART_OF_WEEK));
		System.out.println(new TimexCalendar("2123-W42-WE").getLowestField());
		
		TimexCalendar cal = new TimexCalendar("2023-12-01");
		cal.add(QUARTER_YEAR, -1);
		System.out.println(cal.toString(QUARTER_YEAR));
	}
}
