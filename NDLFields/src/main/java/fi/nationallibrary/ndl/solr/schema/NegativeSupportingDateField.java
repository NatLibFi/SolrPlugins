/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fi.nationallibrary.ndl.solr.schema;

import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.FieldComparatorSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.DateUtil;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.DateField;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.QParser;
import org.apache.solr.util.DateMathParser;

import java.io.IOException;
import java.text.*;
import java.util.*;

/**
 * FieldType that supports dates with negative years.
 */
public class NegativeSupportingDateField extends DateField {

	/**
	 * Needs to be overridden because this method calls parseDate
	 * which is a static method in DateField
	 */
	@Override
	public Date parseMath(Date now, String val) {
		// Ugly hax
		if (val.length() == 8)
			val = "AD" + val.substring(0, 4) + "-" + val.substring(4, 6) + "-"
					+ val.substring(6, 8);
		if (val.length() == 12)
			val += "T00:00:00Z";

		String math = null;
		final DateMathParser p = new DateMathParser(MATH_TZ, MATH_LOCALE);

		if (null != now)
			p.setNow(now);

		if (val.startsWith(NOW)) {
			math = val.substring(NOW.length());
		} else {
			try {
				p.setNow(eraAwareParseDate(val));
			} catch (ParseException e) {
				throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
						"Invalid Date in Date Math String:'" + val + '\'', e);
			}
		}

		if (null == math || math.equals("")) {
			return p.getNow();
		}

