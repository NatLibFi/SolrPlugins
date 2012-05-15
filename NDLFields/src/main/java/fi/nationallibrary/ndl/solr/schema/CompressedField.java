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


import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.AbstractField;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexWriter; // for javadoc
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.UnicodeUtil;

import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A field that will compress it contents while still allowing its contents
 * to be indexed.
 */
public final class CompressedField extends AbstractField implements Fieldable, Serializable {

	/** generated serial version id */
	private static final long serialVersionUID = -1804212401473793170L;

	/** the uncompressed form of fieldsData */
	private Object uncompressedFieldsData;

	/**
	 * The value of the field as a String, or null. If null, the Reader value or
	 * binary value is used. Exactly one of stringValue(), readerValue(), and
	 * getBinaryValue() must be set.
	 */
	public String stringValue() {
		if (fieldsData instanceof String)
			return (String) fieldsData;
		else if (uncompressedFieldsData instanceof String)
			return (String) uncompressedFieldsData;
		else if (fieldsData instanceof byte[])
			try {
				byte[] val = (byte[]) fieldsData;
				return decompressString(val, 0, val.length);
			} catch (DataFormatException e) {
				return null;
			}
		else
			return null;
	}

	/**
	 * The value of the field as a Reader, or null. If null, the String value or
	 * binary value is used. Exactly one of stringValue(), readerValue(), and
	 * getBinaryValue() must be set.
	 */
	public Reader readerValue() {
		return fieldsData instanceof Reader ? (Reader) fieldsData : null;
	}

	/**
	 * The TokesStream for this field to be used when indexing, or null. If null,
	 * the Reader value or String value is analyzed to produce the indexed tokens.
	 */
	public TokenStream tokenStreamValue() {
		return tokenStream;
	}

	/**
	 * Create a field by specifying its name, value and how it will be saved in
	 * the index. Term vectors will not be stored in the index.
	 *
	 * @param name        The name of the field
	 * @param value       The string to process
	 * @param store       Whether <code>value</code> should be stored in the index
	 * @param index       Whether the field should be indexed, and if so, if it should be tokenized before indexing
	 * @param compressionLevel  the compression level to apply. must be between Deflater.BEST_SPEED (1) and Deflater.BEST_COMPRESSION (9)
	 * @throws NullPointerException  if name or value is <code>null</code>
	 * @throws IllegalArgumentException  if the field is neither stored nor indexed
	 */
	public CompressedField(String name, String value, Store store, Index index, int compressionLevel) {
		this(name, value, store, index, TermVector.NO, compressionLevel);
	}

	/**
	 * Create a field by specifying its name, value and how it will be saved in
	 * the index.
	 * @param name        The name of the field
	 * @param value       The string to process
	 * @param store       Whether <code>value</code> should be stored in the index
	 * @param index       Whether the field should be indexed, and if so, if it should be tokenized before indexing
	 * @param termVector  Whether term vector should be stored
	 * @param compressionLevel  the compression level to apply. must be between Deflater.BEST_SPEED (1) and Deflater.BEST_COMPRESSION (9)
	 * @throws NullPointerException  if name or value is <code>null</code>
	 * @throws IllegalArgumentException  in any of the following situations:
	 *           <ul>
	 *           <li>the field is neither stored nor indexed</li>
	 *           <li>the field is not indexed but termVector is
	 *           <code>TermVector.YES</code></li>
	 *           </ul>
	 */
	public CompressedField(String name, String value, Store store, Index index, TermVector termVector, int compressionLevel) {
		this(name, true, value, store, index, termVector, compressionLevel);
	}

