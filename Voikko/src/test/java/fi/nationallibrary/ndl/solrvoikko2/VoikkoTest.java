/* 
 * Copyright (C) 2014-2016 The National Library of Finland
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

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.AttributeFactory;
import org.puimula.libvoikko.Voikko;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import fi.nationallibrary.ndl.solrvoikko2.VoikkoFilter.CompoundToken;

/**
 * Unit tests for Voikko
 * 
 * @author ere.maijala@helsinki.fi
 *
 */
 public class VoikkoTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public VoikkoTest(String testName)
    {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite(VoikkoTest.class);
    }

    /**
     * Tests for Voikko
     */
    public void testVoikko() throws IOException
    {
      LinkedList<Entry<String, String>> tests = new LinkedList<Entry<String, String>>();
      
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "kyminsanomat",
          "kyminsanoma [1:0:12],kymi [0:0:12],sanoma [1:0:12]"
      ));
      
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "taidemaalaus",
          "taidemaalaus [1:0:12],taide [0:0:12],maalaus [1:0:12]"
      ));
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "lopputarkastuspöytäkirja", 
          "lopputarkastuspöytäkirja [1:0:24],loppu [0:0:24],tarkastus [1:0:24],pöytä [1:0:24],kirja [1:0:24]"
      ));
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "totalgibberish",
          "totalgibberish [1:0:14]"
      ));
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "moottorisaha",
          "moottorisaha [1:0:12],moottori [0:0:12],saha [1:0:12]"
      ));
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "hyvinvointiasiantuntijajärjestelmässä",
          "hyvinvointiasiantuntijajärjestelmä [1:0:37],hyvinvointi [0:0:37],asia [1:0:37],tuntija [1:0:37],järjestelmä [1:0:37]"
      ));
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "kahdeksankulmainen",
          "kahdeksankulmainen [1:0:18],kahdeksan [0:0:18],kahdeksa [0:0:18],kulmainen [1:0:18]"
      ));
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "perinteinen puutarhakaluste",
          "perinteinen [1:0:11],puutarhakaluste [1:12:27],puu [0:12:27],tarha [1:12:27],kaluste [1:12:27]"
      ));
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "nuorisotyö",
          "nuorisotyö [1:0:10],nuoriso [0:0:10],työ [1:0:10]"
      ));
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "nuorisotyöttömyys",
          "nuorisotyöttömyys [1:0:17],nuoriso [0:0:17],työttömyys [1:0:17]"
      ));
      tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
          "",
          ""
      ));
      
      for (int i = 0; i < tests.size(); i++) {
        Entry<String, String> entry = tests.get(i);
        assertEquals("Testing '" + entry.getKey() + "'", entry.getValue(), getVoikkoWords(entry.getKey()));
      }
    }
    
    /**
     * Execute Voikko analysis and return results in a string
     * 
     * @param term           String to analyze
     * 
     * @return Comma-separated list of results 
     * @throws IOException
     */
    final protected String getVoikkoWords(String term) throws IOException
    {
      Cache<String, List<CompoundToken>> cache = Caffeine.newBuilder()
          .maximumSize(100)
          .build();

      Tokenizer tokenizer = new StandardTokenizer(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY);
      tokenizer.setReader(new StringReader(term));
      tokenizer.reset();

      Voikko voikko = new Voikko("fi-x-morphoid");
      VoikkoFilter voikkoFilter = new VoikkoFilter(tokenizer, voikko, true,
          VoikkoFilter.DEFAULT_MIN_WORD_SIZE, VoikkoFilter.DEFAULT_MIN_SUBWORD_SIZE,
          VoikkoFilter.DEFAULT_MAX_SUBWORD_SIZE, true, cache, 0);

      String results = "";

      while (voikkoFilter.incrementToken()) {
        if (!results.isEmpty()) {
          results += ",";
        }
        results += voikkoFilter.termAtt.toString() + " [" + voikkoFilter.posIncAtt.getPositionIncrement() + ":" + voikkoFilter.offsetAtt.startOffset() + ":" + voikkoFilter.offsetAtt.endOffset() + "]";
      }
      voikkoFilter.close();
      
      return results;
    }
}