		try {
			return p.parseMath(math);
		} catch (ParseException e) {
			throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
					"Invalid Date Math String:'" + val + '\'', e);
		}
	}

	public Date toObject(String indexedForm) throws java.text.ParseException {
		return eraAwareParseDate(indexedToReadable(indexedForm));
	}

	@Override
	public Date toObject(Fieldable f) {
		try {
			return eraAwareParseDate(toExternal(f));
		} catch (ParseException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	@Override 
	public SortField getSortField(SchemaField field, boolean reverse) { 
		 return new SortField(field.getName(), comparatorSource, reverse); 
	}

	private static FieldComparatorSource comparatorSource = new FieldComparatorSource() {
		@Override
		public FieldComparator<String> newComparator(final String fieldname,
				final int numHits, int sortPos, boolean reversed)
				throws IOException {
			return new EraAwareFieldComparator(numHits, fieldname);
		}

		class EraAwareFieldComparator extends FieldComparator<String> {
			private String[] values;
			private String[] currentReaderValues;
			private final String field;
			private String bottom;

			EraAwareFieldComparator(int numHits, String field) {
				values = new String[numHits];
				this.field = field;
			}

			@Override
			public int compare(int slot1, int slot2) {
				final String val1 = values[slot1];
				final String val2 = values[slot2];
				
				return collator.compare(val1, val2);
			}

			@Override
			public int compareBottom(int doc) {
				final String val2 = currentReaderValues[doc];
				
				return collator.compare(bottom, val2);
			}

			@Override
			public void copy(int slot, int doc) {
				values[slot] = currentReaderValues[doc];
			}

			@Override
			public void setNextReader(IndexReader reader, int docBase)
					throws IOException {
				currentReaderValues = FieldCache.DEFAULT.getStrings(reader,
						field);
			}

			@Override
			public void setBottom(final int bottom) {
				this.bottom = values[bottom];
			}

			@Override
			public String value(int slot) {
				return values[slot];
			}

			@Override
			public int compareValues(String val1, String val2) {
				return collator.compare(val1, val2);
			}
		}
	};

	/**
	 * Thread safe method that can be used by subclasses to format a Date using
	 * the Internal representation.
	 */
	@Override
	protected String formatDate(Date d) {
		return fmtThreadLocal.get().format(d);
	}

	/**
	 * Return the standard human readable form of the date
	 */
	@Override
	public String toExternal(Date d) {
		return fmtThreadLocal.get().format(d) + Z;
	}

	/**
	 * Thread safe method that can be used by subclasses to parse a Date that is
	 * already in the internal representation
	 */
	protected Date eraAwareParseDate(String s) throws ParseException {
		return fmtThreadLocal.get().parse(s);
	}

	/**
	 * Parse a date string in the standard format, or any supported by
	 * DateUtil.parseDate
	 */
	@Override
	public Date parseDateLenient(String s, SolrQueryRequest req)
			throws ParseException {
		// request could define timezone in the future
		try {
			return fmtThreadLocal.get().parse(s);
		} catch (Exception e) {
			return DateUtil.parseDate(s);
		}
	}

	/** EraAwareDateField specific range query */

	@Override
	public Query getRangeQuery(QParser parser, SchemaField sf, Date part1,
			Date part2, boolean minInclusive, boolean maxInclusive) {
		return new TermRangeQuery(sf.getName(), part1 == null ? null
				: toInternal(part1), part2 == null ? null : toInternal(part2),
				minInclusive, maxInclusive, collator);
	}

	@Override
	public Query getRangeQuery(QParser parser, SchemaField field, String part1,
			String part2, boolean minInclusive, boolean maxInclusive) {
		// Use custom collator
		return new TermRangeQuery(field.getName(), part1 == null ? null
				: toInternal(part1), part2 == null ? null : toInternal(part2),
				minInclusive, maxInclusive, collator);
	}

	protected static Collator collator = new Collator() {
		@Override
		public int compare(String source, String target) {
			if (source == null || source.length() == 0) {
				if (target == null || target.length() == 0) {
					return 0;
				}
				return -1;
			} else if (target == null || target.length() == 0) {
				return 1;
			}
			
			boolean inverse = false;
			
			int sourceIndex = source.indexOf('-');
			int targetIndex = target.indexOf('-');
			
			if(sourceIndex == 0) {
				if(targetIndex == 0) {
					sourceIndex = source.indexOf('-',1);
					targetIndex = target.indexOf('-',1);
					inverse = true;
				}
				else return -1;
			}
			else if(targetIndex == 0)
				return 1;
			
			// Both are either positive or negative with varying amounts of digits
			// so we need to parse them as numbers
			int sourceYear = Integer.parseInt(source.substring(0,sourceIndex));
			int targetYear = Integer.parseInt(target.substring(0,targetIndex));
			
			if(sourceYear < targetYear)
				return -1;
			if(sourceYear > targetYear)
				return 1;
			
			// Years match, so a lexicographical compare should work 
			if(inverse)
				return target.compareTo(source);
			else 
				return source.compareTo(target);
		}

		@Override
		public CollationKey getCollationKey(String source) {
			throw new RuntimeException("Collation keys not supported");
		}

		@Override
		public int hashCode() {
			return 0;
		}
	};

	/**
	 * Thread safe DateFormat that can <b>format</b> in the canonical ISO8601
	 * date format, not including the trailing "Z" (since it is left off in the
	 * internal indexed values). Supports negative years.
	 */
	private final static ThreadLocalDateFormat fmtThreadLocal = new ThreadLocalDateFormat(
			new NegativeSupportingDateFormat());
	
	private static class NegativeSupportingDateFormat extends SimpleDateFormat {
	  
	  protected static final Locale _CANONICAL_LOCALE = Locale.US;
	  public static TimeZone _UTC = TimeZone.getTimeZone("UTC");
	  protected static final TimeZone _CANONICAL_TZ = _UTC;
	  
		protected NumberFormat millisParser = NumberFormat
				.getIntegerInstance(_CANONICAL_LOCALE);

		protected NumberFormat millisFormat = new DecimalFormat(".###",
				new DecimalFormatSymbols(_CANONICAL_LOCALE));

		public NegativeSupportingDateFormat() {
			super("Gyyyy-MM-dd'T'HH:mm:ss", _CANONICAL_LOCALE);
			this.setTimeZone(_CANONICAL_TZ);
		}

		@Override
		public Date parse(String i, ParsePosition p) {

			/*
			 * Convert canonical dateTime years to era format Eg.
			 * -333-01-01T00:00:00 -> BC333-01-01T00:00:00 1999-06-01T00:00:00
			 * -> AD1999-06-01T00:00:00
			 */
			char first = Character.toLowerCase(i.charAt(0));
			if (first != 'b' && first != 'a') {
				if (first == '-')
					i = "BC" + i.substring(1);
				else
					i = "AD" + i;
			}

			/* delegate to SimpleDateFormat for easy stuff */
			Date d = super.parse(i, p);
			int milliIndex = p.getIndex();
			/* worry aboutthe milliseconds ourselves */
			if (null != d && -1 == p.getErrorIndex()
					&& milliIndex + 1 < i.length()
					&& '.' == i.charAt(milliIndex)) {
				p.setIndex(++milliIndex); // NOTE: ++ to chomp '.'
				Number millis = millisParser.parse(i, p);
				if (-1 == p.getErrorIndex()) {
					int endIndex = p.getIndex();
					d = new Date(d.getTime()
							+ (long) (millis.doubleValue() * Math.pow(10,
									(3 - endIndex + milliIndex))));
				}
			}
			return d;
		}

		@Override
		public StringBuffer format(Date d, StringBuffer toAppendTo,
				FieldPosition pos) {
			/*
			 * delegate to SimpleDateFormat for easy stuff 
			 * Replace BC and AD with '-' and nothing, respectively
			 */
			StringBuffer outputWithEra = new StringBuffer();
			super.format(d, outputWithEra, new FieldPosition(0));
			if (outputWithEra.length() > 0) {
				if (outputWithEra.charAt(0) == 'A')
					toAppendTo.append(outputWithEra.substring(2));
				else
					toAppendTo.append("-").append(outputWithEra.substring(2));
			}

			/* worry aboutthe milliseconds ourselves */
			long millis = d.getTime() % 1000l;
			if (0l == millis) {
				return toAppendTo;
			}
			int posBegin = toAppendTo.length();
			toAppendTo.append(millisFormat.format(millis / 1000d));
			if (DateFormat.MILLISECOND_FIELD == pos.getField()) {
				pos.setBeginIndex(posBegin);
				pos.setEndIndex(toAppendTo.length());
			}
			return toAppendTo;
		}

		@Override
		public Object clone() {
		  NegativeSupportingDateFormat c = (NegativeSupportingDateFormat) super
					.clone();
			c.millisParser = NumberFormat.getIntegerInstance(CANONICAL_LOCALE);
			c.millisFormat = new DecimalFormat(".###",
					new DecimalFormatSymbols(CANONICAL_LOCALE));
			return c;
		}
	}

	private static class ThreadLocalDateFormat extends ThreadLocal<DateFormat> {
		DateFormat proto;

		public ThreadLocalDateFormat(DateFormat d) {
			super(); 
			proto = d; 
		}

		@Override
		protected DateFormat initialValue() {
			return (DateFormat) proto.clone();
		}
	}
}
