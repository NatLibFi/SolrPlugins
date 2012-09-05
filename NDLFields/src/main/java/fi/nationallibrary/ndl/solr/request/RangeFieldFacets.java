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

package fi.nationallibrary.ndl.solr.request;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams.FacetRangeOther;
import org.apache.solr.common.params.FacetParams.FacetRangeInclude;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.*;
import org.apache.solr.search.*;
import org.apache.solr.util.DateMathParser;
import org.apache.solr.handler.component.ResponseBuilder;
import fi.nationallibrary.ndl.solr.schema.RangeField;

import java.io.IOException;
import java.util.*;

/**
 * A class that generates simple Facet information for a request.
 *
 * More advanced facet implementations may compose or subclass this class 
 * to leverage any of it's functionality.
 */
public class RangeFieldFacets extends SimpleFacets {
	
  // per-facet values
  SolrParams localParams; // localParams on this particular facet command
  String facetValue;      // the field to or query to facet on (minus local params)
  DocSet base;            // the base docset for this particular facet
  String key;             // what name should the results be stored under
	
  public RangeFieldFacets(SolrQueryRequest req,
                      DocSet docs,
                      SolrParams params) {
    super(req,docs,params,null);
  }

  public RangeFieldFacets(SolrQueryRequest req,
                      DocSet docs,
                      SolrParams params,
                      ResponseBuilder rb) {
	super(req, docs, params, rb);
  }
  

  /**
   * Looks at various Params to determing if any simple Facet Constraint count
   * computations are desired.
   *
   * @see #getFacetQueryCounts
   * @see #getFacetFieldCounts
   * @see #getFacetDateCounts
   * @see #getFacetRangeCounts
   * @see FacetParams#FACET
   * @return a NamedList of Facet Count info or null
   */
  @Override
  public NamedList getFacetCounts() {

    // if someone called this method, benefit of the doubt: assume true
    if (!params.getBool(FacetParams.FACET,true))
      return null;

    facetResponse = new SimpleOrderedMap();
    try {
      facetResponse.add("facet_queries", getFacetQueryCounts());
      facetResponse.add("facet_fields", getFacetFieldCounts());
      facetResponse.add("facet_dates", getFacetDateCounts());
      facetResponse.add("facet_ranges", getFacetRangeCounts());

    } catch (IOException e) {
      SolrException.log(SolrCore.log, "Exception during facet counts", e);
      throw new SolrException(ErrorCode.SERVER_ERROR, e);
    } catch (ParseException e) {
      SolrException.log(SolrCore.log, "Exception during facet counts", e);
      throw new SolrException(ErrorCode.BAD_REQUEST, e);
    }
    return facetResponse;
  }

  public NamedList getFacetRangeCounts() throws IOException, ParseException {
    final NamedList resOuter = new SimpleOrderedMap();
    final String[] fields = params.getParams(FacetParams.FACET_RANGE);

    if (null == fields || 0 == fields.length) return resOuter;

    for (String f : fields) {
      this.getFacetRangeCounts(f, resOuter);
    }

    return resOuter;
  }
  

  void parseParams(String type, String param) throws ParseException, IOException {
    localParams = QueryParsing.getLocalParams(param, req.getParams());
    base = docs;
    facetValue = param;
    key = param;

    if (localParams == null) return;

    // remove local params unless it's a query
    if (type != FacetParams.FACET_QUERY) {
      facetValue = localParams.get(CommonParams.VALUE);
    }

    // reset set the default key now that localParams have been removed
    key = facetValue;

    // allow explicit set of the key
    key = localParams.get(CommonParams.OUTPUT_KEY, key);

    // figure out if we need a new base DocSet
    String excludeStr = localParams.get(CommonParams.EXCLUDE);
    if (excludeStr == null) return;

    Map tagMap = (Map)req.getContext().get("tags");
    if (tagMap != null && rb != null) {
      List<String> excludeTagList = StrUtils.splitSmart(excludeStr,',');

      IdentityHashMap<Query,Boolean> excludeSet = new IdentityHashMap<Query,Boolean>();
      for (String excludeTag : excludeTagList) {
        Object olst = tagMap.get(excludeTag);
        // tagMap has entries of List<String,List<QParser>>, but subject to change in the future
        if (!(olst instanceof Collection)) continue;
        for (Object o : (Collection)olst) {
          if (!(o instanceof QParser)) continue;
          QParser qp = (QParser)o;
          excludeSet.put(qp.getQuery(), Boolean.TRUE);
        }
      }
      if (excludeSet.size() == 0) return;

      List<Query> qlist = new ArrayList<Query>();

      // add the base query
      if (!excludeSet.containsKey(rb.getQuery())) {
        qlist.add(rb.getQuery());
      }

      // add the filters
      if (rb.getFilters() != null) {
        for (Query q : rb.getFilters()) {
          if (!excludeSet.containsKey(q)) {
            qlist.add(q);
          }
        }
      }

      // get the new base docset for this facet
      base = searcher.getDocSet(qlist);
    }

  }
  
