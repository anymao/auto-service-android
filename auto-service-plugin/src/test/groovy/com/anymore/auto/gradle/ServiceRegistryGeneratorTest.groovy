package com.anymore.auto.gradle

import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class ServiceRegistryGeneratorTest {

    @Test
    void '注册表只生成已注册候选且类型为public final'() {
        ServiceCatalog catalog = catalogWithRegisteredAndExcluded()

        String source = new ServiceRegistryGenerator().generate(catalog).toString()

        assertTrue(source.contains('public final class ServiceRegistry'))
        assertTrue(source.contains('return new RegisteredTask();'))
        assertFalse(source.contains('ExcludedTask'))
    }

    @Test
    void '同一单例实现绑定多个接口时复用supplier'() {
        ServiceCatalog catalog = new ServiceCatalog()
        ClassOrigin origin = new ClassOrigin('sample.SharedTask', 'classes', 'sample/SharedTask.class', 'a')
        catalog.addClass(origin)
        ['java.lang.Runnable', 'java.util.concurrent.Callable'].each { String service ->
            catalog.addCandidate(new ServiceCandidate(
                    service, 'sample.SharedTask', 0, '', true,
                    ServiceCandidateStatus.REGISTERED, null, origin))
        }

        String source = new ServiceRegistryGenerator().generate(catalog).toString()

        assertEquals(1, source.count('newInstance()'))
        assertTrue(source.contains('supplier0'))
        assertTrue(source.contains('register(Runnable.class'))
        assertTrue(source.contains('register(Callable.class'))
    }

    @Test
    void '生成注册表兼容minSdk17且不要求core library desugaring'() {
        String source = new ServiceRegistryGenerator()
                .generate(catalogWithRegisteredAndExcluded())
                .toString()

        assertFalse(source.contains('java.util.function'))
        assertFalse(source.contains('.computeIfAbsent('))
        assertFalse(source.contains('.getOrDefault('))
    }

    private static ServiceCatalog catalogWithRegisteredAndExcluded() {
        ServiceCatalog catalog = new ServiceCatalog()
        ClassOrigin registered = new ClassOrigin('sample.RegisteredTask', 'classes', 'sample/RegisteredTask.class', 'a')
        ClassOrigin excluded = new ClassOrigin('sample.ExcludedTask', 'library.jar', 'sample/ExcludedTask.class', 'b')
        catalog.addClass(registered)
        catalog.addClass(excluded)
        catalog.addCandidate(new ServiceCandidate(
                'java.lang.Runnable', 'sample.RegisteredTask', 0, 'prod', false,
                ServiceCandidateStatus.REGISTERED, null, registered))
        catalog.addCandidate(new ServiceCandidate(
                'java.lang.Runnable', 'sample.ExcludedTask', 5, 'debug', false,
                ServiceCandidateStatus.EXCLUDED, 'alias /debug/', excluded))
        catalog
    }
}
