/*
 * Copyright (C) 2012-2019 University of Helsinki (The National Library of Finland)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fi.nationallibrary.ndl.solrvoikko2;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.puimula.libvoikko.Analysis;
import org.puimula.libvoikko.Voikko;
import com.github.benmanes.caffeine.cache.Cache;

/**
 * Voikko Filter
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

  /**
   * Default token count interval for displaying a statistics message
   */
  public static final int DEFAULT_STATS_INTERVAL = 0;

  private static final String BASEFORM_ATTR = "BASEFORM";
  private static final String WORDBASES_ATTR = "WORDBASES";

  protected Voikko voikko;
  protected final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  protected final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
  protected final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
  private State current = null;
  private int currentPosition = 1;
  private final boolean expandCompounds;
  private final int minWordSize;
  private final int minSubwordSize;
  private final int maxSubwordSize;
  private final boolean allAnalysis;
  private final int statsInterval;

  private final LinkedHashSet<CompoundToken> tokens;

  private Cache<String, List<CompoundToken>> cache;

  // Statistics
  private final static AtomicLong tokenCount = new AtomicLong();
  private final static AtomicLong analysisCount = new AtomicLong();
  private final static AtomicLong analysisTime = new AtomicLong();

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected VoikkoFilter(TokenStream input, Voikko voikko, boolean expandCompounds, int minWordSize, int minSubwordSize, int maxSubwordSize, boolean allAnalysis, Cache<String, List<CompoundToken>> cache, int statsInterval) {
    super(input);
    this.tokens = new LinkedHashSet<CompoundToken>();
    this.voikko = voikko;
    this.expandCompounds = expandCompounds;
    this.minWordSize = minWordSize;
    this.minSubwordSize = minSubwordSize;
    this.maxSubwordSize = maxSubwordSize;
    this.allAnalysis = allAnalysis;
    this.cache = cache;
    this.statsInterval = statsInterval;
  }

  @Override
  public final boolean incrementToken() throws IOException {
    if (!tokens.isEmpty()) {
      assert current != null;
      // keep original attributes apart the ones we want to change
      restoreState(current);

      CompoundToken token = null;
      int prevPosition = currentPosition;
      // Find next token for this or next position
      for (; currentPosition <= prevPosition + 1; currentPosition++) {
        Iterator<CompoundToken> it = tokens.iterator();
        while (it.hasNext()) {
          CompoundToken t = it.next();
          if (t.position == currentPosition) {
            token = t;
            it.remove();
            break;
          }
        }
        if (token != null) {
          break;
        }
      }

      assert token != null;
      if (token == null) {
        tokens.clear();
        return false;
      }
      termAtt.setEmpty().append(token.txt);
      /*
      It's too complicated trying to get the attributes right especially for
      multi-word strings, so let's keep the original offsets even though we
      only return a part of the original term. It's not that bad, it just causes
      the whole term to be highlighted, but that could be considered better than
      trying to highlight just a part of it (and getting the offsets wrong).
      if (token.startOffset != -1) {
        offsetAtt.setOffset(token.startOffset, token.endOffset);
      }*/
      posIncAtt.setPositionIncrement(currentPosition > prevPosition ? 1 : 0);
      return true;
    }

    current = null;
    if (input.incrementToken()) {
      current = captureState();
      String term = termAtt.toString();
      int termLen = term.length();
      if (termLen < minWordSize || !term.matches("[a-zA-ZåäöÅÄÖ]+")) {
        return true;
      }
      List<CompoundToken> cachedTokens = cache != null
        ? cache.getIfPresent(term.toLowerCase())
        : null;
      if (cachedTokens != null) {
        tokens.addAll(cachedTokens);
      } else {
        long startTime = 0;
        if (statsInterval > 0) {
          analysisCount.incrementAndGet();
          startTime = System.nanoTime();
        }
        List<Analysis> analysisList = voikko.analyze(term);
        if (statsInterval > 0) {
          analysisTime.addAndGet((System.nanoTime() - startTime) / 1000000);
        }

        if (analysisList.isEmpty()) {
          if (cache != null) {
            ArrayList<CompoundToken> tokenList = new ArrayList<CompoundToken>();
            cache.put(term.toLowerCase(), tokenList);
          }
          return true;
        }

        // Remove duplicates from analysis list
        if (analysisList.size() > 1) {
          LinkedHashSet<Analysis> analysisMap = new LinkedHashSet<Analysis>(analysisList);
          analysisList = new ArrayList<Analysis>(analysisMap);
        }

        // Process base forms first
        boolean first = true;
        for (Analysis analysis: analysisList) {
          if (!this.allAnalysis && !first) {
            break;
          }
          if (analysis.containsKey(BASEFORM_ATTR)) {
            String baseform = analysis.get(BASEFORM_ATTR);
            // get rid of equals sign in e.g. di=oksidi
            baseform = baseform.replace("=", "");
            tokens.add(new CompoundToken(baseform, 1));
          }
          first = false;
        }

        // Expand compound words
        if (expandCompounds) {
          first = true;
          StringBuilder composedWord = new StringBuilder();
          for (Analysis analysis: analysisList) {
            if (!this.allAnalysis && !first) {
              break;
            }
            first = false;
            if (!analysis.containsKey(WORDBASES_ATTR)) {
              continue;
            }
            String wordbases = analysis.get(WORDBASES_ATTR);

            // Split by plus sign (unless right after an open parenthesis)
            String matches[] = wordbases.split("(?<!\\()\\+");

            int wordPos = 1;
            composedWord.setLength(0);
            int wordPosBase = 1;
            // The string starts with a plus sign, so skip the first (empty) entry
            for (int i = 1; i <= matches.length - 1; i++) {
              String wordAnalysis = matches[i];

              // get rid of equals sign in e.g. di=oksidi
              wordAnalysis = wordAnalysis.replaceAll("=", "");

              final String wordBody;
              final String wordPart;
              int parenPos = wordAnalysis.indexOf('(');
              if (parenPos == -1) {
                wordBody = wordPart = wordAnalysis;
              } else {
                // Word body is before the parenthesis
                wordBody = wordAnalysis.substring(0, parenPos);

                // Base form or derivative is in parenthesis
                wordPart = wordAnalysis.substring(parenPos + 1, wordAnalysis.length() - 1);
              }
              final boolean isDerivative = wordPart.startsWith("+");
              if (!isDerivative) {
                // Add the non-derivative word separately
                if (wordPart.length() >= minSubwordSize) {
                  if (wordPart.length() > maxSubwordSize) {
                    tokens.add(new CompoundToken(wordPart.substring(0, maxSubwordSize), wordPosBase));
                  } else {
                    tokens.add(new CompoundToken(wordPart, wordPosBase));
                  }
                  ++wordPosBase;
                }
                // Add previously composed word
                if (composedWord.length() >= minSubwordSize) {
                  if (composedWord.length() > maxSubwordSize) {
                    composedWord.setLength(maxSubwordSize);
                  }
                  tokens.add(new CompoundToken(composedWord.toString(), wordPos));
                  ++wordPos;
                }
                composedWord.setLength(0);
              }
              composedWord.append(wordBody);
            }
            if (composedWord.length() >= minSubwordSize) {
              if (composedWord.length() > maxSubwordSize) {
                composedWord.setLength(maxSubwordSize);
              }
              tokens.add(new CompoundToken(composedWord.toString(), wordPos));
            }
          }
        }
        ArrayList<CompoundToken> tokenList = new ArrayList<CompoundToken>(tokens);
        if (cache != null) {
          cache.put(term.toLowerCase(), tokenList);
        }
      }

      currentPosition = 1;

      Iterator<CompoundToken> it = tokens.iterator();
      if (it.hasNext()) {
        final CompoundToken t = it.next();
        it.remove();
        termAtt.setEmpty().append(t.txt);
      }

      if (statsInterval > 0 && tokenCount.incrementAndGet() % statsInterval == 0) {
        logStatistics();
      }

      return true;
    }
    return false;
  }

  /**
   * Helper function that writes periodic stats to Solr log
   */
  protected void logStatistics() {
    final String msg = "Stats"
      + ": tokenCount=" + tokenCount.get()
      + ", analysisCount=" + analysisCount.get()
      + ", analysisTime=" + analysisTime.get()
      + ", avgTime=" + (analysisCount.get() > 0
        ? (float)analysisTime.get() / analysisCount.get() : 0) + "ms"
      + ", cacheSize=" + (cache != null ? cache.estimatedSize() : '0')
      + ", cacheHits=" + (cache != null ? cache.stats().hitCount() : '-')
      + ", hitRatio=" + (cache != null ? cache.stats().hitRate() : '-')
      + ", evictionCount=" + (cache != null ? cache.stats().evictionCount() : '-');

    log.info(msg);
  }
}
