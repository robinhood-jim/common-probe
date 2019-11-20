package com.robin.probe.javaagent.transform;

import com.google.common.eventbus.Subscribe;
import com.robin.core.query.util.ConfigResourceScanner;
import com.robin.probe.reflect.ClassGraphReflector;
import com.robin.probe.javaagent.annotation.AuditLog;
import com.robin.probe.javaagent.config.AuditConfig;
import io.github.classgraph.*;
import javassist.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public class AuditLogClassTransformer implements ClassFileTransformer {
    private ClassPool classPool;
    private String scanPath = "probe.yml";
    private AuditConfig auditConfig;
    private Map<String, Map<String, ImmutablePair<MethodInfo, List<ImmutablePair<Integer, MethodParameterInfo>>>>> probeMap = new HashMap<>();
    private ClassGraphReflector classGraphReflector;

    public AuditLogClassTransformer(String scanPath) {
        if (scanPath != null) {
            this.scanPath = scanPath;
        }
        InputStream stream = ConfigResourceScanner.loadResource(this.scanPath);
        Yaml yaml = new Yaml(new Constructor(AuditConfig.class));
        auditConfig = yaml.load(stream);
        classPool = ClassPool.getDefault();
        initConfig(auditConfig);
        log.debug("finish to init Probe");
    }

    public void initConfig(AuditConfig config) {
        classGraphReflector = new ClassGraphReflector(config.getBasePackage());
        ClassInfoList classInfos = classGraphReflector.getClassesByAnnotationMethod(AuditLog.class);
        //load with annotation method AuditLog
        List<String> annotationPackages = new ArrayList<>();
        if (config.getAnnotationPackages() != null && config.getAnnotationPackages().size() > 0) {
            annotationPackages.addAll(config.getAnnotationPackages());
        }

        classInfos.forEach(classInfo -> {
            MethodInfoList mList = classInfo.getMethodInfo();
            Map<String, ImmutablePair<MethodInfo, List<ImmutablePair<Integer, MethodParameterInfo>>>> mMap = new HashMap<>();
            if (annotationPackages.isEmpty()) {
                mList.forEach(methodInfo -> {
                    AnnotationInfo info = methodInfo.getAnnotationInfo(AuditLog.class.getCanonicalName());
                    AnnotationInfo info1=methodInfo.getAnnotationInfo(Subscribe.class.getCanonicalName());
                    MethodParameterInfo[] params = methodInfo.getParameterInfo();
                    if (info != null) {
                        List<ImmutablePair<Integer, MethodParameterInfo>> list = new ArrayList<>();
                        String[] fields = (String[])info.getParameterValues().getValue("fields");
                        if(fields.length==0) {
                            addParamterInfos(list, methodInfo.getParameterInfo());
                        } else{
                            for (int i = 0; i < fields.length; i++) {
                                if (Integer.parseInt(fields[i]) <= fields.length) {
                                    list.add(new ImmutablePair<>(Integer.parseInt(fields[i]), params[Integer.parseInt(fields[i]) - 1]));
                                }
                            }
                        }
                        mMap.put(methodInfo.getName(), new ImmutablePair(methodInfo, list));
                    }
                });
                probeMap.put(classInfo.getName(), mMap);
            } else {
                String packageName = classInfo.getPackageName();
                config.getAnnotationPackages().forEach(f -> {
                    if (packageName.startsWith(f)) {
                        mList.forEach(methodInfo -> {
                            AnnotationInfo info = methodInfo.getAnnotationInfo(AuditLog.class.getCanonicalName());
                            MethodParameterInfo[] params = methodInfo.getParameterInfo();
                            if (info != null) {
                                List<ImmutablePair<Integer, MethodParameterInfo>> list = new ArrayList<>();
                                String[] fields = (String[])info.getParameterValues().getValue("fields");
                                if (fields.length > 0 && !"*".equals(fields[0])) {
                                    list = new ArrayList<>();
                                    for (int i = 0; i < fields.length; i++) {
                                        if (Integer.parseInt(fields[i]) < fields.length) {
                                            list.add(new ImmutablePair<>(Integer.parseInt(fields[i]), params[Integer.parseInt(fields[i]) - 1]));
                                        }
                                    }
                                } else {
                                    addParamterInfos(list, methodInfo.getParameterInfo());
                                }
                                mMap.put(methodInfo.getName(), new ImmutablePair(methodInfo, list));
                            }
                        });
                        return;
                    }
                });
                if (!mMap.isEmpty()) {
                    probeMap.put(classInfo.getName(), mMap);
                }
            }
        });
        //using aspect with specify method and paramter
        if (config.getScanPackages() != null && !config.getScanPackages().isEmpty()) {
            List<ClassRegexParam> classRegexParams = new ArrayList<>();

            config.getScanPackages().stream().forEach(f -> classRegexParams.add(getRegexClassMethod(f)));
            config.getScanPackages().stream().forEach(f -> {
                List<ClassInfo> classInfos1 = classGraphReflector.getClassInPackage(f.getPackageName());

                if (classInfos1 != null) {
                    classInfos1.forEach(classInfo -> {
                        Map<String, ImmutablePair<MethodInfo, List<ImmutablePair<Integer, MethodParameterInfo>>>> mMap = new HashMap<>();
                        if (!classRegexParams.isEmpty()) {
                            classRegexParams.forEach(regex -> {
                                if (regex.getPattern() != null) {
                                    if (regex.getPattern().matcher(classInfo.getSimpleName()).find()) {
                                        filterMethodWithRegex(classInfo, regex, mMap);
                                    }
                                }
                            });
                        } else {
                            List<ImmutablePair<Integer, MethodParameterInfo>> list = new ArrayList<>();
                            MethodInfoList methodInfos = classInfo.getMethodInfo();
                            methodInfos.forEach(methodInfo -> {
                                MethodParameterInfo[] parameterInfos = methodInfo.getParameterInfo();
                                for (int i = 0; i < parameterInfos.length; i++) {
                                    list.add(new ImmutablePair(i + 1, parameterInfos[i]));
                                }
                                mMap.put(methodInfo.getName(),new ImmutablePair(methodInfo,list));
                            });

                        }
                        if (!mMap.isEmpty()) {
                            probeMap.put(classInfo.getName(), mMap);
                        }
                    });
                }
            });

        }
    }

    private void addParamterInfos(List<ImmutablePair<Integer, MethodParameterInfo>> list, MethodParameterInfo[] params) {
        for (int i = 0; i < params.length; i++) {
            list.add(new ImmutablePair(i + 1, params[i]));
        }
    }

    private void filterMethodWithRegex(ClassInfo classInfo, ClassRegexParam classRegexParam,Map<String, ImmutablePair<MethodInfo, List<ImmutablePair<Integer, MethodParameterInfo>>>> mMap) {
        MethodInfoList methodInfos = classInfo.getMethodInfo();

        methodInfos.forEach(methodInfo -> {
            List<ImmutablePair<Integer, MethodParameterInfo>> list=new ArrayList<>();
            if (classRegexParam.getMethodPatterns() != null && !classRegexParam.getMethodPatterns().isEmpty()) {
                classRegexParam.getMethodPatterns().forEach(regex -> {
                    if (regex.getPattern().matcher(methodInfo.getName()).find()) {
                        MethodParameterInfo[] parameterInfos = methodInfo.getParameterInfo();
                        List<Integer> columnPos = regex.columPos;
                        Integer maxColumnsize = parameterInfos.length;
                        if (columnPos.size() > 0) {
                            maxColumnsize = Collections.max(columnPos);
                        }
                        Collections.sort(columnPos);
                        if (parameterInfos.length <= maxColumnsize) {
                            if (columnPos.size() > 0) {
                                for (int i = 0; i < columnPos.size(); i++) {
                                    list.add(new ImmutablePair(columnPos.get(i), parameterInfos[columnPos.get(i) - 1]));
                                }
                            } else {
                                for (int i = 0; i < parameterInfos.length; i++) {
                                    list.add(new ImmutablePair(i + 1, parameterInfos[i]));
                                }
                            }
                        }
                    }
                });
            } else {
                MethodParameterInfo[] parameterInfos = methodInfo.getParameterInfo();
                for (int i = 0; i < parameterInfos.length; i++) {
                    list.add(new ImmutablePair(i + 1, parameterInfos[i]));
                }
            }
            if(!list.isEmpty()){
                mMap.put(methodInfo.getName(),new ImmutablePair(methodInfo,list));
            }
        });

    }

    private ClassRegexParam getRegexClassMethod(AuditConfig.ScanPackage scanPackage) {
        List<String> classes = Arrays.asList(scanPackage.getMonitorMethods().split("\\|"));
        ClassRegexParam classRegexParam = new ClassRegexParam();
        if (!"*".equals(scanPackage.getMonitorClasses())) {
            classRegexParam.setPattern(Pattern.compile(scanPackage.getMonitorClasses()));
        }
        for (String f : classes) {
            classRegexParam.getMethodPatterns().add(addRegexMethod(paresMonitorClass(scanPackage.getMonitorClasses(), f)));
        }
        return classRegexParam;
    }

    private AuditConfig.MonitorClass paresMonitorClass(String className, String methodConfig) {
        String[] arr = methodConfig.split(" ");
        return new AuditConfig.MonitorClass(className, arr);
    }

    private ParamRegexParam addRegexMethod(AuditConfig.MonitorClass f) {
        ParamRegexParam param = new ParamRegexParam();
        List<String> fregex = f.getMonitorMethod();
        if (fregex != null && !fregex.isEmpty()) {
            if (!"*".equals(fregex.get(0))) {
                if (!"*".equals(fregex.get(1))) {
                    String[] posArr = fregex.get(1).split(",");
                    for (int i = 0; i < posArr.length; i++) {
                        param.getColumPos().add(Integer.valueOf(posArr[i]));
                    }
                }
                param.setPattern(Pattern.compile(fregex.get(0)));
            } else {
                if (!"*".equals(fregex.get(1))) {
                    String[] posArr = fregex.get(1).split(",");
                    for (int i = 0; i < posArr.length; i++) {
                        param.getColumPos().add(Integer.valueOf(posArr[i]));
                    }
                }
            }
        }
        return param;
    }

    @Data
    public static class ClassRegexParam {
        private Pattern pattern;
        private List<ParamRegexParam> methodPatterns = new ArrayList<>();
    }

    @Data
    public static class ParamRegexParam {
        private Pattern pattern;
        private List<Integer> columPos = new ArrayList<>();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        String clazzName = className.replaceAll("/", ".");

        try {
            classPool.insertClassPath(new ByteArrayClassPath(className, classfileBuffer));
            //need to enhance
            if (probeMap.containsKey(clazzName)) {
                Map<String, ImmutablePair<MethodInfo, List<ImmutablePair<Integer, MethodParameterInfo>>>> map = probeMap.get(clazzName);
                CtClass ctClass = classPool.get(clazzName);
                if (map != null && !map.isEmpty()) {
                    Iterator<Map.Entry<String, ImmutablePair<MethodInfo, List<ImmutablePair<Integer, MethodParameterInfo>>>>> iter = map.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<String, ImmutablePair<MethodInfo, List<ImmutablePair<Integer, MethodParameterInfo>>>> entry = iter.next();
                        CtMethod method = ctClass.getDeclaredMethod(entry.getValue().left.getName());
                        if (method != null) {
                            enhace(ctClass, method, entry.getValue().right);
                        }
                    }
                }
                return ctClass.toBytecode();
            } else {
                return classfileBuffer;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return classfileBuffer;
    }

    private void enhace(CtClass ctClass, CtMethod method, List<ImmutablePair<Integer, MethodParameterInfo>> params) throws Exception {
        String mName = method.getName();
        String replaceName = method.getName() + "$impl";
        method.setName(replaceName);
        CtMethod newmethod = CtNewMethod.copy(method, mName, ctClass, null);
        String type = method.getReturnType().getName();
        StringBuilder builder = new StringBuilder();

        builder.append("{\n    brave.Tracing tracing=null;\n   brave.Span parentSpan=null;\n   brave.Span childSpan=null;\n    " +
                "StringBuilder sb=new StringBuilder();\ntry{\n");
        builder.append("      tracing=com.robin.core.base.spring.SpringContextHolder.getBean(brave.Tracing.class);\n");
        builder.append("        long startTs=System.currentTimeMillis();\n");
        if (params != null && !params.isEmpty()) {
            for (ImmutablePair<Integer, MethodParameterInfo> param : params) {
                String columName=param.right.getName()!=null?param.right.getName():param.left.toString();
                builder.append("     sb.append(\"").append(columName).append(":\").append($").append(param.left).append(").append(\",\");\n");
            }
        }
        builder.append("    if(tracing!=null){\n");
        builder.append("     parentSpan=tracing.tracer().currentSpan();\n");
        builder.append("     tracing.tracer().startScopedSpanWithParent(\""+ctClass.getSimpleName()+"."+mName+"\", parentSpan.context());\n");
        builder.append("     childSpan=tracing.tracer().currentSpan();\n");
        builder.append("     childSpan.tag(\"param\", sb.toString());\n  childSpan.start();\n}\n");
        if (!"void".equals(type)) {
            builder.append(type + "  result = ");
        }
        builder.append(replaceName + "($$);\n");
        builder.append("        if(childSpan!=null){\n       long lastTs =Math.subtractExact(System.currentTimeMillis(),startTs);\n     childSpan.finish(lastTs);\n}\n"); //
        if (!"void".equals(type)) {
            builder.append("        return result;\n");
        }
        builder.append("    }catch(Exception ex){\n");
        builder.append("        if(childSpan!=null){\n");
        builder.append("          childSpan.error(ex);\n        throw ex;");
        builder.append("        }\n     }\n}");
        //System.out.println(builder.toString());
        newmethod.setBody(builder.toString());
        ctClass.addMethod(newmethod);
    }
}
