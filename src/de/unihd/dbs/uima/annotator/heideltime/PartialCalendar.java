package de.unihd.dbs.uima.annotator.heideltime;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class PartialCalendar extends Calendar {

	private static final long serialVersionUID = 1L;
	protected final Calendar wrapped;
	protected final Collection<Implicature> implicatures;
	protected int fieldMask;
	
	public static class Implicature {
		int condition;
		int implies;
		
		public Implicature(int[] conditionFields, int[] impliesFields) {
			this(makeMask(conditionFields), makeMask(impliesFields));
		}
		
		public Implicature(int condition, int implies) {
			this.condition = condition;
			this.implies = implies;
		}
	}
	
	public static final List<Implicature> DEFAULT_IMPLICATURES = new ArrayList<Implicature>();
	static {
		DEFAULT_IMPLICATURES.add(new Implicature(makeMask(ERA, YEAR, MONTH, DATE), makeMask(DAY_OF_WEEK, DAY_OF_WEEK_IN_MONTH, DAY_OF_YEAR, WEEK_OF_YEAR, WEEK_OF_MONTH)));
		DEFAULT_IMPLICATURES.add(new Implicature(makeMask(ERA, YEAR, DAY_OF_YEAR), makeMask(MONTH, DATE)));
		DEFAULT_IMPLICATURES.add(new Implicature(makeMask(ERA, YEAR, DAY_OF_WEEK, WEEK_OF_YEAR), makeMask(MONTH, DATE)));
		DEFAULT_IMPLICATURES.add(new Implicature(makeMask(ERA, YEAR, MONTH, DAY_OF_WEEK, WEEK_OF_MONTH), makeMask(DATE)));
		DEFAULT_IMPLICATURES.add(new Implicature(makeMask(AM_PM, HOUR), makeMask(HOUR_OF_DAY)));
		DEFAULT_IMPLICATURES.add(new Implicature(makeMask(HOUR_OF_DAY), makeMask(AM_PM, HOUR)));
	}
	
	public PartialCalendar(Collection<Implicature> implicatures, Calendar wrapped) {
		this(implicatures, wrapped, 0);
	}
	
	protected PartialCalendar(Collection<Implicature> implicatures, Calendar wrapped, int fieldMask) {
		this.implicatures = implicatures;
		this.wrapped = wrapped;
		this.fieldMask = fieldMask;
	}
	
	public static int makeMask(int... fields) {
		int res = 0;
		for (int field : fields) {
			res |= (1 << field);
		}
		return res;
	}
	
	public void set(int field, int value) {
		wrapped.set(field, value);
		markSet(field);
	}
	
	public void update(PartialCalendar other) {
		int otherFieldMask = other.fieldMask;
		fieldMask |= otherFieldMask;
		markImplicatures();
		int curFieldMask = 1;
		int curField = 0;
		while (otherFieldMask != 0) {
			if ((curFieldMask & otherFieldMask) != 0) {
				wrapped.set(curField, other.get(curField));
				otherFieldMask &= ~curFieldMask;
			}
			curField ++;
			curFieldMask <<= 1;
		}
	}
	
	public void markSet(int field) {
		int oldMask = fieldMask;
		fieldMask |= (1 << field);
		if (oldMask != fieldMask) {
			markImplicatures();
		}
	}
	
	protected void markImplicatures() {
		int oldMask;
		do {
			oldMask = fieldMask;
			for (Implicature impl : implicatures) {
				if ((fieldMask & impl.condition) == impl.condition) {
					fieldMask |= impl.implies;
				}
			}
		} while (oldMask != fieldMask);
	}
	
	public boolean has(int field) {
		return (fieldMask & (1 << field)) != 0;
	}
	
	public boolean hasAny(int... fields) {
		return (fieldMask & makeMask(fields)) != 0;
	}

	public boolean hasAll(int... fields) {
		int mask = makeMask(fields);
		return (fieldMask & mask) == mask;
	}
	
	public int compareFieldsTo(PartialCalendar other, int... fields) {
		for (int field : fields) {
			if (has(field) && other.has(field)) {
				int thisVal = get(field);
				int otherVal = other.get(field);
				if (thisVal > otherVal) {
					return 1;
				}
				else if (otherVal > thisVal) { 
					return -1;
				}
			}
		}
		return 0;
	}
	
	public Object clone() {
		return new PartialCalendar(implicatures, (Calendar) wrapped.clone(), fieldMask);
	}

	public String toString() {
		// TODO
		return wrapped.toString();
	}
	
	@Override
	protected void computeFields() {
	}

	@Override
	protected void computeTime() {
		
	}

	// Unmodified delegate methods //
	public void add(int field, int amount) {
		wrapped.add(field, amount);
	}

	public boolean after(Object when) {
		return wrapped.after(when);
	}

	public boolean before(Object when) {
		return wrapped.before(when);
	}

	/**
	 * Compares the underlying time without regard to set and unset fields.
	 */
	public int compareTo(Calendar anotherCalendar) {
		return wrapped.compareTo(anotherCalendar);
	}

	public boolean equals(Object obj) {
		return wrapped.equals(obj);
	}

	public int get(int field) {
		return wrapped.get(field);
	}

	public int getActualMaximum(int field) {
		return wrapped.getActualMaximum(field);
	}

	public int getActualMinimum(int field) {
		return wrapped.getActualMinimum(field);
	}

	public String getDisplayName(int field, int style, Locale locale) {
		return wrapped.getDisplayName(field, style, locale);
	}

	public Map<String, Integer> getDisplayNames(int field, int style,
			Locale locale) {
		return wrapped.getDisplayNames(field, style, locale);
	}

	public int getFirstDayOfWeek() {
		return wrapped.getFirstDayOfWeek();
	}

	public int getGreatestMinimum(int field) {
		return wrapped.getGreatestMinimum(field);
	}

	public int getLeastMaximum(int field) {
		return wrapped.getLeastMaximum(field);
	}

	public int getMaximum(int field) {
		return wrapped.getMaximum(field);
	}

	public int getMinimalDaysInFirstWeek() {
		return wrapped.getMinimalDaysInFirstWeek();
	}

	public int getMinimum(int field) {
		return wrapped.getMinimum(field);
	}

	public long getTimeInMillis() {
		return wrapped.getTimeInMillis();
	}

	public TimeZone getTimeZone() {
		return wrapped.getTimeZone();
	}

	public int hashCode() {
		return wrapped.hashCode();
	}

	public boolean isLenient() {
		return wrapped.isLenient();
	}

	public void roll(int field, boolean up) {
		wrapped.roll(field, up);
	}

	public void roll(int field, int amount) {
		wrapped.roll(field, amount);
	}

	public void setFirstDayOfWeek(int value) {
		wrapped.setFirstDayOfWeek(value);
	}

	public void setLenient(boolean lenient) {
		wrapped.setLenient(lenient);
	}

	public void setMinimalDaysInFirstWeek(int value) {
		wrapped.setMinimalDaysInFirstWeek(value);
	}

	public void setTimeInMillis(long millis) {
		wrapped.setTimeInMillis(millis);
	}

	public void setTimeZone(TimeZone value) {
		wrapped.setTimeZone(value);
	}	
	
	public static void main(String[] args) {
		Calendar c = new GregorianCalendar();
		PartialCalendar partial = new PartialCalendar(PartialCalendar.DEFAULT_IMPLICATURES, c);
		partial.set(ERA, GregorianCalendar.AD);
		partial.set(YEAR, 2001);
		partial.set(MONTH, 05);
		partial.set(DATE, 05);
		System.out.println(partial.has(YEAR));
		System.out.println(partial.has(DAY_OF_WEEK));
	}
}
