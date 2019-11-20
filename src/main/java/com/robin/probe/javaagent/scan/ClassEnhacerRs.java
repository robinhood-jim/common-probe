package com.robin.probe.javaagent.scan;

import lombok.Data;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.List;
import java.util.Map;

@Data
public class ClassEnhacerRs {
    private Map<String, ImmutablePair<String,List<MethodParamterEnhacerRs>>> methodMap;

}
