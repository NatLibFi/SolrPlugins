/*
 * Copyright (C) 2014-2019 University of Helsinki (The National Library of Finland)
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
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.AttributeFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.puimula.libvoikko.Voikko;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import fi.nationallibrary.ndl.solrvoikko2.CompoundToken;

/**
 * Unit tests for Voikko
 *
 * @author ere.maijala@helsinki.fi
 *
 */
public class VoikkoTest
{
    /**
     * Tests for Voikko
     */
    @Test
    public void testVoikko() throws IOException
    {
        LinkedList<Entry<String, String>> tests = new LinkedList<Entry<String, String>>();

        tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
            "kyminsanomat",
            "kyminsanoma [1:0:12],kymi [0:0:12],kymin [0:0:12],sanoma [1:0:12],sanoa [0:0:12],sano [0:0:12],ma [1:0:12]"
        ));
        tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
            "taidemaalaus",
            "taidemaalaus [1:0:12],taide [0:0:12],maalata [1:0:12],maalaus [0:0:12]"
        ));
        tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
            "lopputarkastuspöytäkirja",
            "lopputarkastuspöytäkirja [1:0:24],loppu [0:0:24],tarkastaa [1:0:24],tarkastus [0:0:24],pöytä [1:0:24],kirja [1:0:24]"
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
            "hyvinvointiasiantuntijajärjestelmä [1:0:37],hyvinvointi [0:0:37],asia [1:0:37],asian [0:0:37],tuntea [1:0:37],tuntija [0:0:37],järjestelmä [1:0:37]"
        ));
        tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
            "kahdeksankulmainen",
            "kahdeksankulmainen [1:0:18],kahdeksan [0:0:18],kulma [1:0:18],kulmainen [0:0:18]"
        ));
        tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
            "perinteinen puutarhakaluste",
            "perinteinen [1:0:11],perinne [0:0:11],puutarhakaluste [1:12:27],puu [0:12:27],tarha [1:12:27],kaluste [1:12:27]"
        ));
        tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
            "nuorisotyö",
            "nuorisotyö [1:0:10],nuoriso [0:0:10],työ [1:0:10]"
        ));
        tests.add(new java.util.AbstractMap.SimpleEntry<String, String>(
            "nuorisotyöttömyys",
            "nuorisotyöttömyys [1:0:17],nuoriso [0:0:17],työ [1:0:17],työttömyys [0:0:17]"
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
