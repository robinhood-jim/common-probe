package com.robin.probe.javaagent.enhancer;

import com.robin.probe.javaagent.scan.ClassEnhacerRs;
import javassist.CtClass;
import javassist.CtMethod;


public interface IEnhancer {
    void enhace(CtClass ctClass, CtMethod method, String methodName, ClassEnhacerRs classEnhacerRs) throws Exception;
}
