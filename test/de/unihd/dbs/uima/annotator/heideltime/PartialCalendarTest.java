package de.unihd.dbs.uima.annotator.heideltime;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.Test;

import de.unihd.dbs.uima.annotator.heideltime.PartialCalendar.Implicature;

public class PartialCalendarTest {
	
	private List<PartialCalendar.Implicature> firstImplicature() {
		List<PartialCalendar.Implicature> implicatures = new ArrayList<PartialCalendar.Implicature>();
		implicatures.add(new PartialCalendar.Implicature(PartialCalendar.makeMask(PartialCalendar.ERA, PartialCalendar.YEAR, PartialCalendar.MONTH, PartialCalendar.DATE),
				PartialCalendar.makeMask(PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH)));
		return implicatures;
	}
	
	@Test
	public void testImplicatures() {
		PartialCalendar partial = new PartialCalendar(firstImplicature(), new GregorianCalendar());
		
		assertHasntFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH);
		partial.markSet(PartialCalendar.ERA);
		assertHasntFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH);
		partial.markSet(PartialCalendar.YEAR);
		assertHasntFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH);
		partial.markSet(PartialCalendar.MONTH);
		assertHasntFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH);
		partial.markSet(PartialCalendar.DATE);
		assertHasFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH);
	}
	
	@Test
	public void testSecondaryImplicatures() {
		List<PartialCalendar.Implicature> implicatures = firstImplicature();
		implicatures.add(new Implicature(PartialCalendar.makeMask(PartialCalendar.ERA, PartialCalendar.YEAR, PartialCalendar.DAY_OF_YEAR), PartialCalendar.makeMask(PartialCalendar.MONTH, PartialCalendar.DATE)));
		PartialCalendar partial = new PartialCalendar(implicatures, new GregorianCalendar());
		assertHasntFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH);
		partial.markSet(PartialCalendar.ERA);
		assertHasntFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH);
		partial.markSet(PartialCalendar.YEAR);
		assertHasntFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH);
		partial.markSet(PartialCalendar.DAY_OF_YEAR);
		assertHasFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH);
	}
	
	@Test
	public void testMarkSet() {
		List<PartialCalendar.Implicature> implicatures = new ArrayList<PartialCalendar.Implicature>(); // empty
		PartialCalendar partial = new PartialCalendar(implicatures, new GregorianCalendar());
		assertHasntFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.MONTH);
		partial.markSet(PartialCalendar.DAY_OF_WEEK);
		assertHasFields(partial, PartialCalendar.DAY_OF_WEEK);
		assertHasntFields(partial, PartialCalendar.MONTH);
		partial.markSet(PartialCalendar.MONTH);
		assertHasFields(partial, PartialCalendar.DAY_OF_WEEK, PartialCalendar.MONTH);
	}
	
	@Test
	public void testSet() {
		List<PartialCalendar.Implicature> implicatures = new ArrayList<PartialCalendar.Implicature>(); // empty
		PartialCalendar partial = new PartialCalendar(implicatures, new GregorianCalendar());
		assertHasntFields(partial, PartialCalendar.DAY_OF_MONTH, PartialCalendar.MONTH);
		partial.set(PartialCalendar.DAY_OF_MONTH, 5);
		assertEquals(partial.get(PartialCalendar.DAY_OF_MONTH), 5);
		assertHasFields(partial, PartialCalendar.DAY_OF_MONTH);
		assertHasntFields(partial, PartialCalendar.MONTH);
		
		partial.set(PartialCalendar.MONTH, PartialCalendar.JUNE);
		assertEquals(partial.get(PartialCalendar.DAY_OF_MONTH), 5);
		assertEquals(partial.get(PartialCalendar.MONTH), PartialCalendar.JUNE);
		assertHasFields(partial, PartialCalendar.DAY_OF_MONTH, PartialCalendar.MONTH);
	}
	
	@Test
	public void testUpdate() {
		List<PartialCalendar.Implicature> implicatures = new ArrayList<PartialCalendar.Implicature>(); // empty
		PartialCalendar partial1 = new PartialCalendar(implicatures, new GregorianCalendar(2005, PartialCalendar.JUNE, 5));
		partial1.markSet(PartialCalendar.ERA);
		partial1.markSet(PartialCalendar.YEAR);
		PartialCalendar partial2 = new PartialCalendar(implicatures, new GregorianCalendar(2004, PartialCalendar.JULY, 6));
		partial2.markSet(PartialCalendar.MONTH);
		assertHasntFields(partial1, PartialCalendar.MONTH, PartialCalendar.DATE);
		partial1.update(partial2);
		assertHasntFields(partial1, PartialCalendar.DATE);
		assertHasFields(partial1, PartialCalendar.MONTH);
		assertHasntFields(partial2, PartialCalendar.YEAR);
	}
	
	@Test
	public void testUpdateImplicatures() {
		List<PartialCalendar.Implicature> implicatures = firstImplicature();
		PartialCalendar partial1 = new PartialCalendar(implicatures, new GregorianCalendar(2005, PartialCalendar.JUNE, 5));
		partial1.markSet(PartialCalendar.ERA);
		partial1.markSet(PartialCalendar.YEAR);
		PartialCalendar partial2 = new PartialCalendar(implicatures, new GregorianCalendar(2004, PartialCalendar.JULY, 6));
		partial2.markSet(PartialCalendar.MONTH);
		PartialCalendar partial3 = new PartialCalendar(implicatures, new GregorianCalendar(2003, PartialCalendar.AUGUST, 7));
		partial3.markSet(PartialCalendar.DATE);
		assertHasntFields(partial1, PartialCalendar.MONTH, PartialCalendar.DATE, PartialCalendar.DAY_OF_WEEK);
		
		partial1.update(partial2);
		assertHasntFields(partial1, PartialCalendar.DATE, PartialCalendar.DAY_OF_WEEK);
		assertHasFields(partial1, PartialCalendar.MONTH);
		assertHasntFields(partial2, PartialCalendar.YEAR);
		assertHasntFields(partial3, PartialCalendar.MONTH, PartialCalendar.DAY_OF_WEEK);
		
		partial1.update(partial3);
		assertHasFields(partial1, PartialCalendar.DATE, PartialCalendar.DAY_OF_WEEK);
	}
	
	@Test
	public void testClone() {
		List<PartialCalendar.Implicature> implicatures = new ArrayList<PartialCalendar.Implicature>();
		PartialCalendar partial1 = new PartialCalendar(implicatures, new GregorianCalendar(2005, PartialCalendar.JUNE, 5));
		partial1.markSet(PartialCalendar.ERA);
		partial1.markSet(PartialCalendar.YEAR);
		
		PartialCalendar partial2 = (PartialCalendar) partial1.clone();
		assertClone(partial1, partial2, PartialCalendar.ERA, PartialCalendar.YEAR, PartialCalendar.DAY_OF_YEAR, PartialCalendar.DAY_OF_WEEK, PartialCalendar.DAY_OF_WEEK_IN_MONTH, PartialCalendar.DAY_OF_YEAR, PartialCalendar.WEEK_OF_YEAR, PartialCalendar.WEEK_OF_MONTH, PartialCalendar.AM_PM);
		
		// Ensure underlying calendars are now distinct
		partial2.set(PartialCalendar.YEAR, 2006);
		assertEquals(partial1.get(PartialCalendar.YEAR), 2005);
		assertEquals(partial2.get(PartialCalendar.YEAR), 2006);
		
		// TODO: ensure identical implicatures?
	}
	
	@Test
	public void testCompareFieldsTo() {
		List<PartialCalendar.Implicature> implicatures = new ArrayList<PartialCalendar.Implicature>();
		PartialCalendar partial1 = new PartialCalendar(implicatures, new GregorianCalendar(2005, PartialCalendar.JUNE, 5),
				PartialCalendar.YEAR, PartialCalendar.MONTH, PartialCalendar.DATE);
		PartialCalendar partial2 = new PartialCalendar(implicatures, new GregorianCalendar(2005, PartialCalendar.JULY, 4, 11, 30),
				PartialCalendar.YEAR, PartialCalendar.MONTH, PartialCalendar.DATE, PartialCalendar.HOUR_OF_DAY, PartialCalendar.MINUTE);
		
		// compare single fields
		assertEquals(0, partial1.compareFieldsTo(partial2, PartialCalendar.YEAR));
		assertEquals(0, partial2.compareFieldsTo(partial1, PartialCalendar.YEAR));
		assertEquals(-1, partial1.compareFieldsTo(partial2, PartialCalendar.MONTH));
		assertEquals(1, partial2.compareFieldsTo(partial1, PartialCalendar.MONTH));
		assertEquals(1, partial1.compareFieldsTo(partial2, PartialCalendar.DATE));
		assertEquals(-1, partial2.compareFieldsTo(partial1, PartialCalendar.DATE));
		
		// compare fields hierarchically
		assertEquals(-1, partial1.compareFieldsTo(partial2, PartialCalendar.YEAR, PartialCalendar.MONTH));
		assertEquals(1, partial2.compareFieldsTo(partial1, PartialCalendar.YEAR, PartialCalendar.MONTH));
		assertEquals(-1, partial1.compareFieldsTo(partial2, PartialCalendar.YEAR, PartialCalendar.MONTH, PartialCalendar.DATE));
		assertEquals(1, partial2.compareFieldsTo(partial1, PartialCalendar.YEAR, PartialCalendar.MONTH, PartialCalendar.DATE));
		assertEquals(1, partial1.compareFieldsTo(partial2, PartialCalendar.YEAR, PartialCalendar.DATE, PartialCalendar.MONTH));
		assertEquals(-1, partial2.compareFieldsTo(partial1, PartialCalendar.YEAR, PartialCalendar.DATE, PartialCalendar.MONTH));
		
		// compare on fields not held by both: should have no impact
		assertEquals(0, partial1.compareFieldsTo(partial2, PartialCalendar.HOUR_OF_DAY));
		assertEquals(0, partial2.compareFieldsTo(partial1, PartialCalendar.HOUR_OF_DAY));
		assertEquals(1, partial1.compareFieldsTo(partial2, PartialCalendar.HOUR_OF_DAY, PartialCalendar.DATE));
		assertEquals(-1, partial2.compareFieldsTo(partial1, PartialCalendar.HOUR_OF_DAY, PartialCalendar.DATE));
		assertEquals(1, partial1.compareFieldsTo(partial2, PartialCalendar.DATE, PartialCalendar.HOUR_OF_DAY));
		assertEquals(-1, partial2.compareFieldsTo(partial1, PartialCalendar.DATE, PartialCalendar.HOUR_OF_DAY));
	}
	
	private void assertClone(PartialCalendar pc1, PartialCalendar pc2, int...fields) {
		for (int field : fields) {
			assertEquals(pc1.has(field), pc2.has(field));
			if (pc1.has(field)) {
				assertEquals(pc1.get(field), pc2.get(field));
			}
		}
	}
	
	private void assertHasFields(PartialCalendar partial, int... fields) {
		for (int field : fields) {
			assertTrue(partial.has(field));
		}
		assertTrue(partial.hasAll(fields)); // equivalent
	}
	
	private void assertHasntFields(PartialCalendar partial, int... fields) {
		for (int field : fields) {
			assertFalse(partial.has(field));
		}
		assertFalse(partial.hasAny(fields)); // equivalent
	}

}
