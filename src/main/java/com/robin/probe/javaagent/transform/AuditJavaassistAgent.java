package com.robin.probe.javaagent.transform;

import java.lang.instrument.Instrumentation;

/**
 * <p>Created at: 2019-10-21 14:23:03</p>
 *
 * @author robinjim
 * @version 1.0
 */
public class AuditJavaassistAgent {
    public static void premain(String args, Instrumentation instrumentation){
        //instrumentation.addTransformer(new AuditLogClassTransformer(args));
        instrumentation.addTransformer(new ConfigClassTransformer(args));
    }
}
