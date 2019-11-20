package com.robin.probe.javaagent.enhancer;

import com.robin.probe.javaagent.scan.ClassEnhacerRs;
import com.robin.probe.javaagent.scan.MethodParamterEnhacerRs;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Slf4j
public class BraveEnhancer implements IEnhancer {

    @Override
    public void enhace(CtClass ctClass, CtMethod method, String methodName, ClassEnhacerRs classEnhacerRs) throws Exception {
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
        List<MethodParamterEnhacerRs> params= classEnhacerRs.getMethodMap().get(mName).right;
        if (params != null && !params.isEmpty()) {
            for (MethodParamterEnhacerRs param : params) {
                String columName=param.getName()!=null?param.getName():String.valueOf(param.getParamPos());
                builder.append("     sb.append(\"" + columName + ":\").append($" + param.getParamPos() + ").append(\",\");\n");
            }
        }
        builder.append("    if(tracing!=null){\n");
        builder.append("     parentSpan=tracing.tracer().currentSpan();\n");
        String displayName=(StringUtils.isEmpty(methodName))?ctClass.getSimpleName()+"."+mName:methodName;
        builder.append("     tracing.tracer().startScopedSpanWithParent(\""+displayName+"\", parentSpan.context());\n");
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
        log.info("{}",builder.toString());
        newmethod.setBody(builder.toString());
        ctClass.addMethod(newmethod);
    }
}
