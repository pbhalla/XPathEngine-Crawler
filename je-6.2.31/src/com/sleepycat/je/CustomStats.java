/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je;

/**
 * A custom statistics object. Custom statistics allow for customization
 * of statistics that are written at periodic intervals to the je.stats.csv
 * file. The field names returned from the getFieldNames() method are used as
 * column headers in the je.stat.csv file. The getFieldNames() method is only
 * called once when the environment is opened. The field values are associated
 * with the field names in the order of the returned array. The
 * getFieldValues() method is called when a row is written to the statistics
 * file. The semantic for the values are implementation specific. The values
 * may represent totals, incremental (since the last getFieldValues() call), or
 * stateless (computed at the time the statistic is requested).
 */
public interface CustomStats {

    /**
     * The field names that are output to the je.stats.csv file.
     *
     * @return Array of strings that represent the field values.
     */
    String[] getFieldNames();

    /**
     * The field values that are output to the je.stats.csv file.
     *
     * @return Array of strings that represent a value for the
     * associated field name.
     */
    String[] getFieldValues();
}
