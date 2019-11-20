package com.robin.probe.reflect;

import io.github.classgraph.*;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ClassGraphReflector {
    private ScanResult scanResult;
    //default Scan package
    private String scanPackage = "com.robin";


    public ClassGraphReflector(String scanPackage) {
        if (scanPackage != null) {
            this.scanPackage = scanPackage;
        }
        scanResult = new ClassGraph().enableAllInfo().whitelistPackages(scanPackage).scan();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> scanResult.close()));
    }


    public void setScanPackage(String scanPackage) {
        this.scanPackage = scanPackage;
    }

    public Map<String, Method> returnGetMethods(Class clazz) {
        ClassInfo classInfo = scanResult.getClassInfo(clazz.getCanonicalName());
        final Map<String, Method> map = new HashMap<>();
        MethodInfoList mList = classInfo.getMethodInfo().filter(methodInfo -> methodInfo.getName().startsWith("get") && methodInfo.getParameterInfo().length == 0);
        mList.forEach(methodInfo -> {
            String fieldName = StringUtils.uncapitalize(methodInfo.getName().substring(3));
            map.put(fieldName, methodInfo.loadClassAndGetMethod());
        });
        return map;
    }

    public Map<String, Method> returnSetMethods(Class clazz) {
        ClassInfo classInfo = scanResult.getClassInfo(clazz.getCanonicalName());
        final Map<String, Method> map = new HashMap<>();
        MethodInfoList mList = classInfo.getMethodInfo().filter(methodInfo -> methodInfo.getName().startsWith("set") && methodInfo.getParameterInfo().length == 1);
        mList.forEach(methodInfo -> {
            String fieldName = StringUtils.uncapitalize(methodInfo.getName().substring(3));
            map.put(fieldName, methodInfo.loadClassAndGetMethod());
        });
        return map;
    }

    public FieldInfo returnField(Class clazz, String fieldName) {
        return scanResult.getClassInfo(clazz.getCanonicalName()).getFieldInfo(fieldName);
    }

    public ClassInfoList getAnnotationClasses(Class clazz) {
        return scanResult.getClassesWithAnnotation(clazz.getCanonicalName());
    }

    public ClassInfoList getClassesByAnnotationFields(Class fieldClass) {
        return scanResult.getClassesWithFieldAnnotation(fieldClass.getCanonicalName());
    }

    public ClassInfoList getClassesByAnnotationMethod(Class annotationMethod) {
        return scanResult.getClassesWithMethodAnnotation(annotationMethod.getCanonicalName());
    }

    public List<ClassInfo> getClassInPackage(String packageName) {
        List<ClassInfo> list=new ArrayList<>();
        if(scanResult.getPackageInfo(packageName)!=null) {
            getClassInfoRecursive(scanResult.getPackageInfo(packageName), list);
            return list;
        }else{
            return null;
        }
    }
    public ClassInfoList getClassInfosInPackage(String packageName) {
        List<ClassInfo> list=new ArrayList<>();
        if(scanResult.getPackageInfo(packageName)!=null) {
            getClassInfoRecursive(scanResult.getPackageInfo(packageName), list);
            return new ClassInfoList(list);
        }else{
            return null;
        }
    }

    private void getClassInfoRecursive(PackageInfo packageInfo, List<ClassInfo> classInfos) {

        classInfos.addAll(packageInfo.getClassInfo());
        if (packageInfo.getChildren().size() > 0) {
            packageInfo.getChildren().forEach(packageInfo1 -> {
                getClassInfoRecursive(packageInfo1, classInfos);
            });
        }
    }

    public boolean isAnnotationClassWithAnnotationFields(Class clazz, Class annotationClazz, Class annotationFields) {
        return scanResult.getClassInfo(clazz.getCanonicalName()).hasAnnotation(annotationClazz.getCanonicalName())
                && scanResult.getClassInfo(clazz.getCanonicalName()).hasFieldAnnotation(annotationFields.getCanonicalName());
    }

    public ScanResult getScanResult() {
        return scanResult;
    }
}
