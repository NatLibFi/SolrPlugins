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


package fi.nationallibrary.ndl.solrvoikko2;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.puimula.libvoikko.Voikko;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import fi.nationallibrary.ndl.solrvoikko2.VoikkoFilter.CompoundToken;

/**
 * 
 * @author ere.maijala@helsinki.fi
 *
 */
public class VoikkoFilterFactory extends TokenFilterFactory {

  /**
   * Default cache size
   */
  private static final int DEFAULT_CACHE_SIZE = 1024;
  
  private boolean expandCompounds = false;
  private boolean allAnalysis = false; // Whether to use all analysis possibilities 
  private int minWordSize;
  private int minSubwordSize;
  private int maxSubwordSize;
  private int cacheSize;
  private Voikko voikko;

  private ConcurrentMap<String, List<CompoundToken>> cache;
  
  public VoikkoFilterFactory(Map<String, String> args) {
    super(args);
    voikko = new Voikko("fi-x-morphoid");
    minWordSize = getInt(args, "minWordSize", VoikkoFilter.DEFAULT_MIN_WORD_SIZE);
    minSubwordSize = getInt(args, "minSubwordSize", VoikkoFilter.DEFAULT_MIN_SUBWORD_SIZE);
    maxSubwordSize = getInt(args, "maxSubwordSize", VoikkoFilter.DEFAULT_MAX_SUBWORD_SIZE);
    expandCompounds = getBoolean(args, "expandCompounds", false);
    allAnalysis = getBoolean(args, "allAnalysis", false);
    cacheSize = getInt(args, "cacheSize", DEFAULT_CACHE_SIZE);
    cache = new ConcurrentLinkedHashMap.Builder<String, List<CompoundToken>>()
        .maximumWeightedCapacity(cacheSize)
        .build();
  }

  public TokenStream create(TokenStream input) {
    return new VoikkoFilter(input, voikko, expandCompounds, minWordSize, minSubwordSize, maxSubwordSize, allAnalysis, cache);
  }
  
  @Override
  protected void finalize() throws Throwable {
	  voikko.terminate();
  }
  
}
