package com.robin.probe.javaagent.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class ScanConfig {
    private String scanPackage;
    private List<ScanParameter> parameters;
    private String enhacer="brave";
    @Setter
    @Getter
    public static class ScanParameter{
        private String annotationClass;
        private String annotationMethod;
        private String classRegex;
        private String methodRegex;
        private String basePackage;
    }
}
