/*
 * Copyright 2012-2017 University of Helsinki (The National Library of Finland)
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

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.puimula.libvoikko.Voikko;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Voikko Filter Factory
 *
 * @author ere.maijala@helsinki.fi
 *
 */
public class VoikkoFilterFactory extends TokenFilterFactory {

  /**
   * Default cache size
   */
  private static final int DEFAULT_CACHE_SIZE = 1024;

  private final boolean expandCompounds;
  private final boolean allAnalysis; // Whether to use all analysis possibilities
  private final int minWordSize;
  private final int minSubwordSize;
  private final int maxSubwordSize;
  private final int cacheSize;
  private final int statsInterval;
  private final Voikko voikko;
  private final Cache<String, List<CompoundToken>> cache;
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public VoikkoFilterFactory(Map<String, String> args) {
    super(args);
    final String language = get(args, "dictionaryLanguage", "fi-x-morphoid");
    final String dictionaryPath = get(args, "dictionaryPath", "");
    log.info("initializing " + language + " with dictionary path " + (dictionaryPath.isEmpty() ? "[default]" : dictionaryPath));
    voikko = new Voikko(language, dictionaryPath.isEmpty() ? null : dictionaryPath);
    minWordSize = getInt(args, "minWordSize", VoikkoFilter.DEFAULT_MIN_WORD_SIZE);
    minSubwordSize = getInt(args, "minSubwordSize", VoikkoFilter.DEFAULT_MIN_SUBWORD_SIZE);
    maxSubwordSize = getInt(args, "maxSubwordSize", VoikkoFilter.DEFAULT_MAX_SUBWORD_SIZE);
    expandCompounds = getBoolean(args, "expandCompounds", false);
    allAnalysis = getBoolean(args, "allAnalysis", false);
    statsInterval = getInt(args, "statsInterval", VoikkoFilter.DEFAULT_STATS_INTERVAL);
    cacheSize = getInt(args, "cacheSize", DEFAULT_CACHE_SIZE);
    if (cacheSize > 0) {
      if (statsInterval > 0) {
        cache = Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .recordStats()
        .build();
      } else {
        cache = Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .build();
      }
    } else {
      cache = null;
    }
    log.info("initialized with cache for " + cacheSize + " entries");
  }

  public TokenStream create(TokenStream input) {
    return new VoikkoFilter(input, voikko, expandCompounds, minWordSize, minSubwordSize, maxSubwordSize, allAnalysis, cache, statsInterval);
  }

  @Override
  protected void finalize() throws Throwable {
	  voikko.terminate();
  }

}
