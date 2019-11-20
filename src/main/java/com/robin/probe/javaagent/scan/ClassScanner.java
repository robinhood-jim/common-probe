package com.robin.probe.javaagent.scan;

import com.robin.core.base.util.StringUtils;
import com.robin.probe.reflect.ClassGraphReflector;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;


/**
 * scan fit condition ClassInfo
 */
public class ClassScanner {
    /**
     *
     * @param scanResult ClassGraph Result
     * @param config Class match config
     * @return match Class infos
     */
    public static final ClassInfoList scanPackage(ClassGraphReflector reflector, ScanResult scanResult, ClassMatchConfig config){
        ClassInfoList infos=null;
        if(!StringUtils.isEmpty(config.getScanPackage())){
            infos=reflector.getClassInfosInPackage(config.getScanPackage());
        }
        if(config.getClassNamePattern()!=null){
            if(infos!=null){
                infos=infos.filter(classInfo -> config.getClassNamePattern().matcher(classInfo.getSimpleName()).find());
            }else{
                infos=scanResult.getAllClasses().filter(classInfo -> config.getClassNamePattern().matcher(classInfo.getSimpleName()).find());
            }
        }
        if(config.getImplementClass()!=null){
            if(infos!=null && infos.getImplementedInterfaces().get(config.getImplementClass().getCanonicalName())!=null){
                infos=infos.filter(classInfo -> classInfo.implementsInterface(config.getImplementClass().getCanonicalName()));
            }else{
                infos=scanResult.getClassesImplementing(config.getImplementClass().getCanonicalName());
            }
        }
        if(config.getSubClass()!=null){
            if(infos!=null){
                infos=infos.filter(classInfo -> classInfo.extendsSuperclass(config.getSubClass().getCanonicalName()));
            }else{
                infos=scanResult.getSubclasses(config.getSubClass().getCanonicalName());
            }
        }
        if(config.getAnnotationClass()!=null){
            if(infos!=null){
                infos=infos.filter(classInfo -> classInfo.isAnnotation() && classInfo.getAnnotationInfo(config.getAnnotationClass().getCanonicalName())!=null);
            }else{
                infos=scanResult.getClassesWithAnnotation(config.getAnnotationClass().getCanonicalName());
            }
        }
        if(config.getAnnotationMethod()!=null){
            if(infos!=null){
                infos=infos.filter(classInfo -> classInfo.hasDeclaredMethodAnnotation(config.getAnnotationMethod().getCanonicalName()));
            }else{
                infos=scanResult.getClassesWithMethodAnnotation(config.getAnnotationMethod().getCanonicalName());
            }
        }
        return infos;
    }
}
