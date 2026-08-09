package com.anymore.auto

import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidApiCompatibilityTest {

    @Test
    fun `运行时服务工厂不依赖API24 Supplier`() {
        val supplierClass = Class.forName("java.util.function.Supplier")
        val runtimeTypes = listOf(
            ServiceSupplier::class.java,
            ServiceLazy::class.java,
            SingletonServiceSupplier::class.java
        )

        assertFalse(runtimeTypes.any { supplierClass.isAssignableFrom(it) })
        assertFalse(
            runtimeTypes
                .flatMap { it.declaredConstructors.asIterable() }
                .flatMap { it.parameterTypes.asIterable() }
                .any { it == supplierClass }
        )
    }

    @Test
    fun `注册表签名不暴露API24 Supplier`() {
        val getMethod = ServiceRegistry::class.java.getDeclaredMethod(
            "get",
            Class::class.java,
            String::class.java
        )

        assertFalse(getMethod.genericReturnType.typeName.contains("java.util.function.Supplier"))
    }
}
