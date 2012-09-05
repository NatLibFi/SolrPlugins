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

import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.*;
import org.apache.solr.common.SolrException;
import org.apache.solr.response.TextResponseWriter;
import org.apache.solr.response.XMLWriter;
import org.apache.solr.schema.AbstractSubTypeFieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.QParser;

import java.io.IOException;
import java.util.Map;

/**
 * Represents a range of two values within a single field. 
 */
public class RangeField extends AbstractSubTypeFieldType {
  protected String separator = ",";
  
  @Override
  protected void init(IndexSchema schema, Map<String, String> args) {
    super.init(schema, args);
    String separator = args.remove("separator");
    if(separator != null)
      this.separator = separator;
    
    createSuffixCache(3);
  }

  @Override
  public String toExternal(IndexableField f) {
    return subType.indexedToReadable(f.stringValue());
  }

  @Override
  public IndexableField[] createFields(SchemaField field, Object value, float boost) {
  	String externalVal = value.toString();
	  int separatorIndex = externalVal.indexOf(separator);
    if(separatorIndex == -1) {
	    throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Invalid range, provide start and end range separated by ',' or with specified separator.");
    }
    
    IndexableField[] f = new IndexableField[(field.indexed() ? 2 : 0) + (field.stored() ? 1 : 0)];
    if (field.indexed()) {
      f[0] = subField(field, 0)
          .createField(
              externalVal.substring(0,separatorIndex), // Range start 
              boost);
      f[1] = subField(field, 1)
          .createField(
              externalVal.substring(separatorIndex+1,externalVal.length()), // Range end 
              boost);
    }

    if (field.stored()) {
      String storedVal = externalVal;  // normalize or not?
      FieldType customType = new FieldType();
      customType.setStored(true);
      f[f.length - 1] = createField(field.getName(), storedVal, customType, 1f);
    }
    return f;
  }


  @Override
  public Query getRangeQuery(QParser parser, SchemaField field, String start, String end, boolean minInclusive, boolean maxInclusive) {
	  SchemaField startField = subField(field, 0);
	  SchemaField endField = subField(field, 1);
    BooleanQuery result = new BooleanQuery();
     
    result.add(startField.getType().getRangeQuery(parser, startField, null, end, minInclusive, maxInclusive), BooleanClause.Occur.MUST);
    result.add(endField.getType().getRangeQuery(parser, endField, start, null, minInclusive, maxInclusive), BooleanClause.Occur.MUST);

    return result;
  }
  
  @Override
  public boolean isPolyField() {
    return true;
  }

  @Override
  public void write(TextResponseWriter writer, String name, IndexableField f) throws IOException {
    writer.writeStr(name, f.stringValue(), false);
  }
  
  @Override
  public SortField getSortField(SchemaField field, boolean top) {
    throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Sorting not supported on RangeField " + field.getName());
  }

  @Override
  public IndexableField createField(SchemaField field, Object externalVal, float boost) {
    throw new UnsupportedOperationException("RangeField uses multiple fields.  field=" + field.getName());
  }

  public SchemaField getSubField(SchemaField field) {
    // Default to first field
    return subField(field, 0);
  }
  
  public SchemaField getSubField(SchemaField field, int n) {
    return subField(field, n);
  }
  
  @Override
  public org.apache.solr.schema.FieldType getSubType() {
    if(subType == null)
      subType = schema.getDynamicFieldType(suffix);  
    return subType;
  }
}