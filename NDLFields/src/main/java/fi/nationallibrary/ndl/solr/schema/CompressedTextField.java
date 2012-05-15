package fi.nationallibrary.ndl.solr.schema;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.solr.common.SolrException;
import org.apache.solr.response.TextResponseWriter;
import org.apache.solr.response.XMLWriter;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.TextField;

/**
 *	A text field that will be compressed if it exceeds compression threshold in length
 *  using the supplied compression level.
 */
public class CompressedTextField extends TextField {

	/** the compression level to use */
	protected int compressionLevel = Deflater.BEST_COMPRESSION;

	/** only compress fields that are longer than this threshold */
	protected int compressionThreshold = 0;

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.apache.solr.schema.TextField#init(org.apache.solr.schema.IndexSchema,
	 * java.util.Map)
	 */
	@Override
	protected void init(IndexSchema schema, Map<String, String> args) {
		String compLevel = args.remove("compressionlevel");
		if (compLevel != null)
			try {
				compressionLevel = Integer.parseInt(compLevel);
			} catch (NumberFormatException nfe) {
				compressionLevel = Deflater.BEST_COMPRESSION;
			}
		if (compressionLevel < Deflater.BEST_SPEED) compressionLevel = Deflater.BEST_SPEED;
		if (compressionLevel > Deflater.BEST_COMPRESSION) compressionLevel = Deflater.BEST_COMPRESSION;

		String compThreshold = args.remove("compressThreshold");
		if (compThreshold == null) compThreshold = args.remove("compressionThreshold");
		if (compThreshold != null)
			try {
				compressionThreshold = Integer.parseInt(compThreshold);
			} catch (NumberFormatException nfe) {
				compressionThreshold = 0;
			}
		if (compressionThreshold < 0) compressionThreshold = 0;

		super.init(schema, args);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.apache.solr.schema.FieldType#createField(org.apache.solr.schema.SchemaField, java.lang.String, float)
	 */
	@Override
	public Fieldable createField(SchemaField field, String externalVal, float boost) {
		if (!field.indexed() && !field.stored()) {
			if (log.isTraceEnabled())
				log.trace("Ignoring unindexed/unstored field: " + field);
			return null;
		}

		String val = null;
		try {
			val = toInternal(externalVal);
		} catch (RuntimeException e) {
			throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Error while creating field '" + field + "' from value '" + externalVal + "'", e, false);
		}
		if (val == null)
			return null;

		Fieldable f;
		if (val.length() > compressionThreshold) {
			f = new CompressedField(field.getName(), val, getFieldStore(field, val), getFieldIndex(field, val), getFieldTermVec(field, val), compressionLevel);
		} else {
			f = new Field(field.getName(), val, getFieldStore(field, val), getFieldIndex(field, val), getFieldTermVec(field, val));
		}
		f.setOmitNorms(field.omitNorms());
		if (field.omitTermFreqAndPositions()) {
			if (field.omitPositions()) {
				f.setIndexOptions(IndexOptions.DOCS_ONLY);
		  } else {
		  	f.setIndexOptions(IndexOptions.DOCS_AND_FREQS);  
		  }
		} else {
			f.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);  
		}
		f.setBoost(boost);

		return f;
	}

	/* (non-Javadoc)
   * @see org.apache.solr.schema.TextField#write(org.apache.solr.response.XMLWriter, java.lang.String, org.apache.lucene.document.Fieldable)
   */
  @Override
  public void write(XMLWriter xmlWriter, String name, Fieldable f) throws IOException {
  	xmlWriter.writeStr(name, toExternal(f));
  }

	/* (non-Javadoc)
   * @see org.apache.solr.schema.TextField#write(org.apache.solr.response.TextResponseWriter, java.lang.String, org.apache.lucene.document.Fieldable)
   */
  @Override
  public void write(TextResponseWriter writer, String name, Fieldable f) throws IOException {
  	writer.writeStr(name, toExternal(f), true);
  }

	/* (non-Javadoc)
   * @see org.apache.solr.schema.FieldType#toExternal(org.apache.lucene.document.Fieldable)
   */
  @Override
  public String toExternal(Fieldable f) {
  	if (f.isBinary()) {
	    try {
		return CompressedField.decompressString(f.getBinaryValue(), f.getBinaryOffset(), f.getBinaryLength());
	    } catch (DataFormatException e) {
		return super.toExternal(f);
	    }
  	} else {
  		return super.toExternal(f);
  	}
  }

}
