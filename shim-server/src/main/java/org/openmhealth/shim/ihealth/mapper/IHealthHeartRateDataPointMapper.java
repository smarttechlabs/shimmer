/*
 * Copyright 2015 Open mHealth
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

package org.openmhealth.shim.ihealth.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import org.openmhealth.schema.domain.omh.DataPoint;
import org.openmhealth.schema.domain.omh.HeartRate;

import java.util.List;
import java.util.Optional;

import static org.openmhealth.shim.common.mapper.JsonNodeMappingSupport.asRequiredDouble;


/**
 * @author Chris Schaefbauer
 */
public class IHealthHeartRateDataPointMapper extends IHealthDataPointMapper<HeartRate>{


    @Override
    protected List<String> getListNodeNames() {

        return Lists.newArrayList("BPDataList","BODataList");
    }

    @Override
    protected Optional<String> getUnitPropertyNameForMeasure() {
        return Optional.empty();
    }

    @Override
    protected Optional<DataPoint<HeartRate>> asDataPoint(JsonNode listNode, Integer measureUnit) {

        double heartRateValue = asRequiredDouble(listNode,"HR");
        HeartRate.Builder heartRateBuilder = new HeartRate.Builder(heartRateValue);
        setEffectiveTimeFrameIfExists(listNode,heartRateBuilder);
        HeartRate heartRate = heartRateBuilder.build();
        return Optional.of(new DataPoint<>(createDataPointHeader(listNode,heartRate),heartRate));
    }
}