/*
 * Copyright (C) 2012-2016 University of Helsinki (The National Library of Finland)
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

/**
 * Test application
 */

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
            System.out.print("base form: ");
            print(component);
          }
          if (analysis.containsKey(WORDBASES)) {
            System.out.print("word bases: ");
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
