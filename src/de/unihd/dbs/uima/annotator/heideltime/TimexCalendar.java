package de.unihd.dbs.uima.annotator.heideltime;

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
	
	public static final int H1 = 1;
	public static final int H2 = 2;
	
	public static final int Q1 = 1;
	public static final int Q2 = 2;
	public static final int Q3 = 3;
	public static final int Q4 = 4;
	
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
	}
	
	private static final int PART_OF_YEAR_MASK = makeMask(MONTH, HALF_YEAR, QUARTER_YEAR, SEASON, WEEK_OF_YEAR);
	private static final int PART_OF_DAY_MASK = makeMask(HOUR_OF_DAY, HOUR, PART_OF_DAY, AM_PM);
	private static final int EARLY_END_MASK = makeMask(HALF_YEAR, QUARTER_YEAR, SEASON, WEEK_OF_YEAR, DAY_OF_WEEK, WEEK_OF_MONTH, PART_OF_DAY, AM_PM);
	
	int seasonOffset = 0;
	
	public TimexCalendar(String timexValue) {
		this(timexValue, true);
	}
	
	public TimexCalendar(String timexValue, boolean northernSeasons) {
		super(TIMEX_IMPLICATURES, new ExtendedCalendar());
		parseTimex(timexValue);
		if (!northernSeasons) {
			seasonOffset = 2;
		}
	}
	
	public void parseTimex(String value) {
		set(ERA, GregorianCalendar.AD);
		if (value.matches("^\\d{4}.*")) {
			set(YEAR, Integer.parseInt(value.substring(0, 4)));
		}
		else {
			if (value.matches("^\\d{2}[X0-9]{2}.*")) {
				set(CENTURY, Integer.parseInt(value.substring(0, 2)));
			}
			if (value.matches("^[X0-9]{2}\\d[X0-9].*")) {
				set(DECADE, Integer.parseInt(value.substring(2, 3)));
			}
			if (value.matches("^[X0-9]{3}\\d.*")) {
				set(YEAR_UNIT, Integer.parseInt(value.substring(3, 4)));
			}
		}
		if (value.length() < 5) {
			return;
		}
		value = value.substring(5);
		
		if (value.matches("^\\d{2}.*")) {
			set(MONTH, Integer.parseInt(value.substring(0, 2)));
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
		}
		
		if (value.indexOf('-') < 0) {
			return;
		}
		value = value.substring(value.indexOf('-') + 1);
		if (value.matches("^\\d{2}.*")) {
			set(DAY_OF_MONTH, Integer.parseInt(value.substring(0, 2)));
			value = value.substring(2);
		}
		if (!value.startsWith("T")) {
			return;
		}
		if (value.matches("^T\\d{2}.*")) {
			set(HOUR_OF_DAY, Integer.parseInt(value.substring(1, 3)));
		}
		else if (value.matches("^T(MO|MD|AF|EV|NI).*")) {
			set(PART_OF_DAY, partOfDayToInt(value.substring(1, 3)));
		}
		if (value.matches("^T\\d{2}:\\d{2}.*")) {
			set(MINUTE, Integer.parseInt(value.substring(4, 6)));
		}
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
		return (0 + seasonOffset) % 4;
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
	
	public String toString() {
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
			res.append(pad2(get(MONTH)));
		}
		else if (has(WEEK_OF_YEAR)) {
			res.append("W" + pad2(get(WEEK_OF_YEAR)));
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
			res.append("H" + get(QUARTER_YEAR));
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
	
	private static final Map<Integer, Integer> endOffset;
	static {
		endOffset = new HashMap<Integer, Integer>();
		endOffset.put(YEAR, 4);
		endOffset.put(YEAR_UNIT, 4);
		endOffset.put(MONTH, 7);
		endOffset.put(DATE, 10);
		endOffset.put(HOUR_OF_DAY, 13);
		endOffset.put(MINUTE, 16);
	}
	
	private String toStringMasked(int mask) {
		int tmpFieldMask = fieldMask;
		fieldMask &= mask;
		String res = toString();
		fieldMask = tmpFieldMask;
		return res;
	}
	
	public String toString(int lowestValue) {
		int lowestMask = makeMask(lowestValue);
		if (endOffset.containsKey(lowestValue)) {
			return toStringMasked(~EARLY_END_MASK).substring(0, endOffset.get(lowestValue));
		}
		else if ((lowestMask & PART_OF_YEAR_MASK) != 0) {
			String res = toStringMasked(~(PART_OF_YEAR_MASK ^ lowestMask));
			if (res.charAt(5) == 'X') {
				switch (lowestValue) {
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
		else if ((lowestMask & PART_OF_DAY_MASK) != 0) {
			String res = toStringMasked(~(PART_OF_DAY_MASK ^ lowestMask));
			// ??
			return res;
		}
		return null;
	}
	
	private static class ExtendedCalendar extends GregorianCalendar {
		@Override
		public void add(int field, int value) {
			if (field >= FIELD_COUNT) {
				throw new UnsupportedOperationException("add is not yet available for field " + field);
			}
			super.add(field, value);
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
			if (field == CENTURY) {
				return super.get(YEAR) / 100;
			}
			else if (field == DECADE) {
				return (super.get(YEAR) % 100) / 10;
			}
			else if (field == YEAR_UNIT) {
				return super.get(YEAR) % 10;
			}
			else if (field == HALF_YEAR) {
				return super.get(MONTH) < 7 ? H1 : H2;
			}
			else if (field == QUARTER_YEAR) {
				int month = super.get(MONTH);
				if (month < 4) {
					return Q1;
				}
				else if (month < 7) {
					return Q2;
				}
				else if (month < 10) {
					return Q3;
				}
				else {
					return Q4;
				}
			}
			else if (field == SEASON) {
				int month = super.get(MONTH);
				return ((month + 9) / 3) % 4;
			}
			else if (field == PART_OF_DAY) {
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
			}
			else if (field == CENTURY) {
				super.set(YEAR, super.get(YEAR) % 100 + value * 100);
			}
			else if (field == DECADE) {
				int year = super.get(YEAR);
				super.set(YEAR, (year / 100) * 100 + value * 10 + year % 10);
			}
			else if (field == YEAR_UNIT) {
				super.set(YEAR, (super.get(YEAR) / 10) * 10 + value);
			}
			else if (field == HALF_YEAR) {
				int month = super.get(MONTH);
				if (value == H1 && month > JUNE) {
					super.set(MONTH, 1);
				}
				else if (value == H2 && month < JULY) {
					super.set(MONTH, 7);
				}
			}
			else if (field == QUARTER_YEAR) {
				int month = super.get(MONTH);
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
			}
			else if (field == SEASON) {
				int month = super.get(MONTH);
				int seasonStart = value * 3 + 2;
				if (month < seasonStart || month > seasonStart + 2) {
					super.set(MONTH, seasonStart);
				}
			}
			else if (field == PART_OF_DAY) {
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
			}
			else {
				throw new IllegalArgumentException("Unhandled field: " + field);
			}
		}
		
//		if (field == CENTURY) {
//			
//		}
//		else if (field == DECADE) {
//			
//		}
//		else if (field == YEAR_UNIT) {
//			
//		}
//		else if (field == HALF_YEAR) {
//			
//		}
//		else if (field == QUARTER_YEAR) {
//			
//		}
//		else if (field == SEASON) {
//			
//		}
//		else if (field == PART_OF_DAY) {
//			
//		}
//		throw new IllegalArgumentException("Unhandled field: " + field);
	}
	
	public static void main(String[] args) {
		System.out.println(new TimexCalendar("2123").toString(YEAR));
		System.out.println(new TimexCalendar("20XX").toString(YEAR));
		System.out.println(new TimexCalendar("2023-XX").toString(MONTH));
		System.out.println(new TimexCalendar("2023-H1").toString(HALF_YEAR));
		System.out.println(new TimexCalendar("2023-H1").toString(MONTH));
		System.out.println(new TimexCalendar("2023-Q2").toString(QUARTER_YEAR));
		System.out.println(new TimexCalendar("2023-WI").toString(SEASON));
		System.out.println(new TimexCalendar("2023-W04").toString(WEEK_OF_YEAR));
		System.out.println(new TimexCalendar("2023").toString(WEEK_OF_YEAR));
		System.out.println(new TimexCalendar("2023-05-07").toString(WEEK_OF_YEAR));
		System.out.println(new TimexCalendar("2023-05").toString(MONTH));
		System.out.println(new TimexCalendar("2023-05-07").toString(DATE));
		System.out.println(new TimexCalendar("XX93-04-15TNI").toString(PART_OF_DAY));
		System.out.println(new TimexCalendar("XX93-04-15T22").toString(PART_OF_DAY));
		System.out.println(new TimexCalendar("XX93-04-15T22").toString(HOUR_OF_DAY));
	}
}
