/*
 * Copyright (C) 2012-2017 The National Library of Finland
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
