/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.cube.kv;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.io.LongWritable;
import org.apache.kylin.common.util.LocalFileMetadataTestCase;
import org.apache.kylin.cube.CubeManager;
import org.apache.kylin.cube.model.CubeDesc;
import org.apache.kylin.cube.model.HBaseColumnDesc;
import org.apache.kylin.metadata.MetadataManager;
import org.apache.kylin.metadata.measure.MeasureCodec;
import org.apache.kylin.metadata.model.FunctionDesc;
import org.apache.kylin.metadata.model.MeasureDesc;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RowValueDecoderTest extends LocalFileMetadataTestCase {

    @Before
    public void setUp() throws Exception {
        this.createTestMetadata();
        MetadataManager.clearCache();
    }

    @After
    public void after() throws Exception {
        this.cleanupTestMetadata();
    }

    @Test
    public void testDecode() throws Exception {
        CubeDesc cubeDesc = CubeManager.getInstance(getTestConfig()).getCube("test_kylin_cube_with_slr_ready").getDescriptor();
        HBaseColumnDesc hbaseCol = cubeDesc.getHBaseMapping().getColumnFamily()[0].getColumns()[0];

        MeasureCodec codec = new MeasureCodec(hbaseCol.getMeasures());
        BigDecimal sum = new BigDecimal("333.1234567");
        BigDecimal min = new BigDecimal("333.1111111");
        BigDecimal max = new BigDecimal("333.1999999");
        LongWritable count = new LongWritable(2);
        LongWritable item_count = new LongWritable(99999);
        ByteBuffer buf = ByteBuffer.allocate(RowConstants.ROWVALUE_BUFFER_SIZE);
        codec.encode(new Object[] { sum, min, max, count,item_count }, buf);

        buf.flip();
        byte[] valueBytes = new byte[buf.limit()];
        System.arraycopy(buf.array(), 0, valueBytes, 0, buf.limit());

        RowValueDecoder rowValueDecoder = new RowValueDecoder(hbaseCol);
        for (MeasureDesc measure : cubeDesc.getMeasures()) {
            FunctionDesc aggrFunc = measure.getFunction();
            int index = hbaseCol.findMeasureIndex(aggrFunc);
            rowValueDecoder.setIndex(index);
        }

        rowValueDecoder.decode(valueBytes);
        List<String> measureNames = rowValueDecoder.getNames();
        Object[] measureValues = rowValueDecoder.getValues();

        assertEquals("[PRICE, MIN_PRICE_, MAX_PRICE_, COUNT__, ITEM_COUNT]", measureNames.toString());
        assertEquals("[333.1234567, 333.1111111, 333.1999999, 2, 99999]", Arrays.toString(measureValues));
    }

}
