/**
 *
 * Copyright 2012 The National Library of Finland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fi.nationallibrary.ndl.solrvoikko;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.puimula.libvoikko.Analysis;
import org.puimula.libvoikko.Voikko;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author ere.maijala@helsinki.fi
 *
 */
public class VoikkoFilter extends TokenFilter {
  /**
   * The default for minimal word length that gets processed
   */
  public static final int DEFAULT_MIN_WORD_SIZE = 3;

  /**
   * The default for minimal length of subwords that get propagated to the output of this filter
   */
  public static final int DEFAULT_MIN_SUBWORD_SIZE = 2;

  /**
   * The default for maximal length of subwords that get propagated to the output of this filter
   */
  public static final int DEFAULT_MAX_SUBWORD_SIZE = 25;

  private static final String BASEFORM_ATTR = "BASEFORM";
  private static final String WORDBASES_ATTR = "WORDBASES";

  protected Voikko voikko;
  protected final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  protected final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
  private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
  private State current = null;
  private final boolean expandCompounds;
  private int minWordSize;
  private int minSubwordSize;
  private int maxSubwordSize;

  private final LinkedList<CompoundToken> tokens;

  // Cache for most recent analysis  
  final int MAX_CACHED_ENTRIES = 1024;
  private Map<String, List<Analysis>> cache = new LinkedHashMap<String, List<Analysis>>(MAX_CACHED_ENTRIES+1, .75F, true) {
    public boolean removeEldestEntry(Map.Entry eldest) {
        return size() > MAX_CACHED_ENTRIES;
    }
  };
  
  protected VoikkoFilter(TokenStream input, Voikko voikko, boolean expandCompounds, int minWordSize, int minSubwordSize, int maxSubwordSize) {
    super(input);
    this.tokens = new LinkedList<CompoundToken>();
    this.voikko = voikko;
    this.expandCompounds = expandCompounds;
    this.minWordSize = minWordSize;
    this.minSubwordSize = minSubwordSize;
    this.maxSubwordSize = maxSubwordSize;
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (!tokens.isEmpty()) {
      assert current != null;
      CompoundToken token = tokens.removeFirst();
      restoreState(current); // keep all other attributes untouched
      termAtt.setEmpty().append(token.txt);
      offsetAtt.setOffset(token.startOffset, token.endOffset);
      posIncAtt.setPositionIncrement(0);
      return true;
    }
    current = null; // not really needed, but for safety
    if (input.incrementToken()) {
      String word = termAtt.toString();
      if (word.length() < minWordSize || !word.matches("[a-zA-ZåäöÅÄÖ]+")) {
        return true;
      }
      List<Analysis> analysisList = null;
      if (cache.containsKey(word.toLowerCase())) {
        analysisList = cache.get(word.toLowerCase());
      } else {
        analysisList = voikko.analyze(word);
        cache.put(word.toLowerCase(), analysisList);
      }
      if (!analysisList.isEmpty()) {
        // TODO: this will use only the first analysis, should we use all?
        Analysis analysis = analysisList.get(0);

        if (analysis.containsKey(BASEFORM_ATTR)) {
          termAtt.setEmpty();
          String baseform = analysis.get(BASEFORM_ATTR);
          termAtt.append(baseform);
        }
        
        if (expandCompounds && analysis.containsKey(WORDBASES_ATTR)) {
          String wordbases = analysis.get(WORDBASES_ATTR);
          int start = wordbases.indexOf('('), end = 0;
          // Don't proceed unless we have more than one word
          if (wordbases.indexOf('+', start+1) != -1) {
            current = captureState();
            int offset = 0;
            for (String wordbase: wordbases.split("\\+")) {
              String base = wordbase;
              start = wordbase.indexOf('(');
              if (start != -1) {
                end = wordbase.indexOf(')');
                if (end != -1) {
                  base = wordbase.substring(start+1, end);
                }
              }
              int len = base.length();
              if (len >= minSubwordSize && len <= maxSubwordSize && !wordbase.equals(termAtt.toString())) {
                tokens.add(new CompoundToken(base, offset, len));
              }
              offset += len;
            }
          }
        }
        return true;
      }
      return true;
    } 
    return false;
  }

  /**
   * Helper class to hold decompounded token information
   */
  protected class CompoundToken {
    public final CharSequence txt;
    public final int startOffset, endOffset;

    /** Construct the compound token based on a slice of the current {@link CompoundWordTokenFilterBase#termAtt}. */
    public CompoundToken(CharSequence txt, int offset, int length) {
      this.txt = txt;
      this.startOffset = offset;
      this.endOffset = offset + length;
    }

  }  

}