  void getFacetRangeCounts(String facetRange, NamedList resOuter)
      throws IOException, ParseException {

    final IndexSchema schema = searcher.getSchema();

    parseParams(FacetParams.FACET_RANGE, facetRange);
    String f = facetValue;

    SchemaField rootSf = schema.getField(f);
    FieldType rootFt = rootSf.getType();
    
    final SchemaField sf;
    final FieldType ft;
    if (rootFt instanceof RangeField) {
      sf = ((RangeField)rootFt).getSubField(rootSf);
      ft = ((RangeField)rootFt).getSubType();
    }
    else {
      sf = rootSf;
      ft = rootFt;
    }

    RangeEndpointCalculator calc = null;

    if (ft instanceof TrieField) {
      final TrieField trie = (TrieField)ft;

      switch (trie.getType()) {
        case FLOAT:
          calc = new FloatRangeEndpointCalculator(sf);
          break;
        case DOUBLE:
          calc = new DoubleRangeEndpointCalculator(sf);
          break;
        case INTEGER:
          calc = new IntegerRangeEndpointCalculator(sf);
          break;
        case LONG:
          calc = new LongRangeEndpointCalculator(sf);
          break;
        default:
          throw new SolrException
              (SolrException.ErrorCode.BAD_REQUEST,
                  "Unable to range facet on tried field of unexpected type:" + f);
      }
    } 
    else if (ft instanceof DateField) {
      calc = new DateRangeEndpointCalculator(sf, new Date());
    } else if (ft instanceof SortableIntField) {
      calc = new IntegerRangeEndpointCalculator(sf);
    } else if (ft instanceof SortableLongField) {
      calc = new LongRangeEndpointCalculator(sf);
    } else if (ft instanceof SortableFloatField) {
      calc = new FloatRangeEndpointCalculator(sf);
    } else if (ft instanceof SortableDoubleField) {
      calc = new DoubleRangeEndpointCalculator(sf);
    } else {
      throw new SolrException
          (SolrException.ErrorCode.BAD_REQUEST,
              "Unable to range facet on field:" + sf);
    }

    resOuter.add(key, getFacetRangeCounts(rootSf, calc));
  }

