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

import java.util.List;

import org.puimula.libvoikko.Analysis;
import org.puimula.libvoikko.Voikko;

/**
 * 
 * @author ere.maijala@helsinki.fi
 *
 */
public class App
{
	private static final String CLASS_ATTR = "CLASS";
	private static final String BASEFORM_ATTR = "BASEFORM";
	private static final String WORDBASES_ATTR = "WORDBASES";
  private static final String WORDIDS_ATTR = "WORDIDS";

    public static void main(String[] args)
    {
        if (args.length <= 0) {
            System.out.println("Enter the term to analyze as a command line parameter");
            System.exit(1);
        }
        Voikko voikko = new Voikko("fi-x-morphoid");
        String term = args[0];
        System.out.println("Term to analyze: " + term);
        List<Analysis> analysisList = voikko.analyze(term);
        analysisList = voikko.analyze(term);
        if (!analysisList.isEmpty())
        {
            int i = 0;
            String baseform = term;
            int wordLen = baseform.length();
            for (Analysis analysis: analysisList) {
                System.out.println("\nAnalysis " + (++i) + " (" + analysis.get(CLASS_ATTR) + "):\n");
                if (analysis.containsKey(BASEFORM_ATTR)) {
                    baseform = analysis.get(BASEFORM_ATTR);
                    System.out.println("Baseform: " + baseform);

                }
                if (analysis.containsKey(WORDBASES_ATTR)) {
                    String wordbases = analysis.get(WORDBASES_ATTR);
                    System.out.println("Word bases: " + wordbases);
                    System.out.println("  Parsed: ");
                    int offset = 0;
                    for (String wordbase: wordbases.split("\\+")) {
                      if (wordbase.length() < 2){
                        continue;
                      }
                      String base = wordbase;
                      int start = wordbase.indexOf('(');
                      if (start != -1) {
                        int end = wordbase.indexOf(')');
                        if (end != -1) {
                          base = wordbase.substring(start+1, end);
                        }
                      }
                      int len = base.length();
                      if (offset + len > wordLen) {
                        offset = wordLen - len;
                        if (offset < 0) {
                          offset = 0;
                          len = wordLen;
                        }
                      }
                      System.out.println("    " + base + ", " + offset + ", " + len + "\n");
                      offset += len;
                      
                    }

                    
                }
                if (analysis.containsKey(WORDIDS_ATTR)) {
                  String ids = analysis.get(WORDIDS_ATTR);
                  System.out.println("Word ids: " + ids);
              }
            }
        } else {
            System.out.println("No analysis results");
        }
    	System.exit(0);
    }

}
