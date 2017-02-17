/*
 * Copyright (C) 2012-2016 The National Library of Finland
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.AttributeFactory;
import org.puimula.libvoikko.Analysis;
import org.puimula.libvoikko.Voikko;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import fi.nationallibrary.ndl.solrvoikko2.CompoundToken;

/**
 *
 * @author hatapitk@iki.fi
 * @author ere.maijala@helsinki.fi
 *
 */
public class TestApp {


  private static final String BASEFORM = "BASEFORM";
  private static final String WORDBASES = "WORDBASES";

  private static final class WordComponent {
    public String component;
    public int startInOriginal;
    public int lengthInOriginal;
  }

  public static void main(String[] args) throws IOException {
    BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
    Voikko voikko = null;
    try {
      Cache<String, List<CompoundToken>> cache = Caffeine.newBuilder()
          .maximumSize(100)
          .build();

      voikko = new Voikko("fi-x-morphoid");

      StringReader reader = new StringReader("");
      Tokenizer tokenizer = new StandardTokenizer(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY);
      tokenizer.setReader(reader);
      tokenizer.reset();

      voikko = new Voikko("fi-x-morphoid");
      VoikkoFilter voikkoFilter = new VoikkoFilter(tokenizer, voikko, true,
          VoikkoFilter.DEFAULT_MIN_WORD_SIZE, VoikkoFilter.DEFAULT_MIN_SUBWORD_SIZE,
          VoikkoFilter.DEFAULT_MAX_SUBWORD_SIZE, true,
          cache, 0);

      String text;
      System.out.println();
      System.out.println("Enter word or phrase");
      while ((text = stdin.readLine()) != null) {
        List<Analysis> analysisList = voikko.analyze(text);
        if (analysisList.isEmpty()) {
          System.out.println("No analysis available");
        }
        for (Analysis analysis : analysisList) {
          System.out.println("Analysis:");
          if (analysis.containsKey(BASEFORM)) {
            WordComponent component = new WordComponent();
            component.component = analysis.get(BASEFORM);
            component.startInOriginal = 0;
            component.lengthInOriginal = text.length();
            print(component);
          }
          if (analysis.containsKey(WORDBASES)) {
            System.out.println(analysis.get(WORDBASES));
          }
        }

        tokenizer.close();
        reader = new StringReader(text);
        tokenizer.setReader(reader);
        tokenizer.reset();

        System.out.println("\nVoikkoFilter results:");
        while (voikkoFilter.incrementToken()) {
          System.out.println(voikkoFilter.termAtt.toString() + " [" + voikkoFilter.posIncAtt.getPositionIncrement() + ":" + voikkoFilter.offsetAtt.startOffset() + ":" + voikkoFilter.offsetAtt.endOffset() + "]");
        }

        System.out.println();
        System.out.println("Enter word or phrase");
      }
      voikkoFilter.close();
    }
    finally {
      voikko.terminate();
    }
  }

  private static void print(WordComponent component) {
    System.out.println(component.component + " [" + component.startInOriginal + ":" + component.lengthInOriginal + "]");
  }
}
