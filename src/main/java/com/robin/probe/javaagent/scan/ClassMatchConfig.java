package com.robin.probe.javaagent.scan;

import lombok.Data;

import java.util.List;
import java.util.regex.Pattern;

@Data
public class ClassMatchConfig {
    private Class<?> annotationClass;
    private Class<?> annotationMethod;
    private Class<?> implementClass;
    private Class<?> subClass;
    private List<RegexConfig> methodRegexs;
    private Pattern classNamePattern;
    private String scanPackage;
    private String annotationFieldSpec="fields";
    private String annotationDescSpec="description";

}
