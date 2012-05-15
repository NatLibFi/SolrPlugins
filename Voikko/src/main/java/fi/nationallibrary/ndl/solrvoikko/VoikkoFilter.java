/* 
 * Copyright 2012 The National Library of Finland
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *********************************************************************************/


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
  private final int minWordSize;
  private final int minSubwordSize;
  private final int maxSubwordSize;
  private final boolean separateTokens;

  private final LinkedList<CompoundToken> tokens;

  // Cache for most recent analysis  
  final int MAX_CACHED_ENTRIES = 1024;
  private Map<String, List<Analysis>> cache = new LinkedHashMap<String, List<Analysis>>(MAX_CACHED_ENTRIES+1, .75F, true) {
    public boolean removeEldestEntry(Map.Entry eldest) {
        return size() > MAX_CACHED_ENTRIES;
    }
  };
  
  protected VoikkoFilter(TokenStream input, Voikko voikko, boolean expandCompounds, int minWordSize, int minSubwordSize, int maxSubwordSize, boolean separateTokens) {
    super(input);
    this.tokens = new LinkedList<CompoundToken>();
    this.voikko = voikko;
    this.expandCompounds = expandCompounds;
    this.minWordSize = minWordSize;
    this.minSubwordSize = minSubwordSize;
    this.maxSubwordSize = maxSubwordSize;
    this.separateTokens = separateTokens;
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (!tokens.isEmpty()) {
      assert current != null;
      CompoundToken token = tokens.removeFirst();
      restoreState(current); // keep all other attributes untouched
      termAtt.setEmpty().append(token.txt);
      if (!separateTokens) {
        for (CompoundToken t: tokens) {
          termAtt.append(' ');
          termAtt.append(t.txt);
        }
        tokens.clear();
      }
      offsetAtt.setOffset(token.startOffset, token.endOffset);
      posIncAtt.setPositionIncrement(0);
      return true;
    }
    current = null; // not really needed, but for safety
    if (input.incrementToken()) {
      String word = termAtt.toString();
      int wordLen = word.length();
      if (wordLen < minWordSize || !word.matches("[a-zA-ZåäöÅÄÖ]+")) {
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
          // Don't proceed unless we have more than one word
          if (wordbases.indexOf('+', 1) != -1) {
            current = captureState();
            int startOffset = offsetAtt.startOffset();
            StringBuilder wordBuilder = new StringBuilder();
            int wordBeginIndex = 0;
            int wordEndIndex = 0;
            StringBuilder wordPartBuilder = new StringBuilder();
            StringBuilder baseBuilder = new StringBuilder();
            boolean inWord = false;
            boolean inBase = false;
            boolean isDerivSuffix = false;
            for (int i = 0; i < wordbases.length(); i++) {
              char c = wordbases.charAt(i);
              if (c == '+') {
                if (!inWord) {
                  inWord = true;
                  continue;
                }
                if (inBase) {
                  isDerivSuffix = true;
                  continue;
                }
                else {
                  // inflectional suffix complete
                  wordBuilder.setLength(0);
                  wordPartBuilder.setLength(0);
                  baseBuilder.setLength(0);
                  wordBeginIndex = wordEndIndex;
                  continue;
                }
              }
              if (c == '(') {
                if (inWord && !inBase) {
                  inBase = true;
                  continue;
                }
                throw new IllegalStateException("Failed to parse Voikko word bases (c == '(', " + 
                  "inWord == " + (inWord ? "true" : "false") + ", " + 
                  "inBase == " + (inBase ? "true" : "false") + ")");
              }
              if (c == ')') {
                if (inBase) {
                  String base;
                  int len;
                  if (isDerivSuffix) {
                    base = wordBuilder.toString() + baseBuilder.toString();
                    len = wordBuilder.length() + wordPartBuilder.length();
                    wordBuilder.append(wordPartBuilder);
                  }
                  else {
                    base = baseBuilder.toString();
                    len = wordPartBuilder.length();
                    wordBeginIndex = wordEndIndex - len;
                    wordBuilder.append(wordPartBuilder);
                  }
                  tokens.add(new CompoundToken(base, startOffset + wordBeginIndex, len));

                  baseBuilder.setLength(0);
                  wordPartBuilder.setLength(0);
                  inWord = false;
                  inBase = false;
                  isDerivSuffix = false;
                  continue;
                }
                throw new IllegalStateException("Failed to parse Voikko word bases (c == ')', inBase == false)");
              }
              
              if (inBase) {
                baseBuilder.append(c);
              }
              else if (inWord) {
                wordPartBuilder.append(c);
                wordEndIndex++;
              }
              else {
                throw new IllegalStateException("Failed to parse Voikko word bases (c == '" + c + 
                  "', inBase == false, inWord == false");
              }
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
