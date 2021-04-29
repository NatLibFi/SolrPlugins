/*
 * Copyright (C) 2012-2017 University of Helsinki (The National Library of Finland)
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
 * Helper class to hold decompounded token information
 */

package fi.nationallibrary.ndl.solrvoikko2;

public class CompoundToken {
  public final CharSequence txt;
  public final int position;

  /** Construct the compound token based on a slice of the current {@link CompoundWordTokenFilterBase#termAtt}. */
  public CompoundToken(CharSequence txt, int position) {
    this.txt = txt;
    this.position = position;
  }

  public int hashCode() {
    return this.position;
  }

  /**
   * Compare objects
   *
   * @return boolean
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof CompoundToken)) {
      return false;
    }
    CompoundToken t2 = (CompoundToken) obj;

    return t2.txt.equals(txt)
      && t2.position == position;
  }
}
