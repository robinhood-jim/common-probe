package com.robin.probe.javaagent.scan;

import io.github.classgraph.*;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MethodScanner {
    //private static final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
    public static final Map<String, ClassEnhacerRs> getMatchedMethod(ClassInfoList classInfos,ClassMatchConfig config,IParameterAware parameterAware){
        Map<String,ClassEnhacerRs> classMap=new HashMap<>();
        classInfos.forEach(classInfo -> {
            ClassEnhacerRs rs=new ClassEnhacerRs();
            Map<String, ImmutablePair<String,List<MethodParamterEnhacerRs>>> methodMap=new HashMap<>();
            MethodInfoList methodInfos=classInfo.getMethodInfo();
            methodInfos.forEach(methodInfo -> {
                if(config.getAnnotationMethod()!=null){
                    if(methodInfo.hasAnnotation(config.getAnnotationMethod().getCanonicalName())){
                        methodMap.put(methodInfo.getName(),getMethodParamter(methodInfo,methodInfo.getAnnotationInfo(config.getAnnotationMethod().getCanonicalName()),config,(parameterAware==null)?MethodScanner::getLogMentionedParameterByAnnotation:parameterAware));
                    }
                }else{
                    if(config.getMethodRegexs()!=null && !config.getMethodRegexs().isEmpty()){
                        for(RegexConfig methodConfig:config.getMethodRegexs()){
                            if(methodConfig.getMethodNamePattern().matcher(methodInfo.getName()).find()){
                                methodMap.put(methodInfo.getName(),getLogMentionedParameterByConfig(methodInfo,methodConfig));
                                break;
                            }
                        }
                    }else{
                        // full paramter
                        methodMap.put(methodInfo.getName(),getLogMentionedParameterByConfig(methodInfo,null));
                    }

                }
            });
            rs.setMethodMap(methodMap);
            classMap.put(classInfo.getName(),rs);
        });
        return classMap;
    }

    private static final ImmutablePair<String,List<MethodParamterEnhacerRs>> getMethodParamter(MethodInfo methodInfo,AnnotationInfo info,ClassMatchConfig config,IParameterAware parameterAware){
        return parameterAware.getMentionedParameter(methodInfo,info,config);
    }
    private static final ImmutablePair<String,List<MethodParamterEnhacerRs>> getLogMentionedParameterByConfig(MethodInfo info,RegexConfig config){
        MethodParameterInfo[] parameterInfos=info.getParameterInfo();
        List<MethodParamterEnhacerRs> retList=new ArrayList<>();
        String[] paramNames=null;//discoverer.getParameterNames(info.loadClassAndGetMethod());
        if(config!=null && config.getMetionedParamters()!=null && config.getMetionedParamters().length>0){
            Integer[] posArr=config.getMetionedParamters();
            for(Integer pos:posArr){
                retList.add(getMethodParameter(parameterInfos[pos-1],pos,paramNames));
            }
        }else{
            for(int i=0;i<parameterInfos.length;i++){
                retList.add(getMethodParameter(parameterInfos[i],i+1,paramNames));
            }
        }
        return new ImmutablePair(null,retList);
    }
    private static final ImmutablePair<String,List<MethodParamterEnhacerRs>> getLogMentionedParameterByAnnotation(MethodInfo methodInfo, AnnotationInfo info, ClassMatchConfig config){
        String[] fields = (info.getParameterValues().getValue(config.getAnnotationFieldSpec())!=null)?(String[]) info.getParameterValues().getValue("fields"):null;
        String description=(info.getParameterValues().getValue(config.getAnnotationDescSpec())!=null)?info.getParameterValues().getValue(config.getAnnotationDescSpec()).toString():null;
        List<MethodParamterEnhacerRs> retList=new ArrayList<>();
        MethodParameterInfo[] parameterInfos=methodInfo.getParameterInfo();
        String[] paramNames=null;//discoverer.getParameterNames(methodInfo.loadClassAndGetMethod());
        if(fields!=null){
            for(String fieldPos:fields){
                Integer pos=getFieldPos(fieldPos);
                if(pos!=null && pos<=parameterInfos.length){
                    retList.add(getMethodParameter(parameterInfos[pos-1],pos,paramNames));
                }
            }
        }else{
            for(int i=0;i<parameterInfos.length;i++){
                retList.add(getMethodParameter(parameterInfos[i],i+1,paramNames));
            }
        }
        return new ImmutablePair(description,retList);
    }
    private static final Integer getFieldPos(String inputPos){
        if(NumberUtils.isDigits(inputPos)){
            return Integer.parseInt(inputPos);
        }else{
            return null;
        }
    }
    private static final MethodParamterEnhacerRs getMethodParameter(MethodParameterInfo parameterInfo, int pos,String[] paramNames){
        MethodParamterEnhacerRs config=new MethodParamterEnhacerRs();
        config.setParamPos(pos);
        if(parameterInfo.getName()!=null) {
            config.setName(parameterInfo.getName());
        }
        /*else{
            config.setName(paramNames[pos-1]);
        }*/
        config.setParamType(parameterInfo.getTypeDescriptor().toString());
        return config;
    }
}
