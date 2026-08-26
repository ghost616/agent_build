package com.ghost616.agentbase.service.agent.invoker;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;

/**
 * HOOK 数据载体类型解析与匹配工具类。
 *
 * <p>通过反射解析 {@link HookInvoker} 实现类的泛型参数 {@code D}（数据载体类型）：
 * 沿继承链与接口递归解析，支持类型变量映射（如泛型父类/中间接口逐层替换实际类型参数）。
 * 无法解析（无泛型信息、原始类型使用）时返回 {@code null}，匹配时放行。</p>
 *
 * <p>final 类，私有构造，仅提供静态方法。</p>
 *
 * @author ghost616
 */
public final class HookDataMatcher {

    private HookDataMatcher() {
    }

    /**
     * 解析 HookInvoker 实现类的数据载体类型（泛型参数 D）。
     *
     * @param hook HOOK 调用器，可为 null
     * @return 数据载体类型；无法解析时返回 null
     */
    public static Class<?> resolveDataType(HookInvoker<?, ?> hook) {
        if (hook == null) {
            return null;
        }
        return resolveFromClass(hook.getClass(), new HashMap<>());
    }

    /**
     * 判断 HOOK 是否匹配给定的数据载体。
     *
     * <p>数据载体类型无法解析（resolveDataType 返回 null）时放行返回 true。</p>
     *
     * @param hook HOOK 调用器
     * @param data 数据载体
     * @return 匹配返回 true
     */
    public static boolean matches(HookInvoker<?, ?> hook, HookData<?> data) {
        Class<?> dataType = resolveDataType(hook);
        if (dataType == null) {
            return true;
        }
        return data != null && dataType.isInstance(data);
    }

    /**
     * 沿类继承链与接口解析数据载体类型。
     */
    private static Class<?> resolveFromClass(Class<?> clazz, Map<TypeVariable<?>, Type> varMap) {
        if (clazz == null || clazz == Object.class) {
            return null;
        }
        for (Type iface : clazz.getGenericInterfaces()) {
            Class<?> resolved = resolveFromType(iface, varMap);
            if (resolved != null) {
                return resolved;
            }
        }
        Type genericSuper = clazz.getGenericSuperclass();
        if (genericSuper != null) {
            return resolveFromType(genericSuper, varMap);
        }
        return null;
    }

    /**
     * 解析单个类型引用，返回数据载体类型或 null。
     */
    private static Class<?> resolveFromType(Type type, Map<TypeVariable<?>, Type> varMap) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Class<?> raw = (Class<?>) pt.getRawType();
            Type[] args = pt.getActualTypeArguments();
            // HookInvoker 体系接口（含 SystemHook/SystemPostHook）双参数，第一个即数据载体类型 D
            if (args.length == 2 && isHookInvokerFamily(raw)) {
                return resolve(args[0], varMap);
            }
            // 中间类型：将类型变量映射到实际类型参数后继续解析
            Map<TypeVariable<?>, Type> newVarMap = new HashMap<>(varMap);
            TypeVariable<?>[] typeParams = raw.getTypeParameters();
            for (int i = 0; i < typeParams.length && i < args.length; i++) {
                newVarMap.put(typeParams[i], args[i]);
            }
            return resolveFromClass(raw, newVarMap);
        }
        if (type instanceof TypeVariable) {
            Type mapped = varMap.get(type);
            if (mapped != null) {
                return resolve(mapped, varMap);
            }
            return null;
        }
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (isHookInvokerFamily(clazz)) {
                return null;
            }
            return resolveFromClass(clazz, varMap);
        }
        return null;
    }

    /**
     * 将类型解析为具体 Class（类型变量沿映射递归解析，通配符取上界）。
     */
    private static Class<?> resolve(Type type, Map<TypeVariable<?>, Type> varMap) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            return raw instanceof Class ? (Class<?>) raw : null;
        }
        if (type instanceof TypeVariable) {
            Type mapped = varMap.get(type);
            if (mapped != null) {
                return resolve(mapped, varMap);
            }
            return null;
        }
        if (type instanceof WildcardType) {
            Type[] upper = ((WildcardType) type).getUpperBounds();
            if (upper.length > 0) {
                return resolve(upper[0], varMap);
            }
            return null;
        }
        if (type instanceof GenericArrayType) {
            return resolve(((GenericArrayType) type).getGenericComponentType(), varMap);
        }
        return null;
    }

    private static boolean isHookInvokerFamily(Class<?> raw) {
        return raw == HookInvoker.class || raw == SystemHook.class || raw == SystemPostHook.class;
    }
}
