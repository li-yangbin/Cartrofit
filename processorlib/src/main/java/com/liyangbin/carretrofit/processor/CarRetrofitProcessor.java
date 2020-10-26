package com.liyangbin.carretrofit.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@SupportedAnnotationTypes({CarRetrofitProcessor.TARGET, CarRetrofitProcessor.TARGET_CAR_API})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class CarRetrofitProcessor extends AbstractProcessor {
    private static final String PACKAGE_NAME = "com.liyangbin.carretrofit";
    static final String TARGET = PACKAGE_NAME + ".annotation.ProcessSuper";
    private static final String IMPLEMENT_CLASS_NAME = "implementClass";
    private static final String CLASS_NAME = "className";
    private static final String SUPER_NAME = "superClass";
    private static final String SUPER_CONSTRUCTOR = "superConstructor";
    private static final ClassName CAR_RETROFIT_NAME = ClassName.get(PACKAGE_NAME, "CarRetrofit");
    private static final ClassName ID_MAPPER_NAME = ClassName.get(PACKAGE_NAME, "IdRouter");
    private static final String COMMENT_COMMON = "This class is generated by CarRetrofitProcessor."
            + " Do not modify this file directly";

    static final String TARGET_CAR_API = PACKAGE_NAME + ".annotation.CarApi";

    private final HashMap<Integer, ClassName> mScopeMap = new HashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Set<? extends Element> elements = roundEnvironment.getRootElements();
        if (elements.size() == 0) {
            return false;
        }
        for (Element element : elements) {
            final ElementKind kind = element.getKind();
            if (kind == ElementKind.CLASS || kind == ElementKind.INTERFACE) {
                List<? extends AnnotationMirror> list = element.getAnnotationMirrors();
                for (int i = 0; i < list.size(); i++) {
                    DeclaredType annotationElement = list.get(i).getAnnotationType();
                    String annotationName = annotationElement.toString();
                    if (kind == ElementKind.CLASS && TARGET.equals(annotationName)) {
                        logI("process super element:" + element);
                        processClass((TypeElement) element);
                        break;
                    } else if (kind == ElementKind.INTERFACE && TARGET_CAR_API.equals(annotationName)) {
                        logI("process Car Api element:" + element);
                        processCarApiClass((TypeElement) element);
                        break;
                    }
                }
            }
        }
        processIdMapperClass();
        return true;
    }

    private void processIdMapperClass() {
        if (mScopeMap.size() == 0) {
            return;
        }
        TypeSpec.Builder routerBuilder = TypeSpec.classBuilder(ID_MAPPER_NAME)
                .addModifiers(PUBLIC, FINAL);;
        routerBuilder.addField(FieldSpec
                .builder(TypeName.INT, "SCOPE_MASK")
                .addModifiers(PRIVATE, STATIC, FINAL)
                .initializer("0x" + Integer.toHexString(SCOPE_MASK))
                .build());

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("findApiClassById")
                .addModifiers(STATIC, PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class),
                        WildcardTypeName.subtypeOf(Object.class)))
                .addParameter(TypeName.INT, "id");
        methodBuilder.beginControlFlow("switch(id & SCOPE_MASK)");
        for (Map.Entry<Integer, ClassName> entry : mScopeMap.entrySet()) {
            methodBuilder
                    .addCode("case $L: ", entry.getKey())
                    .addStatement("return $T.class", entry.getValue());
        }
        methodBuilder.endControlFlow();
        methodBuilder.addStatement("return null");

        routerBuilder.addMethod(methodBuilder.build());

        try (Writer writer = processingEnv.getFiler()
                .createSourceFile(ID_MAPPER_NAME.reflectionName())
                .openWriter()) {
            JavaFile.builder(PACKAGE_NAME, routerBuilder.build())
                    .addFileComment(COMMENT_COMMON)
                    .skipJavaLangImports(true)
                    .indent("    ")
                    .build()
                    .writeTo(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mScopeMap.clear();
    }

    private void processCarApiClass(TypeElement element) {
        PackageElement packageElement = (PackageElement) element.getEnclosingElement();
        String packageName = packageElement.getQualifiedName().toString();
        String apiClassName = element.getSimpleName().toString();
        ClassName idClassName = ClassName.get(packageName, apiClassName + "Id");
        TypeSpec.Builder indexClassBuilder = TypeSpec.classBuilder(idClassName)
                .addModifiers(PUBLIC, FINAL);

        ArrayList<ExecutableElement> apiInterface = new ArrayList<>();
        resolveExecutableElement(element, apiInterface);

        if (apiInterface.size() == 0) {
            logW("Empty CarApi interface found:" + apiClassName);
            return;
        }

        final int baseScopeId = getScopeBaseId(element.getQualifiedName().toString());
        if (mScopeMap.put(baseScopeId, ClassName.get(element)) != null) {
            logE("conflict scopeId: 0x" + Integer.toHexString(baseScopeId)
                    + " from:" + apiClassName);
            return;
        }

        HashMap<String, String> apiIndexMap = new HashMap<>();
        for (int i = 0; i < apiInterface.size(); i++) {
            ExecutableElement childElement = apiInterface.get(i);
            String name = childElement.getSimpleName().toString();
            int index = baseScopeId + i;
            apiIndexMap.put(name, idClassName.simpleName() + "." + name);
            indexClassBuilder.addField(FieldSpec
                    .builder(TypeName.INT, name, STATIC, FINAL, PUBLIC)
                    .addJavadoc("{@link $T#" + name + "}", ClassName.get(element))
                    .initializer("" + index)
                    .build());
        }

        MethodSpec.Builder importBuilder = MethodSpec.methodBuilder("init");
        importBuilder.addModifiers(STATIC, PUBLIC);
        importBuilder.addParameter(
                ParameterizedTypeName.get(
                        ClassName.get(HashMap.class),
                        ClassName.get(Integer.class),
                        ClassName.get(Method.class)),
                "apiMap");
        importBuilder.addStatement("final $T[] methods = $T.class.getDeclaredMethods()",
                ClassName.get(Method.class), ClassName.get(element))
                .beginControlFlow("for (Method method : methods)")
                .addStatement("String methodName = method.getName()");
        boolean firstTime = true;
        for (Map.Entry<String, String> entry : apiIndexMap.entrySet()) {
            String judgement = "if (methodName.equals($S))";
            String name = entry.getKey();
            if (firstTime) {
                importBuilder.beginControlFlow(judgement, name);
            } else {
                importBuilder.nextControlFlow("else " + judgement, name);
            }
            firstTime = false;
            importBuilder.addStatement("apiMap.put($L, method)", entry.getValue());
        }
        if (!firstTime) {
            importBuilder.endControlFlow();
        }
        importBuilder.endControlFlow();

        indexClassBuilder.addMethod(importBuilder.build());

        try (Writer writer = processingEnv.getFiler()
                .createSourceFile(idClassName.reflectionName())
                .openWriter()) {
            JavaFile.builder(PACKAGE_NAME, indexClassBuilder.build())
                    .addFileComment(COMMENT_COMMON)
                    .skipJavaLangImports(true)
                    .indent("    ")
                    .build()
                    .writeTo(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final int SCOPE_SHIFT_BIT = 9;
    private static final int SCOPE_MASK = 0xFFFFFFFF << SCOPE_SHIFT_BIT;

    private static int getScopeBaseId(String input) {
        return input.hashCode() << SCOPE_SHIFT_BIT;
    }

    private void logI(String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg);
    }

    private void logW(String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg);
    }

    private void logE(String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
    }

    private void resolveExecutableElement(TypeElement element, ArrayList<ExecutableElement> elements) {
        List<? extends Element> enclosedElements = element.getEnclosedElements();
        for (int i = 0; i < enclosedElements.size(); i++) {
            Element elementMember = enclosedElements.get(i);
            ElementKind kind = elementMember.getKind();
            if (kind == ElementKind.METHOD) {
                elements.add((ExecutableElement) elementMember);
            }
        }
    }

    private void resolveVariableElement(TypeElement element, ArrayList<VariableElement> elements) {
        List<? extends Element> enclosedElements = element.getEnclosedElements();
        for (int i = 0; i < enclosedElements.size(); i++) {
            Element elementMember = enclosedElements.get(i);
            ElementKind kind = elementMember.getKind();
            if (kind == ElementKind.FIELD) {
                elements.add((VariableElement) elementMember);
            }
        }
    }

    private void processClass(TypeElement element) {
        // parse ProcessSuper annotation
        List<? extends AnnotationMirror> list = element.getAnnotationMirrors();
        String userExplicitName = null;
        ClassName userExplicitSuperClass = null;
        ArrayList<ClassName> constructorParameterList = null;
        ArrayList<TypeElement> targetInterfaceList = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            AnnotationMirror annotation = list.get(i);
            TypeElement annotatedElement = (TypeElement) annotation.getAnnotationType().asElement();
            if (TARGET.equals(annotatedElement.getQualifiedName().toString())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                        : annotation.getElementValues().entrySet()) {
                    ExecutableElement method = entry.getKey();
                    AnnotationValue value = entry.getValue();
                    String methodName = method.getSimpleName().toString();
                    if (IMPLEMENT_CLASS_NAME.equals(methodName)) {
                        List<AnnotationValue> valueList = (List<AnnotationValue>) value.getValue();
                        for (int j = 0; j < valueList.size(); j++) {
                            DeclaredType type = (DeclaredType) valueList.get(j).getValue();
                            targetInterfaceList.add((TypeElement) type.asElement());
                        }
                    } else if (CLASS_NAME.equals(methodName)) {
                        userExplicitName = (String) value.getValue();
                    } else if (SUPER_NAME.equals(methodName)) {
                        DeclaredType type = (DeclaredType) value.getValue();
                        TypeElement typeElement = (TypeElement) type.asElement();
                        if (!Object.class.getName().equals(typeElement.getQualifiedName().toString())) {
                            userExplicitSuperClass = ClassName.get(typeElement);
                        }
                    } else if (SUPER_CONSTRUCTOR.equals(methodName)) {
                        List<AnnotationValue> valueList = (List<AnnotationValue>) value.getValue();
                        for (int j = 0; j < valueList.size(); j++) {
                            DeclaredType type = (DeclaredType) valueList.get(j).getValue();
                            if (constructorParameterList == null) {
                                constructorParameterList = new ArrayList<>();
                            }
                            constructorParameterList.add(ClassName.get((TypeElement) type.asElement()));
                        }
                    }
                }
            }
        }
        if (targetInterfaceList.size() == 0) {
            logE("Annotation must supply a valid interface name");
            return;
        }

        // resolve class name(including super class name), and build impl class
        PackageElement packageElement = (PackageElement) element.getEnclosingElement();
        String packageName = packageElement.getQualifiedName().toString();
        ClassName implClassName = ClassName.get(packageName, userExplicitName != null
                ? userExplicitName : (targetInterfaceList.get(0).getSimpleName().toString() + "Impl"));
        TypeSpec.Builder implClassBuilder = TypeSpec.classBuilder(implClassName)
                .addModifiers(PUBLIC, ABSTRACT);
        ArrayList<TypeName> targetInterfaceType = new ArrayList<>();
        for (int i = 0; i < targetInterfaceList.size(); i++) {
            targetInterfaceType.add(ClassName.get(targetInterfaceList.get(i)));
        }
        implClassBuilder.addSuperinterfaces(targetInterfaceType);
        logI("generate class packageName:" + packageName + " className:" + implClassName);

        // declare constructor if user specified
        if (userExplicitSuperClass != null) {
            implClassBuilder.superclass(userExplicitSuperClass);
            if (constructorParameterList != null && constructorParameterList.size() > 0) {
                MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder();
                StringBuilder superCall = new StringBuilder("super(");
                final int parameterCount = constructorParameterList.size();
                for (int i = 0; i < parameterCount; i++) {
                    ClassName parameterType = constructorParameterList.get(i);
                    String variableName = parameterType.simpleName().toLowerCase();
                    constructorBuilder.addParameter(parameterType, variableName);
                    superCall.append(variableName);
                    if (i < parameterCount - 1) {
                        superCall.append(", ");
                    }
                }
                superCall.append(")");
                constructorBuilder.addStatement(superCall.toString());
                implClassBuilder.addMethod(constructorBuilder.addModifiers(PUBLIC).build());
            }
        }

        int interfaceCount = targetInterfaceType.size();
        for (int i = 0; i < interfaceCount; i++) {
            // declare base field
            TypeName name = targetInterfaceType.get(i);
            String fieldName = interfaceCount == 1 ? "mBase" : ("mBase" + i);
            implClassBuilder.addField(name, fieldName, Modifier.PRIVATE);

            // declare getBase() method
            String methodName = interfaceCount == 1 ? "getBase" : ("getBase" + i);
            implClassBuilder.addMethod(MethodSpec.methodBuilder(methodName)
                    .returns(name)
                    .addModifiers(PRIVATE, Modifier.FINAL)
                    .beginControlFlow("if ($L == null)", fieldName)
                    .addStatement(fieldName + " = $T.fromDefault($T.class)",
                            CAR_RETROFIT_NAME, name)
                    .endControlFlow()
                    .addStatement("return $L", fieldName)
                    .build());
        }

        // resolve method which need to be implemented
        for (int i = 0; i < targetInterfaceList.size(); i++) {
            TypeElement targetInterface = targetInterfaceList.get(i);
            ArrayList<ExecutableElement> executableElements = new ArrayList<>();
            resolveExecutableElement(targetInterface, executableElements);
            List<? extends TypeMirror> interfaceList = targetInterface.getInterfaces();
            for (int j = 0; j < interfaceList.size(); j++) {
                DeclaredType mirror = (DeclaredType) interfaceList.get(j);
                resolveExecutableElement((TypeElement) mirror.asElement(), executableElements);
            }

            // declare these methods
            for (int j = 0; j < executableElements.size(); j++) {
                ExecutableElement executable = executableElements.get(j);
                MethodSpec.Builder methodBuilder = MethodSpec.overriding(executable);
                String methodName = interfaceCount == 1 ? "getBase" : ("getBase" + i);
                StringBuilder buffer = new StringBuilder(methodName + "()."
                        + executable.getSimpleName().toString() + "(");
                final int parameterCount = executable.getParameters().size();
                for (int k = 0; k < parameterCount; k++) {
                    VariableElement variableElement = executable.getParameters().get(k);
                    buffer.append(variableElement.getSimpleName());
                    if (k < parameterCount - 1) {
                        buffer.append(", ");
                    }
                }
                buffer.append(")");
                if (executable.getReturnType().getKind() == TypeKind.VOID) {
                    methodBuilder.addStatement(buffer.toString());
                } else {
                    methodBuilder.addStatement("return " + buffer.toString());
                }
                implClassBuilder.addMethod(methodBuilder.build());
            }
        }

        // done, write to target path
        try (Writer writer = processingEnv.getFiler()
                .createSourceFile(implClassName.reflectionName())
                .openWriter()) {
            JavaFile.builder(packageName, implClassBuilder.build())
                    .addFileComment(COMMENT_COMMON)
                    .skipJavaLangImports(true)
                    .indent("    ")
                    .build()
                    .writeTo(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}