	/**
	 * Create a field by specifying its name, value and how it will be saved in
	 * the index.
	 * @param name  			The name of the field
	 * @param internName  Whether to .intern() name or not
	 * @param value       The string to process
	 * @param store       Whether <code>value</code> should be stored in the index
	 * @param index       Whether the field should be indexed, and if so, if it should be tokenized before indexing
	 * @param termVector  Whether term vector should be stored
	 * @param compressionLevel  the compression level to apply. must be between Deflater.BEST_SPEED (1) and Deflater.BEST_COMPRESSION (9)
	 * @throws NullPointerException  if name or value is <code>null</code>
	 * @throws IllegalArgumentException in any of the following situations:
	 *           <ul>
	 *           <li>the field is neither stored nor indexed</li>
	 *           <li>the field is not indexed but termVector is
	 *           <code>TermVector.YES</code></li>
	 *           </ul>
	 */
	public CompressedField(String name, boolean internName, String value, Store store, Index index, TermVector termVector, int compressionLevel) {
		if (name == null)
			throw new NullPointerException("name cannot be null");
		if (value == null)
			throw new NullPointerException("value cannot be null");
		if (name.length() == 0 && value.length() == 0)
			throw new IllegalArgumentException("name and value cannot both be empty");
		if (index == Index.NO && store == Store.NO)
			throw new IllegalArgumentException("it doesn't make sense to have a field that " + "is neither indexed nor stored");
		if (index == Index.NO && termVector != TermVector.NO)
			throw new IllegalArgumentException("cannot store term vector information " + "for a field that is not indexed");

		if (internName) // field names are optionally interned
			name = StringHelper.intern(name);

		this.name = name;

		this.uncompressedFieldsData = value;
		this.fieldsData = compressString(value, compressionLevel);
		this.binaryOffset = 0;
		this.binaryLength = ((byte[]) fieldsData).length;

		this.isStored = store.isStored();

		this.isIndexed = index.isIndexed();
		this.isTokenized = index.isAnalyzed();
		this.omitNorms = index.omitNorms();
		if (index == Index.NO) {
			this.indexOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
		}

		this.isBinary = true;

		setStoreTermVector(termVector);
	}

	// Copied from org.apache.lucene.document.CompressionTools
	// Added support for decompressing a string from a within buffer

  /** Compresses the specified byte range using the
   *  specified compressionLevel (constants are defined in
   *  java.util.zip.Deflater). */
  public static byte[] compress(byte[] value, int offset, int length, int compressionLevel) {

    /* Create an expandable byte array to hold the compressed data.
     * You cannot use an array that's the same size as the orginal because
     * there is no guarantee that the compressed data will be smaller than
     * the uncompressed data. */
    ByteArrayOutputStream bos = new ByteArrayOutputStream(length);

    Deflater compressor = new Deflater();

    try {
      compressor.setLevel(compressionLevel);
      compressor.setInput(value, offset, length);
      compressor.finish();

      // Compress the data
      final byte[] buf = new byte[1024];
      while (!compressor.finished()) {
        int count = compressor.deflate(buf);
        bos.write(buf, 0, count);
      }
    } finally {
      compressor.end();
    }

    return bos.toByteArray();
  }

  /** Compresses the String value using the specified
   *  compressionLevel (constants are defined in
   *  java.util.zip.Deflater).
   *  @param value		the value to compress
   *  @param compressionLevel		the compression level to use. must be between Deflater.BEST_SPEED (1) and Deflater.BEST_COMPRESSION (9)
   */
  public static byte[] compressString(String value, int compressionLevel) {
    UnicodeUtil.UTF8Result result = new UnicodeUtil.UTF8Result();
    UnicodeUtil.UTF16toUTF8(value, 0, value.length(), result);
    return compress(result.result, 0, result.length, compressionLevel);
  }

  /** Decompress the byte array previously returned by
   *  compress */
  public static byte[] decompress(byte[] value, int offset, int length) throws DataFormatException {
    // Create an expandable byte array to hold the decompressed data
    ByteArrayOutputStream bos = new ByteArrayOutputStream(value.length);

    Inflater decompressor = new Inflater();

    try {
      decompressor.setInput(value, offset, length);

      // Decompress the data
      final byte[] buf = new byte[1024];
      while (!decompressor.finished()) {
        int count = decompressor.inflate(buf);
        bos.write(buf, 0, count);
      }
    } finally {
      decompressor.end();
    }

    return bos.toByteArray();
  }

  /** Decompress the byte array previously returned by
   *  compressString back into a String
   *  @param value		the string encoded as a UTF-8 compressed byte array
   *  @param offset		the offset in the buffer that the string starts
   *  @param length		the length of the string
   */
  public static String decompressString(byte[] value, int offset, int length) throws DataFormatException {
    UnicodeUtil.UTF16Result result = new UnicodeUtil.UTF16Result();
    final byte[] bytes = decompress(value, offset, length);
    UnicodeUtil.UTF8toUTF16(bytes, 0, bytes.length, result);

    return new String(result.result, 0, result.length);
  }

}