  private <T extends Comparable<T>> NamedList getFacetRangeCounts
    (final SchemaField sf, 
     final RangeEndpointCalculator<T> calc) throws IOException {
    
    final String f = sf.getName();
    final NamedList res = new SimpleOrderedMap();
    final NamedList counts = new NamedList();
    res.add("counts", counts);

    final T start = calc.getValue(required.getFieldParam(f,FacetParams.FACET_RANGE_START));
    
    // not final, hardend may change this
    T end = calc.getValue(required.getFieldParam(f,FacetParams.FACET_RANGE_END));
    if (end.compareTo(start) < 0) {
      throw new SolrException
        (SolrException.ErrorCode.BAD_REQUEST,
         "range facet 'end' comes before 'start': "+end+" < "+start);
    }
    
    final String gap = required.getFieldParam(f, FacetParams.FACET_RANGE_GAP);
    String[] gaps = parseGaps(gap);
    // explicitly return the gap.  compute this early so we are more 
    // likely to catch parse errors before attempting math
    for (int i = 0; i < gaps.length; i++) {
    	calc.getGap(gaps[i]);
    }
    res.add("gap", gap);
    
    final int minCount = params.getFieldInt(f,FacetParams.FACET_MINCOUNT, 0);
    
    final EnumSet<FacetRangeInclude> include = FacetRangeInclude.parseParam
      (params.getFieldParams(f,FacetParams.FACET_RANGE_INCLUDE));
    
    T low = start;
    int gapIdx = 0;
    int previousCount = 0;
    
    while (low.compareTo(end) < 0) {
      T high = calc.addGap(low, gaps[gapIdx]);
      if (end.compareTo(high) < 0) {
        if (params.getFieldBool(f,FacetParams.FACET_RANGE_HARD_END,false)) {
          high = end;
        } else {
          end = high;
        }
      }
      if (high.compareTo(low) < 0) {
        throw new SolrException
          (SolrException.ErrorCode.BAD_REQUEST,
           "range facet infinite loop (is gap negative? did the math overflow?)");
      }
      
      final boolean includeLower = 
        (include.contains(FacetRangeInclude.LOWER) ||
         (include.contains(FacetRangeInclude.EDGE) && 
          0 == low.compareTo(start)));
      final boolean includeUpper = 
        (include.contains(FacetRangeInclude.UPPER) ||
         (include.contains(FacetRangeInclude.EDGE) && 
          0 == high.compareTo(end)));
      
      final String lowS = calc.formatValue(low);
      final String highS = calc.formatValue(high);

      final int count = rangeCount(sf, lowS, highS,
                                   includeLower,includeUpper);
      if (count >= minCount && count != previousCount) {
        counts.add(lowS, count);
        previousCount = count;
      }
      
      low = high;
      
      gapIdx = Math.min(gaps.length -1, gapIdx + 1);
    }
    
    // explicitly return the start and end so all the counts 
    // (including before/after/between) are meaningful - even if mincount
    // has removed the neighboring ranges
    res.add("start", start);
    res.add("end", end);
    
    final String[] othersP =
      params.getFieldParams(f,FacetParams.FACET_RANGE_OTHER);
    if (null != othersP && 0 < othersP.length ) {
      Set<FacetRangeOther> others = EnumSet.noneOf(FacetRangeOther.class);
      
      for (final String o : othersP) {
        others.add(FacetRangeOther.get(o));
      }
      
      // no matter what other values are listed, we don't do
      // anything if "none" is specified.
      if (! others.contains(FacetRangeOther.NONE) ) {
        
        boolean all = others.contains(FacetRangeOther.ALL);
        final String startS = calc.formatValue(start);
        final String endS = calc.formatValue(end);

        if (all || others.contains(FacetRangeOther.BEFORE)) {
          // include upper bound if "outer" or if first gap doesn't already include it
          res.add(FacetRangeOther.BEFORE.toString(),
                  rangeCount(sf,null,startS,
                             false,
                             (include.contains(FacetRangeInclude.OUTER) ||
                              (! (include.contains(FacetRangeInclude.LOWER) ||
                                  include.contains(FacetRangeInclude.EDGE))))));
          
        }
        if (all || others.contains(FacetRangeOther.AFTER)) {
          // include lower bound if "outer" or if last gap doesn't already include it
          res.add(FacetRangeOther.AFTER.toString(),
                  rangeCount(sf,endS,null,
                             (include.contains(FacetRangeInclude.OUTER) ||
                              (! (include.contains(FacetRangeInclude.UPPER) ||
                                  include.contains(FacetRangeInclude.EDGE)))),  
                             false));
        }
        if (all || others.contains(FacetRangeOther.BETWEEN)) {
         res.add(FacetRangeOther.BETWEEN.toString(),
                 rangeCount(sf,startS,endS,
                            (include.contains(FacetRangeInclude.LOWER) ||
                             include.contains(FacetRangeInclude.EDGE)),
                            (include.contains(FacetRangeInclude.UPPER) ||
                             include.contains(FacetRangeInclude.EDGE))));
         
        }
      }
    }
    return res;
  } 
  
    private String[] parseGaps(String gap) {
	      String gaps[] = null;
	      if (gap.indexOf(",") != -1){
	        //we have variable range gaps
	        gaps = gap.split(",");
	      } else {
	        gaps = new String[]{gap};
	     }
	      return gaps;
    }


  /**
   * Perhaps someday instead of having a giant "instanceof" case 
   * statement to pick an impl, we can add a "RangeFacetable" marker 
   * interface to FieldTypes and they can return instances of these 
   * directly from some method -- but until then, keep this locked down 
   * and private.
   */
  private static abstract class RangeEndpointCalculator<T extends Comparable<T>> {
    protected final SchemaField field;
    public RangeEndpointCalculator(final SchemaField field) {
      this.field = field;
    }

