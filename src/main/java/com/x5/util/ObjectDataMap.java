package com.x5.util;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ObjectDataMap
 *
 * Box POJO/Bean/DataCapsule inside a Map.  When accessed, pry into object
 * using reflection/introspection/capsule-export and pull out all public
 * member fields/properties.
 * Convert field names from camelCase to lower_case_with_underscores
 * Convert bean properties from getSomeProperty() to some_property
 *  or isVeryHappy() to is_very_happy
 *
 * Values returned are copies, frozen at time of first access.
 *
 */
@SuppressWarnings("rawtypes")
public class ObjectDataMap implements Map
{
    private Map<String,Object> pickle = null;
    private Object object;
    private boolean isBean = false;

    private static final Map<String,Object> EMPTY_MAP = new HashMap<String,Object>();

    private static final HashSet<Class<?>> WRAPPER_TYPES = getWrapperTypes();

    private static HashSet<Class<?>> getWrapperTypes()
    {
        HashSet<Class<?>> ret = new HashSet<Class<?>>();
        ret.add(Boolean.class);
        ret.add(Character.class);
        ret.add(Byte.class);
        ret.add(Short.class);
        ret.add(Integer.class);
        ret.add(Long.class);
        ret.add(Float.class);
        ret.add(Double.class);
        ret.add(Void.class);
        return ret;
    }

    public static boolean isWrapperType(Class<?> clazz)
    {
        return WRAPPER_TYPES.contains(clazz);
    }

    public ObjectDataMap(Object pojo)
    {
        this.object = pojo;
    }

    private void init()
    {
        if (pickle == null) {
            pickle = mapify(object);
            // prevent multiple expensive calls to mapify
            // when result is null
            if (pickle == null) {
                pickle = EMPTY_MAP;
            }
        }
    }

    public static ObjectDataMap wrapBean(Object bean)
    {
        if (bean == null) return null;
        ObjectDataMap boxedBean = new ObjectDataMap(bean);
        boxedBean.isBean = true;

        return boxedBean;
    }

    private static final Class[] NO_ARGS = new Class[]{};
    private static Map<Class,Boolean> hasOwnToString = new HashMap<Class,Boolean>();

    public boolean isBean()
    {
        return this.isBean;
    }

    public static String getAsString(Object obj)
    {
        Class objClass = obj.getClass();
        Boolean doStringify = hasOwnToString.get(objClass);
        if (doStringify == null) {
            // perform expensive check... but just this once.
            Method toString = null;
            try {
                toString = obj.getClass().getMethod("toString", NO_ARGS);
            } catch (NoSuchMethodException e) {
            } catch (SecurityException e) {
            }
            doStringify = new Boolean(toString != null && !toString.getDeclaringClass().equals(Object.class));
            hasOwnToString.put(objClass, doStringify);
        }
        if (doStringify.booleanValue()) {
            // class has its own toString method -- safe.
            return obj.toString();
        } else {
            // don't expose Object's toString() info.
            return "OBJECT:" + obj.getClass().getName();
        }
    }

    @SuppressWarnings("unused")
    private Map<String,Object> mapify(Object pojo)
    {
        Map<String,Object> data = null;
        if (pojo instanceof DataCapsule) {
            return mapifyCapsule((DataCapsule)pojo);
        }
        if (!isBean) {
            if (hasNonFinalPublicFields(pojo)) {
                data = mapifyPOJO(pojo);
                if (data == null || data.isEmpty()) {
                    // hmmm, maybe it's a bean?
                    isBean = true;
                } else {
                    return data;
                }
            } else {
                // no public (non-final) fields, treat as bean
                isBean = true;
            }
        }
        if (isBean) {
            try {
                // java.beans.* is missing on android.
                // Test for existence before use...
                try {
                    Class<?> beanClass = Class.forName("java.beans.Introspector");
                    return StandardIntrospector.mapifyBean(pojo);
                } catch (ClassNotFoundException e) {
                    try {
                        Class<?> madrobotClass = Class.forName("com.madrobot.beans.Introspector");
                        return MadRobotIntrospector.mapifyBean(pojo);
                    } catch (ClassNotFoundException e2) {
                        // soldier on, treat as pojo
                    }
                }
            } catch (IntrospectionException e) {
                // hmm, not a bean after all...
            }
        }
        return data;
    }

    private static Map<Class,Field[]> declaredFields = new HashMap<Class,Field[]>();

