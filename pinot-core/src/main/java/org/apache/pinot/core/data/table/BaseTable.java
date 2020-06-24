/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.core.data.table;

import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pinot.common.utils.DataSchema;
import org.apache.pinot.core.query.aggregation.function.AggregationFunction;
import org.apache.pinot.core.query.request.context.OrderByExpressionContext;


/**
 * Base abstract implementation of Table
 */
@SuppressWarnings("rawtypes")
public abstract class BaseTable implements Table {
  protected final DataSchema _dataSchema;
  protected final int _numColumns;
  protected final AggregationFunction[] _aggregationFunctions;
  protected final int _numAggregations;

  // the capacity we need to trim to
  protected int _capacity;
  // the capacity with added buffer, in order to collect more records than capacity for better precision
  protected int _maxCapacity;

  protected boolean _isOrderBy;
  protected TableResizer _tableResizer;

  /**
   * Initializes the variables and comparators needed for the table
   */
  public BaseTable(DataSchema dataSchema, AggregationFunction[] aggregationFunctions,
      @Nullable List<OrderByExpressionContext> orderByExpressions, int capacity) {
    _dataSchema = dataSchema;
    _numColumns = dataSchema.size();
    _aggregationFunctions = aggregationFunctions;
    _numAggregations = aggregationFunctions.length;
    addCapacityAndOrderByInfo(orderByExpressions, capacity);
  }

  protected void addCapacityAndOrderByInfo(@Nullable List<OrderByExpressionContext> orderByExpressions, int capacity) {
    if (orderByExpressions != null) {
      _isOrderBy = true;
      _tableResizer = new TableResizer(_dataSchema, _aggregationFunctions, orderByExpressions);

      // TODO: tune these numbers and come up with a better formula (github ISSUE-4801)
      // Based on the capacity and maxCapacity, the resizer will smartly choose to evict/retain recors from the PQ
      if (capacity
          <= 100_000) { // Capacity is small, make a very large buffer. Make PQ of records to retain, during resize
        _maxCapacity = 1_000_000;
      } else { // Capacity is large, make buffer only slightly bigger. Make PQ of records to evict, during resize
        _maxCapacity = (int) (capacity * 1.2);
      }
    } else {
      _isOrderBy = false;
      _tableResizer = null;
      _maxCapacity = capacity;
    }
    _capacity = capacity;
  }

  @Override
  public boolean merge(Table table) {
    Iterator<Record> iterator = table.iterator();
    while (iterator.hasNext()) {
      upsert(iterator.next());
    }
    return true;
  }

  @Override
  public DataSchema getDataSchema() {
    return _dataSchema;
  }
}
