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
import java.util.Collection;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.index.IndexReader;
import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.spelling.SolrSpellChecker;
import org.apache.solr.spelling.SpellingResult;

/**
 * 
 * @author ere.maijala@helsinki.fi
 *
 */
public class VoikkoSpellChecker extends SolrSpellChecker {

	@Override
	public void build(SolrCore arg0, SolrIndexSearcher arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public SpellingResult getSuggestions(Collection<Token> arg0,
			IndexReader arg1, int arg2, boolean arg3, boolean arg4)
			throws IOException {
		SpellingResult result = new SpellingResult();
		// TODO
		return result;
	}

	@Override
	public void reload(SolrCore arg0, SolrIndexSearcher arg1)
			throws IOException {
		// TODO Auto-generated method stub

	}

}
