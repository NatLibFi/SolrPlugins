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

import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.solr.analysis.BaseTokenFilterFactory;
import org.puimula.libvoikko.Voikko;

/**
 * 
 * @author ere.maijala@helsinki.fi
 *
 */
public class VoikkoFilterFactory extends BaseTokenFilterFactory {

  private boolean expandCompounds = false;
  private int minWordSize;
  private int minSubwordSize;
  private int maxSubwordSize;
  private Voikko voikko;

  @Override
  public void init(Map<String, String> args) {
    super.init(args);
    voikko = new Voikko("fi-x-morphoid");
    minWordSize = getInt("minWordSize", VoikkoFilter.DEFAULT_MIN_WORD_SIZE);
    minSubwordSize = getInt("minSubwordSize", VoikkoFilter.DEFAULT_MIN_SUBWORD_SIZE);
    maxSubwordSize = getInt("maxSubwordSize", VoikkoFilter.DEFAULT_MAX_SUBWORD_SIZE);
    expandCompounds = getBoolean("expandCompounds", false);
  }

  public TokenStream create(TokenStream input) {
    return new VoikkoFilter(input, voikko, expandCompounds, minWordSize, minSubwordSize, maxSubwordSize);
  }
  
  @Override
  protected void finalize() throws Throwable {
	  voikko.terminate();
  }
  
}
