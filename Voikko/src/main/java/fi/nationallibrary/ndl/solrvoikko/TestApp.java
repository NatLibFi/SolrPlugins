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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.puimula.libvoikko.Analysis;
import org.puimula.libvoikko.Voikko;


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
      voikko = new Voikko("fi-x-morphoid");
      
      String text;
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
            printBaseformsParts(analysis.get(WORDBASES));
          }
        }
      }
    }
    finally {
      voikko.terminate();
    }
  }

  private static void printBaseformsParts(String wordbases) {
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
          WordComponent wc = new WordComponent();
          if (isDerivSuffix) {
            wc.component = wordBuilder.toString() + baseBuilder.toString();
            wc.lengthInOriginal = wordBuilder.length() + wordPartBuilder.length();
            wordBuilder.append(wordPartBuilder);
          }
          else {
            wc.component = baseBuilder.toString();
            wc.lengthInOriginal = wordPartBuilder.length();
            wordBeginIndex = wordEndIndex - wc.lengthInOriginal;
            wordBuilder.append(wordPartBuilder);
          }
          wc.startInOriginal = wordBeginIndex;
          print(wc);
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
  
  private static void print(WordComponent component) {
    System.out.println(component.component + " [" + component.startInOriginal + ":" + component.lengthInOriginal + "]");
  }
}