    /**
     * Formats a Range endpoint for use as a range label name in the response.
     * Default Impl just uses toString()
     */
    public String formatValue(final T val) {
      return val.toString();
    }
    /**
     * Parses a String param into an Range endpoint value throwing 
     * a useful exception if not possible
     */
    public final T getValue(final String rawval) {
      try {
        return parseVal(rawval);
      } catch (Exception e) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                                "Can't parse value "+rawval+" for field: " + 
                                field.getName(), e);
      }
    }
    /**
     * Parses a String param into an Range endpoint. 
     * Can throw a low level format exception as needed.
     */
    protected abstract T parseVal(final String rawval) 
      throws java.text.ParseException;

    /** 
     * Parses a String param into a value that represents the gap and 
     * can be included in the response, throwing 
     * a useful exception if not possible.
     *
     * Note: uses Object as the return type instead of T for things like 
     * Date where gap is just a DateMathParser string 
     */
    public final Object getGap(final String gap) {
      try {
        return parseGap(gap);
      } catch (Exception e) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                                "Can't parse gap "+gap+" for field: " + 
                                field.getName(), e);
      }
    }

    /**
     * Parses a String param into a value that represents the gap and 
     * can be included in the response. 
     * Can throw a low level format exception as needed.
     *
     * Default Impl calls parseVal
     */
    protected Object parseGap(final String rawval) 
      throws java.text.ParseException {
      return parseVal(rawval);
    }

    /**
     * Adds the String gap param to a low Range endpoint value to determine 
     * the corrisponding high Range endpoint value, throwing 
     * a useful exception if not possible.
     */
    public final T addGap(T value, String gap) {
      try {
        return parseAndAddGap(value, gap);
      } catch (Exception e) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                                "Can't add gap "+gap+" to value " + value +
                                " for field: " + field.getName(), e);
      }
    }
    /**
     * Adds the String gap param to a low Range endpoint value to determine 
     * the corrisponding high Range endpoint value.
     * Can throw a low level format exception as needed.
     */
    protected abstract T parseAndAddGap(T value, String gap) 
      throws java.text.ParseException;

  }

  private static class FloatRangeEndpointCalculator 
    extends RangeEndpointCalculator<Float> {

    public FloatRangeEndpointCalculator(final SchemaField f) { super(f); }
    @Override
    protected Float parseVal(String rawval) {
      return Float.valueOf(rawval);
    }
    @Override
    public Float parseAndAddGap(Float value, String gap) {
      return new Float(value.floatValue() + Float.valueOf(gap).floatValue());
    }
  }
  private static class DoubleRangeEndpointCalculator 
    extends RangeEndpointCalculator<Double> {

    public DoubleRangeEndpointCalculator(final SchemaField f) { super(f); }
    @Override
    protected Double parseVal(String rawval) {
      return Double.valueOf(rawval);
    }
    @Override
    public Double parseAndAddGap(Double value, String gap) {
      return new Double(value.doubleValue() + Double.valueOf(gap).doubleValue());
    }
  }
  private static class IntegerRangeEndpointCalculator 
    extends RangeEndpointCalculator<Integer> {

    public IntegerRangeEndpointCalculator(final SchemaField f) { super(f); }
    @Override
    protected Integer parseVal(String rawval) {
      return Integer.valueOf(rawval);
    }
    @Override
    public Integer parseAndAddGap(Integer value, String gap) {
      return new Integer(value.intValue() + Integer.valueOf(gap).intValue());
    }
  }
  private static class LongRangeEndpointCalculator 
    extends RangeEndpointCalculator<Long> {

    public LongRangeEndpointCalculator(final SchemaField f) { super(f); }
    @Override
    protected Long parseVal(String rawval) {
      return Long.valueOf(rawval);
    }
    @Override
    public Long parseAndAddGap(Long value, String gap) {
      return new Long(value.longValue() + Long.valueOf(gap).longValue());
    }
  }  
  private static class DateRangeEndpointCalculator 
  extends RangeEndpointCalculator<Date> {
  private final Date now;
  public DateRangeEndpointCalculator(final SchemaField f, 
                                     final Date now) { 
    super(f); 
    this.now = now;
    if (! (field.getType() instanceof DateField) ) {
      throw new IllegalArgumentException
        ("SchemaField must use filed type extending DateField");
    }
  }
  @Override
  public String formatValue(Date val) {
    return ((DateField)field.getType()).toExternal(val);
  }
  @Override
  protected Date parseVal(String rawval) {
    return ((DateField)field.getType()).parseMath(now, rawval);
  }
  @Override
  protected Object parseGap(final String rawval) {
    return rawval;
  }
  @Override
  public Date parseAndAddGap(Date value, String gap) throws java.text.ParseException {
    final DateMathParser dmp = new DateMathParser(DateField.UTC, Locale.US);
    dmp.setNow(value);
    return dmp.parseMath(gap);
  }
}
}

