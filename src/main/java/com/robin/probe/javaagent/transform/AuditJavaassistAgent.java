package com.robin.probe.javaagent.transform;

import java.lang.instrument.Instrumentation;

public class AuditJavaassistAgent {
    public static void premain(String args, Instrumentation instrumentation){
        //instrumentation.addTransformer(new AuditLogClassTransformer(args));
        instrumentation.addTransformer(new ConfigClassTransformer(args));
    }
}