    private boolean hasNonFinalPublicFields(Object pojo)
    {
        Field[] fields = grokFields(pojo);
        for (int i=0; i<fields.length; i++) {
            Field field = fields[i];
            int mods = field.getModifiers();
            if (Modifier.isPrivate(mods)) {
                continue;
            }
            if (Modifier.isProtected(mods)) {
                continue;
            }
            if (Modifier.isFinal(mods)) {
                continue;
            }

            return true;
        }

        return false;
    }

    private Field[] grokFields(Object pojo)
    {
        Field[] fields = null;

        Class pojoClass = pojo.getClass();
        if (declaredFields.containsKey(pojoClass)) {
            fields = declaredFields.get(pojoClass);
        } else {
            // expensive. cache!
            fields = pojoClass.getDeclaredFields();
            Field[] publicFields = pojoClass.getFields();
            if (publicFields != null && fields != null) {
                Field[] allFields = new Field[fields.length + publicFields.length];
                System.arraycopy(fields, 0, allFields, 0, fields.length);
                System.arraycopy(publicFields, 0, allFields, fields.length, publicFields.length);
                fields = allFields;
            } else if (fields == null) {
                fields = publicFields;
            }
            declaredFields.put(pojoClass, fields);
        }

        return fields;
    }

    public Map<String,Object> mapifyPOJO(Object pojo)
    {
        Field[] fields = grokFields(pojo);
        Map<String,Object> pickle = null;

        for (int i=0; i<fields.length; i++) {
            Field field = fields[i];
            String paramName = field.getName();
            Class paramClass = field.getType();

            // force access
            int mods = field.getModifiers();
            if (!Modifier.isPrivate(mods) && !Modifier.isProtected(mods)) {
                field.setAccessible(true);
            }

            Object paramValue = null;
            try {
                paramValue = field.get(pojo);
            } catch (IllegalAccessException e) {
                continue;
            }

            if (pickle == null) pickle = new HashMap<String,Object>();
            // convert isActive to is_active
            paramName = splitCamelCase(paramName);
            storeValue(pickle, paramClass, paramName, paramValue, isBean);
        }

        return pickle;
    }

    private Map<String,Object> mapifyCapsule(DataCapsule capsule)
    {
        DataCapsuleReader reader = DataCapsuleReader.getReader(capsule);

        String[] tags = reader.getColumnLabels(null);
        Object[] data = reader.extractData(capsule);

        pickle = new HashMap<String,Object>();
        for (int i=0; i<tags.length; i++) {
            Object val = data[i];
            if (val == null) continue;
            if (val instanceof String) {
                pickle.put(tags[i], val);
            } else if (val instanceof DataCapsule) {
                pickle.put(tags[i], new ObjectDataMap(val));
            } else {
                pickle.put(tags[i], val.toString());
            }
        }

        return pickle;
    }

    private static void storeValue(Map<String,Object> pickle, Class paramClass,
                            String paramName, Object paramValue, boolean isBean)
    {
        if (paramValue == null) {
            pickle.put(paramName, null);
        } else if (paramClass == String.class) {
            pickle.put(paramName, paramValue);
        } else if (paramClass.isArray() || paramValue instanceof List) {
            pickle.put(paramName, paramValue);
        } else if (paramValue instanceof Boolean) {
            if (((Boolean)paramValue).booleanValue()) {
                pickle.put(paramName, "TRUE");
            }
        } else if (paramClass.isPrimitive() || isWrapperType(paramClass)) {
            pickle.put(paramName, paramValue);
        } else {
            // box all non-primitive object member fields
            // in their own ObjectDataMap wrapper.
            // lazy init guarantees no infinite recursion here.
            ObjectDataMap boxedParam = isBean ? wrapBean(paramValue) : new ObjectDataMap(paramValue);
            pickle.put(paramName, boxedParam);
        }

    }

    private static Map<String,String> snakeCased = new HashMap<String,String>();

    // splitCamelCase converts SimpleXMLStuff to simple_xml_stuff
    public static String splitCamelCase(String s)
    {
        String cached = snakeCased.get(s);
        if (cached != null) return cached;

        // no regex! single pass through string
        StringBuilder snakeCase = new StringBuilder();
        int m = 0;
        char[] chars = s.toCharArray();
        // ascii lower
        char[] lower = new char[chars.length];
        for (int i=0; i<chars.length; i++) {
            char c = chars[i];
            lower[i] = (c >= 'A' && c <= 'Z') ? (char)(c + 32) : c;
        }
        for (int i=1; i<chars.length; i++) {
            char c0 = chars[i-1];
            char c1 = chars[i];
            if (c0 < 'A' || c0 > 'Z') {
                if (c1 >= 'A' && c1 <= 'Z') {
                    // non-cap, then cap
                    snakeCase.append(lower, m, i-m);
                    snakeCase.append('_');
                    m = i;
                }
            } else if (i-m > 1 && c0 >= 'A' && c0 <= 'Z' && (c1 > 'Z' || c1 < 'A')) {
                snakeCase.append(lower, m, i-1-m);
                snakeCase.append('_');
                m = i-1;
            }
        }
        snakeCase.append(lower, m, lower.length-m);

        cached = snakeCase.toString();
        snakeCased.put(s, cached);
        return cached;
    }

