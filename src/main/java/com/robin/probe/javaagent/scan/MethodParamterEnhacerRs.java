package com.robin.probe.javaagent.scan;

import lombok.Data;

@Data
public class MethodParamterEnhacerRs {
    private int paramPos;
    private String name;
    private String paramType;
    private String format;
    private String description;

}
