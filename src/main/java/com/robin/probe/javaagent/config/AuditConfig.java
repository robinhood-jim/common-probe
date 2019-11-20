package com.robin.probe.javaagent.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Setter
@Getter
public class AuditConfig {
    private String basePackage;
    private List<String> annotationPackages;
    private List<ScanPackage> scanPackages;
    @Getter
    @Setter
    public static class ScanPackage{
        String packageName;
        String monitorMethods;
        String monitorClasses;

    }
    @Setter
    @Getter
    public static class MonitorClass{
        String monitorClass;
        List<String> monitorMethod;
        public MonitorClass(String monitorClass,String[] methods){
            this.monitorClass=monitorClass;
            this.monitorMethod= Arrays.asList(methods);
        }
    }

}

