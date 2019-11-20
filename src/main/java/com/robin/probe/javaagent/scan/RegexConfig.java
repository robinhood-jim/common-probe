package com.robin.probe.javaagent.scan;

import lombok.Data;

import java.util.regex.Pattern;

@Data
public class RegexConfig {
    private Pattern methodNamePattern;
    private Integer[] metionedParamters;
}
