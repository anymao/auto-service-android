package com.anymore.auto.gradle;

/**
 * 排除规则
 * Created by anymore on 2022/9/10.
 */
class ExclusiveRule implements Serializable {
    /**
     * 排除的类名，支持正则
     */
    private String className
    /**
     * 排除的别名，支持正则
     */
    private String alias

    ExclusiveRule(String className, String alias) {
        this.className = className
        this.alias = alias
    }

    String getClassName() {
        return className
    }

    String getAlias() {
        return alias
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        ExclusiveRule that = (ExclusiveRule) o
        if (alias != that.alias) return false
        if (className != that.className) return false
        return true
    }

    int hashCode() {
        int result
        result = (className != null ? className.hashCode() : 0)
        result = 31 * result + (alias != null ? alias.hashCode() : 0)
        return result
    }


    @Override
    String toString() {
        return "ExcludeRule{" +
                "className='" + className + '\'' +
                ", alias='" + alias + '\'' +
                '}';
    }
}
