package com.robin.probe.javaagent.scan;

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.MethodInfo;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.List;

@FunctionalInterface
public interface IParameterAware {
    ImmutablePair<String,List<MethodParamterEnhacerRs>> getMentionedParameter(MethodInfo methodInfo, AnnotationInfo info, ClassMatchConfig config);
}