    public int size()
    {
        init();
        return pickle.size();
    }

    public boolean isEmpty()
    {
        init();
        return pickle.isEmpty();
    }

    public boolean containsKey(Object key)
    {
        init();
        return pickle.containsKey(key);
    }

    public boolean containsValue(Object value)
    {
        init();
        return pickle.containsValue(value);
    }

    public Object get(Object key)
    {
        init();
        return pickle.get(key);
    }

    public Object put(Object key, Object value)
    {
        // unsupported
        return null;
    }

    public Object remove(Object key)
    {
        // unsupported
        return null;
    }

    public void putAll(Map m)
    {
        // unsupported
    }

    public void clear()
    {
        // unsupported
    }

    public Set keySet()
    {
        init();
        return pickle.keySet();
    }

    public Collection values()
    {
        init();
        return pickle.values();
    }

    public Set entrySet()
    {
        init();
        return pickle.entrySet();
    }

    private static class IntrospectionException extends Exception
    {
        private static final long serialVersionUID = 8890979383599687484L;
    }

    private static class StandardIntrospector
    {
        private static Map<String,Object> mapifyBean(Object bean)
        throws IntrospectionException
        {
            PropertyDescriptor[] properties = null;
            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
                properties = beanInfo.getPropertyDescriptors();
            } catch (java.beans.IntrospectionException e) {
                throw new IntrospectionException();
            }

            if (properties == null) return null;

            Map<String,Object> pickle = null;

            // copy properties into hashtable
            for (PropertyDescriptor property : properties) {
                Method getter = property.getReadMethod();
                if (getter == null) continue;
                try {
                    Object paramValue = getter.invoke(bean, (Object[])null);

                    if (paramValue != null) {
                        // converts isActive() to is_active
                        // converts getBookTitle() to book_title
                        String paramName = property.getName();
                        paramName = splitCamelCase(paramName);
                        if (paramValue instanceof Boolean) {
                            paramName = "is_"+paramName;
                        }

                        if (pickle == null) pickle = new HashMap<String,Object>();

                        Class paramClass = property.getPropertyType();
                        storeValue(pickle, paramClass, paramName, paramValue, true);
                    }
                } catch (InvocationTargetException e) {
                } catch (IllegalAccessException e) {
                }
            }

            return pickle;
        }
    }

    // mad robot provides a stopgap introspection library for android projects
    private static class MadRobotIntrospector
    {
        private static Map<String,Object> mapifyBean(Object bean)
        throws IntrospectionException
        {
            com.madrobot.beans.PropertyDescriptor[] properties = null;
            try {
                com.madrobot.beans.BeanInfo beanInfo = com.madrobot.beans.Introspector.getBeanInfo(bean.getClass());
                properties = beanInfo.getPropertyDescriptors();
            } catch (com.madrobot.beans.IntrospectionException e) {
                throw new IntrospectionException();
            }

            if (properties == null) return null;

            Map<String,Object> pickle = null;

            // copy properties into hashtable
            for (com.madrobot.beans.PropertyDescriptor property : properties) {
                Class paramClass = property.getPropertyType();
                Method getter = property.getReadMethod();
                try {
                    Object paramValue = getter.invoke(bean, (Object[])null);

                    if (paramValue != null) {
                        // converts isActive() to is_active
                        // converts getBookTitle() to book_title
                        String paramName = property.getName();
                        paramName = splitCamelCase(paramName);
                        if (paramValue instanceof Boolean) {
                            paramName = "is_"+paramName;
                        }

                        if (pickle == null) pickle = new HashMap<String,Object>();

                        storeValue(pickle, paramClass, paramName, paramValue, true);
                    }
                } catch (InvocationTargetException e) {
                } catch (IllegalAccessException e) {
                }
            }

            return pickle;
        }
    }

    public String toString()
    {
        return getAsString(this.object);
    }

    public Object unwrap()
    {
        return this.object;
    }
}
