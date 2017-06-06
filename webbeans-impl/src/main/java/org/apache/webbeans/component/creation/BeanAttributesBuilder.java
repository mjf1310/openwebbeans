/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.component.creation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.decorator.Decorator;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.NormalScope;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Specializes;
import javax.enterprise.inject.Typed;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.util.Nonbinding;
import javax.inject.Named;
import javax.inject.Scope;
import javax.interceptor.Interceptor;

import org.apache.webbeans.annotation.AnnotationManager;
import org.apache.webbeans.annotation.AnyLiteral;
import org.apache.webbeans.annotation.DefaultLiteral;
import org.apache.webbeans.annotation.NamedLiteral;
import org.apache.webbeans.component.BeanAttributesImpl;
import org.apache.webbeans.config.OWBLogConst;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.ExternalScope;
import org.apache.webbeans.exception.WebBeansConfigurationException;

import org.apache.webbeans.logger.WebBeansLoggerFacade;
import org.apache.webbeans.portable.AbstractAnnotated;
import org.apache.webbeans.util.AnnotationUtil;
import org.apache.webbeans.util.Asserts;
import org.apache.webbeans.util.ClassUtil;
import org.apache.webbeans.util.WebBeansUtil;

/**
 * Abstract implementation.
 * 
 * @version $Rev$ $Date$
 *
 * @param <T> bean class info
 */
public abstract class BeanAttributesBuilder<T, A extends Annotated>
{
    protected A annotated;

    protected WebBeansContext webBeansContext;

    protected Set<Type> types = new HashSet<Type>();

    protected Set<Annotation> qualifiers = new HashSet<Annotation>();

    protected Class<? extends Annotation> scope;

    protected String name;

    protected boolean nullable;

    protected Set<Class<? extends Annotation>> stereotypes;

    protected boolean alternative;
    
    public static BeanAttributesBuilderFactory forContext(WebBeansContext webBeansContext)
    {
        return new BeanAttributesBuilderFactory(webBeansContext);
    }

    /**
     * Creates a bean instance.
     * 
     * @param annotated
     */
    protected BeanAttributesBuilder(WebBeansContext webBeansContext, A annotated)
    {
        this.annotated = annotated;
        this.webBeansContext = webBeansContext;
    }

    public BeanAttributesImpl<T> build()
    {
        // we need to check the stereotypes first because we might need it to determine the scope
        stereotypes = defineStereotypes(annotated);

        defineScope();
        if (scope == null)
        {
            // this indicates that we shall not use this AnnotatedType to create Beans from it.
            return null;
        }

        defineTypes();
        defineName();
        defineQualifiers();
        defineNullable();
        defineAlternative();
        return new BeanAttributesImpl<T>(types, qualifiers, scope, name, nullable, stereotypes, alternative);
    }

    protected A getAnnotated()
    {
        return annotated;
    }

