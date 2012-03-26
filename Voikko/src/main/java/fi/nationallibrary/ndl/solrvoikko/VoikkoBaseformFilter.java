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
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.puimula.libvoikko.Analysis;
import org.puimula.libvoikko.Voikko;

/**
 * 
 * @author ere.maijala@helsinki.fi
 *
 */
public class VoikkoBaseformFilter extends TokenFilter {

  private static final String BASEFORM_ATTR = "BASEFORM";
  private static final String WORDBASES_ATTR = "WORDBASES";

  private Voikko voikko;
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final PositionIncrementAttribute posAtt = addAttribute(PositionIncrementAttribute.class);
  protected State save = null;
  private final boolean expandCompounds;

  private List<String> pendingWordBases = new ArrayList<String>();

  protected VoikkoBaseformFilter(TokenStream input, Voikko voikko, boolean expandCompounds) {
    super(input);
    this.voikko = voikko;
    this.expandCompounds = expandCompounds;
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (expandCompounds) {
      if (pendingWordBases.size() > 0) {
        restoreState(save);
        termAtt.setEmpty().append(pendingWordBases.get(0));
        posAtt.setPositionIncrement(0);
        pendingWordBases.remove(0);
        return true;
      }
    }
    if (input.incrementToken()) {
      String word = termAtt.toString();
      List<Analysis> analysisList = voikko.analyze(word);
      if (!analysisList.isEmpty()) {
        // TODO: this will use only the first analysis, should we use all?
        Analysis analysis = analysisList.get(0);

        if (expandCompounds && analysis.containsKey(WORDBASES_ATTR)) {
          String wordbases = analysis.get(WORDBASES_ATTR);
          int start = wordbases.indexOf('('), end = 0;
          // Don't proceed unless we have more than one word
          if (wordbases.indexOf('(', start+1) != -1) {
            save = captureState();
            while ((start = wordbases.indexOf('(', end)) != -1 &&
                (end = wordbases.indexOf(')', start)) != -1) {
              pendingWordBases.add(wordbases.substring(start+1, end));
            }
          }
        }
        if (analysis.containsKey(BASEFORM_ATTR)) {
          termAtt.setEmpty();
          termAtt.append(analysis.get(BASEFORM_ATTR));
        }
        return true;
      }
      return true;
    } else {
      return false;
    }
  }

}
