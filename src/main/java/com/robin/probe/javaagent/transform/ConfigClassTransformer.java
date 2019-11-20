package com.robin.probe.javaagent.transform;

import com.robin.core.base.util.StringUtils;
import com.robin.core.query.util.ConfigResourceScanner;
import com.robin.probe.reflect.ClassGraphReflector;
import com.robin.probe.javaagent.annotation.AuditLog;
import com.robin.probe.javaagent.config.ScanConfig;
import com.robin.probe.javaagent.enhancer.BraveEnhancer;
import com.robin.probe.javaagent.enhancer.IEnhancer;
import com.robin.probe.javaagent.scan.ClassEnhacerRs;
import com.robin.probe.javaagent.scan.MethodParamterEnhacerRs;
import com.robin.probe.javaagent.scan.RegexConfig;
import com.robin.probe.javaagent.scan.ClassMatchConfig;
import com.robin.probe.javaagent.scan.ClassScanner;
import com.robin.probe.javaagent.scan.MethodScanner;
import io.github.classgraph.ClassInfoList;
import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.regex.Pattern;


public class ConfigClassTransformer implements ClassFileTransformer {
    private ClassPool classPool;
    private String scanPath = "probe.yml";
    private Class<? extends IEnhancer> enhacerClass;
    private IEnhancer enhacer;
    private List<ClassMatchConfig> configRules=new ArrayList<>();
    private ClassGraphReflector classGraphReflector;
    private Map<String, ClassEnhacerRs> classEnhacerMap=new HashMap<>();
    private Logger logger= LoggerFactory.getLogger(getClass());
    private ScanConfig scanConfig;
    public ConfigClassTransformer(String scanPath){
        if(!StringUtils.isEmpty(scanPath)){
            this.scanPath=scanPath;
        }
        InputStream stream = ConfigResourceScanner.loadResource(this.scanPath);
        Yaml yaml = new Yaml(new Constructor(ScanConfig.class));
        scanConfig = yaml.load(stream);
        classPool = ClassPool.getDefault();
        try {
            initConfig(scanConfig);
            logger.info("{}",classEnhacerMap.keySet());
        }catch (Exception ex){
            logger.error("{}",ex);
        }
    }
    private void initConfig(ScanConfig config) throws Exception{
        if(StringUtils.isEmpty(config.getEnhacer())){
            if("brave".equalsIgnoreCase(config.getEnhacer())){
                enhacerClass= BraveEnhancer.class;
            }else{
                enhacerClass=(Class<? extends IEnhancer>) Class.forName(config.getEnhacer());
            }
        }else{
            enhacerClass= BraveEnhancer.class;
        }
        enhacer=enhacerClass.newInstance();
        String basePackage=null;
        if(!StringUtils.isEmpty(config.getScanPackage())){
            basePackage=config.getScanPackage();
        }
        if(config.getParameters()!=null && !config.getParameters().isEmpty()){
            for(ScanConfig.ScanParameter parameter:config.getParameters()){
                ClassMatchConfig matchConfig=new ClassMatchConfig();
                if(!StringUtils.isEmpty(parameter.getBasePackage())){
                    matchConfig.setScanPackage(parameter.getBasePackage());
                }
                else if(!StringUtils.isEmpty(basePackage)){
                    matchConfig.setScanPackage(basePackage);
                }
                if(!StringUtils.isEmpty(parameter.getAnnotationClass())){
                    matchConfig.setAnnotationClass(Class.forName(parameter.getAnnotationClass()));
                }
                if(!StringUtils.isEmpty(parameter.getAnnotationMethod())){
                    if("AuditLog".equalsIgnoreCase(parameter.getAnnotationMethod())){
                        matchConfig.setAnnotationMethod(AuditLog.class);
                    }else {
                        matchConfig.setAnnotationMethod(Class.forName(parameter.getAnnotationMethod()));
                    }
                }
                if(!StringUtils.isEmpty(parameter.getClassRegex())){
                    matchConfig.setClassNamePattern(Pattern.compile(parameter.getClassRegex()));
                }
                if(!StringUtils.isEmpty(parameter.getMethodRegex())){
                    List<RegexConfig> list=new ArrayList<>();
                    String[] methodStrArr=parameter.getMethodRegex().split("\\|");
                    for(String methodStr:methodStrArr){
                        RegexConfig regexConfig=new RegexConfig();
                        String[] methodParamArr=methodStr.split(" ");
                        if(!"*".equals(methodParamArr[1])){
                            regexConfig.setMetionedParamters(parseParameters(methodParamArr[1].split(",")));
                        }
                        regexConfig.setMethodNamePattern(Pattern.compile(methodParamArr[0]));
                        list.add(regexConfig);
                    }
                    matchConfig.setMethodRegexs(list);
                }
                configRules.add(matchConfig);
            }
        }
        classGraphReflector = new ClassGraphReflector(basePackage);
        if(!configRules.isEmpty()){
            for(ClassMatchConfig classMatchConfig:configRules) {
                ClassInfoList classInfos = ClassScanner.scanPackage(classGraphReflector,classGraphReflector.getScanResult(),classMatchConfig);
                Map<String, ClassEnhacerRs> classMap= MethodScanner.getMatchedMethod(classInfos,classMatchConfig,null);
                Iterator<Map.Entry<String,ClassEnhacerRs>> iter=classMap.entrySet().iterator();
                while(iter.hasNext()){
                    Map.Entry<String,ClassEnhacerRs> entry=iter.next();
                    classEnhacerMap.putIfAbsent(entry.getKey(),entry.getValue());
                }
            }
        }
    }
    private Integer[] parseParameters(String[] parameterPosArr){
        List<Integer> list=new ArrayList<>();
        Integer[] intArr=new Integer[1];
        for(String parameterPos:parameterPosArr){
            if(NumberUtils.isDigits(parameterPos)){
                list.add(Integer.parseInt(parameterPos));
            }
        }
        return list.toArray(intArr);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        String clazzName = className.replaceAll("/", ".");
        try {
            classPool.insertClassPath(new ByteArrayClassPath(className, classfileBuffer));
            //need to enhance
            if (classEnhacerMap.containsKey(clazzName)) {
                CtClass ctClass = classPool.get(clazzName);
                ClassEnhacerRs rs=classEnhacerMap.get(clazzName);
                Map<String, ImmutablePair<String,List<MethodParamterEnhacerRs>>> map=rs.getMethodMap();
                Iterator<Map.Entry<String,ImmutablePair<String,List<MethodParamterEnhacerRs>>>> iterator=map.entrySet().iterator();
                while(iterator.hasNext()){
                    Map.Entry<String,ImmutablePair<String,List<MethodParamterEnhacerRs>>> entry=iterator.next();
                    CtMethod method = ctClass.getDeclaredMethod(entry.getKey());
                    enhacer.enhace(ctClass,method,entry.getValue().getLeft(),rs);
                }
                return ctClass.toBytecode();
            }else{
                return classfileBuffer;
            }
        }catch (Exception ex){
            logger.error("{}",ex);
        }
        return classfileBuffer;
    }
}