    /**
     * {@inheritDoc}
     */
    protected void defineTypes()
    {
        Class<?> baseType = ClassUtil.getClass(annotated.getBaseType());
        if (baseType.isArray())
        {
            // 3.3.1
            types.add(Object.class);
            types.add(baseType);
        }
        else
        {
            Typed beanTypes = annotated.getAnnotation(Typed.class);
            if (beanTypes != null)
            {
                Class<?>[] typedTypes = beanTypes.value();

                //New api types
                Set<Type> newTypes = new HashSet<Type>();
                for (Class<?> type : typedTypes)
                {
                    Type foundType = null;

                    for (Type apiType : annotated.getTypeClosure())
                    {
                        if(ClassUtil.getClazz(apiType) == type)
                        {
                            foundType = apiType;
                            break;
                        }
                    }

                    if(foundType == null)
                    {
                        throw new WebBeansConfigurationException("@Type values must be in bean api types of class: " + baseType);
                    }

                    newTypes.add(foundType);
                }

                this.types.addAll(newTypes);
                this.types.add(Object.class);
            }
            else
            {
                this.types.addAll(annotated.getTypeClosure());
            }
            Set<String> ignored = webBeansContext.getOpenWebBeansConfiguration().getIgnoredInterfaces();
            if (!ignored.isEmpty())
            {
                for (Iterator<Type> i = this.types.iterator(); i.hasNext(); )
                {
                    Type t = i.next();
                    if (t instanceof Class && ignored.contains(((Class<?>) t).getName()))
                    {
                        i.remove();
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void defineQualifiers()
    {
        HashSet<Class<? extends Annotation>> qualifiedTypes = new HashSet<Class<? extends Annotation>>();
        if (annotated.isAnnotationPresent(Specializes.class))
        {
            defineQualifiers(getSuperAnnotated(), qualifiedTypes);
        }
        defineQualifiers(annotated, qualifiedTypes);
    }

    private void defineQualifiers(Annotated annotated, Set<Class<? extends Annotation>> qualifiedTypes)
    {
        Annotation[] annotations = AnnotationUtil.asArray(annotated.getAnnotations());
        final AnnotationManager annotationManager = webBeansContext.getAnnotationManager();

        for (Annotation annotation : annotations)
        {
            Class<? extends Annotation> type = annotation.annotationType();

            if (annotationManager.isQualifierAnnotation(type))
            {
                Method[] methods = webBeansContext.getSecurityService().doPrivilegedGetDeclaredMethods(type);

                for (Method method : methods)
                {
                    Class<?> clazz = method.getReturnType();
                    if (clazz.isArray() || clazz.isAnnotation())
                    {
                        if (!AnnotationUtil.hasAnnotation(method.getDeclaredAnnotations(), Nonbinding.class))
                        {
                            throw new WebBeansConfigurationException("WebBeans definition class : " + method.getDeclaringClass().getName() + " @Qualifier : "
                                                                     + annotation.annotationType().getName()
                                                                     + " must have @NonBinding valued members for its array-valued and annotation valued members");
                        }
                    }
                }

                if (qualifiedTypes.contains(annotation.annotationType()) && !isRepetable(annotated, annotation))
                {
                    continue;
                }
                else
                {
                    qualifiedTypes.add(annotation.annotationType());
                }
                if (annotation.annotationType().equals(Named.class) && name != null)
                {
                    qualifiers.add(new NamedLiteral(name));
                }
                else
                {
                    qualifiers.add(annotation);
                }
            }
        }
        
        // No-binding annotation
        if (qualifiers.size() == 0 )
        {
            qualifiers.add(DefaultLiteral.INSTANCE);
        }
        else if(qualifiers.size() == 1)
        {
            // section 2.3.1
            // If a bean does not explicitly declare a qualifier other than @Named or @Any,
            // the bean has exactly one additional qualifier, of type @Default.
            Annotation annot = qualifiers.iterator().next();
            if(annot.annotationType().equals(Named.class) || annot.annotationType().equals(Any.class))
            {
                qualifiers.add(DefaultLiteral.INSTANCE);
            }
        }
        else if (qualifiers.size() == 2)
        {
            Iterator<Annotation> qualiIt = qualifiers.iterator();
            Class<? extends Annotation> q1 = qualiIt.next().annotationType();
            Class<? extends Annotation> q2 = qualiIt.next().annotationType();
            if (q1.equals(Named.class) && q2.equals(Any.class) ||
                q2.equals(Named.class) && q1.equals(Any.class) )
            {
                qualifiers.add(DefaultLiteral.INSTANCE);
            }
        }

        //Add @Any support
        if(!hasAnyQualifier())
        {
            qualifiers.add(AnyLiteral.INSTANCE);
        }
        
    }

    // we don't want to do the getRepeatableMethod() logic *again* if we can but we can need for custom AT
    private boolean isRepetable(final Annotated annotated, final Annotation annotation)
    {
        return AbstractAnnotated.class.isInstance(annotated) ?
                AbstractAnnotated.class.cast(annotated).getRepeatables().contains(annotation.annotationType()) :
                webBeansContext.getAnnotationManager().getRepeatableMethod(annotation.annotationType()) != null;
    }

    /**
     * Returns true if any binding exist
     * 
     * @return true if any binding exist
     */
    private boolean hasAnyQualifier()
    {
        return AnnotationUtil.getAnnotation(qualifiers, Any.class) != null;
    }


    protected abstract void defineScope();

    protected void defineScope(String errorMessage)
    {
        defineScope(null, false, errorMessage);
    }

    protected void defineScope(Class<?> declaringClass, boolean onlyScopedBeans, String errorMessage)
    {
        Annotation[] annotations = AnnotationUtil.asArray(annotated.getAnnotations());
        boolean found = false;

        List<ExternalScope> additionalScopes = webBeansContext.getBeanManagerImpl().getAdditionalScopes();
        
        for (Annotation annotation : annotations)
        {   
            if (declaringClass != null && AnnotationUtil.getDeclaringClass(annotation, declaringClass) != null && !AnnotationUtil.isDeclaringClass(declaringClass, annotation))
            {
                continue;
            }

            Class<? extends Annotation> annotationType = annotation.annotationType();

            if (!webBeansContext.getBeanManagerImpl().isScope(annotationType))
            {
                continue;
            }

            /*Normal scope*/
            Annotation var = annotationType.getAnnotation(NormalScope.class);
            /*Pseudo scope*/
            Annotation pseudo = annotationType.getAnnotation(Scope.class);
        
            if (var == null && pseudo == null)
            {
                // check for additional scopes registered via a CDI Extension
                for (ExternalScope additionalScope : additionalScopes)
                {
                    if (annotationType.equals(additionalScope.getScope()))
                    {
                        // create a proxy which implements the given annotation
                        Annotation scopeAnnotation = additionalScope.getScopeAnnotation();
    
                        if (additionalScope.isNormal())
                        {
                            var = scopeAnnotation;
                        }
                        else
                        {
                            pseudo = scopeAnnotation;
                        }
                    }
                }
            }
            
            if (var != null)
            {
                if(pseudo != null)
                {
                    throw new WebBeansConfigurationException("Not to define both @Scope and @NormalScope on bean : " + ClassUtil.getClass(annotated.getBaseType()).getName());
                }
                
                if (found)
                {
                    throw new WebBeansConfigurationException(errorMessage);
                }

                found = true;
                scope = annotation.annotationType();
            }
            else
            {
                if(pseudo != null)
                {
                    if (found)
                    {
                        throw new WebBeansConfigurationException(errorMessage);
                    }

                    found = true;
                    scope = annotation.annotationType();
                }
            }
        }

        if (found && annotated.getAnnotation(Interceptor.class) != null && scope != Dependent.class)
        {
            throw new WebBeansConfigurationException("An Interceptor must declare any other Scope than @Dependent: " + ClassUtil.getClass(annotated.getBaseType()).getName());
        }

        if (found && annotated.getAnnotation(Decorator.class) != null && scope != Dependent.class)
        {
            throw new WebBeansConfigurationException("A Decorator must declare any other Scope than @Dependent: " + ClassUtil.getClass(annotated.getBaseType()).getName());
        }


        if (!found && declaringClass != null && !hasDeclaredNonInheritedScope(declaringClass))
        {
            defineScope(declaringClass.getSuperclass(), onlyScopedBeans, errorMessage);
        }
        else if (!found)
        {
            defineDefaultScope(errorMessage, onlyScopedBeans);
        }
    }

    private void defineDefaultScope(String exceptionMessage, boolean onlyScopedBeans)
    {
        if (scope == null)
        {
            Set<Class<? extends Annotation>> stereos = stereotypes;
            if (stereos != null && stereos.size() >  0)
            {
                Annotation defined = null;
                Set<Class<? extends Annotation>> anns = stereotypes;
                for (Class<? extends Annotation> stero : anns)
                {
                    boolean containsNormal = AnnotationUtil.hasMetaAnnotation(stero.getDeclaredAnnotations(), NormalScope.class);
                    
                    if (AnnotationUtil.hasMetaAnnotation(stero.getDeclaredAnnotations(), NormalScope.class) ||
                            AnnotationUtil.hasMetaAnnotation(stero.getDeclaredAnnotations(), Scope.class))
                    {                        
                        Annotation next;
                        
                        if(containsNormal)
                        {
                            next = AnnotationUtil.getMetaAnnotations(stero.getDeclaredAnnotations(), NormalScope.class)[0];
                        }
                        else
                        {
                            next = AnnotationUtil.getMetaAnnotations(stero.getDeclaredAnnotations(), Scope.class)[0];
                        }

                        if (defined == null)
                        {
                            defined = next;
                        }
                        else
                        {
                            if (!defined.equals(next))
                            {
                                throw new WebBeansConfigurationException(exceptionMessage);
                            }
                        }
                    }
                }

                if (defined != null)
                {
                    scope = defined.annotationType();
                }
                else
                {
                    scope = Dependent.class;
                }
            }
            if (scope == null &&
                    (!onlyScopedBeans ||
                    annotated.getAnnotation(Interceptor.class) != null ||
                    annotated.getAnnotation(Decorator.class) != null))
            {
                // only add a 'default' Dependent scope
                // * if it's not in a bean-discovery-mode='scoped' module, or
                // * if it's a Decorator or Interceptor
                scope = Dependent.class;
            }

        }
    }

    private boolean hasDeclaredNonInheritedScope(Class<?> type)
    {
        return webBeansContext.getAnnotationManager().getDeclaredScopeAnnotation(type) != null;
    }

    protected abstract void defineName();

    protected void defineName(Annotated annotated, String name)
    {
        Annotation[] anns = AnnotationUtil.asArray(annotated.getAnnotations());
        Named nameAnnot = null;
        boolean isDefault = false;
        for (Annotation ann : anns)
        {
            if (ann.annotationType().equals(Named.class))
            {
                nameAnnot = (Named) ann;
                break;
            }
        }

        if (nameAnnot == null) // no @Named
        {
            // Check for stereottype
            if (webBeansContext.getAnnotationManager().hasNamedOnStereoTypes(stereotypes))
            {
                isDefault = true;
            }

        }
        else
        // yes @Named
        {
            if (nameAnnot.value().equals(""))
            {
                isDefault = true;
            }
            else
            {
                this.name = nameAnnot.value();
            }

        }

        if (isDefault)
        {
            this.name = name;
        }
    }

    /**
     * @return the AnnotatedType of the next non-Specialized superclass
     */
    protected abstract Annotated getSuperAnnotated();

    protected abstract void defineNullable();
    
    protected void defineNullable(boolean nullable)
    {
        this.nullable = nullable;
    }

    /**
     * {@inheritDoc}
     */
    protected Set<Class<? extends Annotation>> defineStereotypes(Annotated annot)
    {
        Set<Class<? extends Annotation>> stereos  = null;
        Annotation[] anns = AnnotationUtil.asArray(annot.getAnnotations());
        final AnnotationManager annotationManager = webBeansContext.getAnnotationManager();
        if (annotationManager.hasStereoTypeMetaAnnotation(anns))
        {
            Annotation[] steroAnns =
                annotationManager.getStereotypeMetaAnnotations(anns);

            for (Annotation stereo : steroAnns)
            {
                if (stereos == null)
                {
                    stereos = new HashSet<Class<? extends Annotation>>();
                }
                stereos.add(stereo.annotationType());
            }
        }

        return stereos != null ? stereos : Collections.EMPTY_SET;
    }

    // these alternatives can be not activated
    protected void defineAlternative()
    {
        alternative = WebBeansUtil.isAlternative(annotated, stereotypes);
    }


    protected abstract Class<?> getType();
    
    public static class BeanAttributesBuilderFactory
    {
        private WebBeansContext webBeansContext;

        private BeanAttributesBuilderFactory(WebBeansContext webBeansContext)
        {
            Asserts.assertNotNull(webBeansContext, Asserts.PARAM_NAME_WEBBEANSCONTEXT);
            this.webBeansContext = webBeansContext;
        }
        
        public <T> BeanAttributesBuilder<T, AnnotatedType<T>> newBeanAttibutes(AnnotatedType<T> annotatedType)
        {
            return newBeanAttibutes(annotatedType, false);
        }
        
        public <T> BeanAttributesBuilder<T, AnnotatedType<T>> newBeanAttibutes(AnnotatedType<T> annotatedType, boolean onlyScopedBeans)
        {
            return new AnnotatedTypeBeanAttributesBuilder<T>(webBeansContext, annotatedType, onlyScopedBeans);
        }

        public <T> BeanAttributesBuilder<T, AnnotatedField<T>> newBeanAttibutes(AnnotatedField<T> annotatedField)
        {
            return new AnnotatedFieldBeanAttributesBuilder<T>(webBeansContext, annotatedField);
        }
        
        public <T> BeanAttributesBuilder<T, AnnotatedMethod<T>> newBeanAttibutes(AnnotatedMethod<T> annotatedMethod)
        {
            return new AnnotatedMethodBeanAttributesBuilder<T>(webBeansContext, annotatedMethod);
        }
    }

    private static class AnnotatedTypeBeanAttributesBuilder<C> extends BeanAttributesBuilder<C, AnnotatedType<C>>
    {
        private final boolean onlyScopedBeans;

        public AnnotatedTypeBeanAttributesBuilder(WebBeansContext webBeansContext, AnnotatedType<C> annotated, boolean onlyScopedBeans)
        {
            super(webBeansContext, annotated);
            this.onlyScopedBeans = onlyScopedBeans;
        }

        @Override
        protected void defineScope()
        {
            defineScope(getAnnotated().getJavaClass(), onlyScopedBeans,
                    WebBeansLoggerFacade.getTokenString(OWBLogConst.TEXT_MB_IMPL) + getAnnotated().getJavaClass().getName() +
                    WebBeansLoggerFacade.getTokenString(OWBLogConst.TEXT_SAME_SCOPE));
        }

        @Override
        protected void defineName()
        {
            if (getAnnotated().isAnnotationPresent(Specializes.class))
            {
                AnnotatedType<? super C>  annotatedToSpecialize = getAnnotated();
                
                do
                {
                    Class<? super C> superclass = annotatedToSpecialize.getJavaClass().getSuperclass();
                    if (superclass.equals(Object.class))
                    {
                        throw new WebBeansConfigurationException("@Specialized Class : " + getAnnotated().getJavaClass().getName()
                                + " must not directly extend Object.class");
                    }
                    annotatedToSpecialize = webBeansContext.getAnnotatedElementFactory().newAnnotatedType(superclass);
                } while(annotatedToSpecialize.getAnnotation(Specializes.class) != null);


                defineName(annotatedToSpecialize, WebBeansUtil.getManagedBeanDefaultName(annotatedToSpecialize.getJavaClass().getSimpleName()));
            }
            if (name == null)
            {
                defineName(getAnnotated(), WebBeansUtil.getManagedBeanDefaultName(getAnnotated().getJavaClass().getSimpleName()));
            }
            else
            {
                // TODO XXX We have to check stereotypes here, too
                if (getAnnotated().getJavaClass().isAnnotationPresent(Named.class))
                {
                    throw new WebBeansConfigurationException("@Specialized Class : " + getAnnotated().getJavaClass().getName()
                            + " may not explicitly declare a bean name");
                }
            }
        }
        
        @Override
        protected void defineNullable()
        {
            defineNullable(false);
        }

        @Override
        protected Class<?> getType()
        {
            return annotated.getJavaClass();
        }

        @Override
        protected AnnotatedType<? super C> getSuperAnnotated()
        {
            AnnotatedType<? super C> annotatedType = getAnnotated();
            do
            {
                Class<? super C> superclass = annotatedType.getJavaClass().getSuperclass();
                if (superclass == null || superclass.equals(Object.class))
                {
                    return null;
                }
                annotatedType = webBeansContext.getAnnotatedElementFactory().newAnnotatedType(superclass);

            } while (annotatedType.getAnnotation(Specializes.class) != null);

            return annotatedType;
        }
    }
    
    private static class AnnotatedFieldBeanAttributesBuilder<M> extends AnnotatedMemberBeanAttributesBuilder<M, AnnotatedField<M>>
    {

        protected AnnotatedFieldBeanAttributesBuilder(WebBeansContext webBeansContext, AnnotatedField<M> annotated)
        {
            super(webBeansContext, annotated);
        }

        @Override
        protected Class<?> getType()
        {
            return annotated.getJavaMember().getType();
        }

        @Override
        protected void defineScope()
        {
            defineScope("Annotated producer field: " + getAnnotated().getJavaMember() +  "must declare default @Scope annotation");
        }

        @Override
        protected void defineName()
        {
            defineName(getAnnotated(), WebBeansUtil.getProducerDefaultName(getAnnotated().getJavaMember().getName()));
        }
        
        @Override
        protected void defineNullable()
        {
            defineNullable(!getAnnotated().getJavaMember().getType().isPrimitive());
        }

        @Override
        protected AnnotatedField<? super M> getSuperAnnotated()
        {
            AnnotatedField<M> thisField = getAnnotated();
            for (AnnotatedField<? super M> superField: getSuperType().getFields())
            {
                if (thisField.getJavaMember().getName().equals(superField.getJavaMember().getName())
                    && thisField.getBaseType().equals(superField.getBaseType()))
                {
                    return superField;
                }
            }
            return null;
        }
    }
    
    private static class AnnotatedMethodBeanAttributesBuilder<M> extends AnnotatedMemberBeanAttributesBuilder<M, AnnotatedMethod<M>>
    {

        protected AnnotatedMethodBeanAttributesBuilder(WebBeansContext webBeansContext, AnnotatedMethod<M> annotated)
        {
            super(webBeansContext, annotated);
        }

        @Override
        protected Class<?> getType()
        {
            return annotated.getJavaMember().getReturnType();
        }

        @Override
        protected void defineScope()
        {
            defineScope("Annotated producer method : " + getAnnotated().getJavaMember() +  "must declare default @Scope annotation");
        }

        @Override
        protected void defineName()
        {
            if (getAnnotated().isAnnotationPresent(Specializes.class))
            {
                AnnotatedMethod<? super M> superAnnotated = getSuperAnnotated();
                defineName(superAnnotated, WebBeansUtil.getProducerDefaultName(superAnnotated.getJavaMember().getName()));
            }
            if (name == null)
            {
                defineName(getAnnotated(), WebBeansUtil.getProducerDefaultName(getAnnotated().getJavaMember().getName()));
            }
            else
            {
                // TODO XXX We have to check stereotypes here, too
                if (getAnnotated().isAnnotationPresent(Named.class))
                {
                    throw new WebBeansConfigurationException("@Specialized Producer method : " + getAnnotated().getJavaMember().getName()
                            + " may not explicitly declare a bean name");
                }
            }
        }
        
        @Override
        protected void defineNullable()
        {
            defineNullable(!getAnnotated().getJavaMember().getReturnType().isPrimitive());
        }

        @Override
        protected AnnotatedMethod<? super M> getSuperAnnotated()
        {
            AnnotatedMethod<M> thisMethod = getAnnotated();
            for (AnnotatedMethod<? super M> superMethod: webBeansContext.getAnnotatedElementFactory().getFilteredAnnotatedMethods(getSuperType()))
            {
                List<AnnotatedParameter<M>> thisParameters = thisMethod.getParameters();
                if (thisMethod.getJavaMember().getName().equals(superMethod.getJavaMember().getName())
                    && thisMethod.getBaseType().equals(superMethod.getBaseType())
                    && thisParameters.size() == superMethod.getParameters().size())
                {
                    List<AnnotatedParameter<?>> superParameters = (List<AnnotatedParameter<?>>)(List<?>)superMethod.getParameters();
                    boolean match = true;
                    for (int i = 0; i < thisParameters.size(); i++)
                    {
                        if (!thisParameters.get(i).getBaseType().equals(superParameters.get(i).getBaseType()))
                        {
                            match = false;
                            break;
                        }
                    }
                    if (match)
                    {
                        return superMethod;
                    }
                }
            }
            return null;
        }
    }

    private abstract static class AnnotatedMemberBeanAttributesBuilder<M, A extends AnnotatedMember<M>> extends BeanAttributesBuilder<M, A>
    {
        protected AnnotatedMemberBeanAttributesBuilder(WebBeansContext webBeansContext, A annotated)
        {
            super(webBeansContext, annotated);
        }

        protected AnnotatedType<? super M> getSuperType()
        {
            Class<? super M> superclass = getAnnotated().getDeclaringType().getJavaClass().getSuperclass();
            if (superclass == null)
            {
                return null;
            }
            return webBeansContext.getAnnotatedElementFactory().getAnnotatedType(superclass);
        }
    }
}
