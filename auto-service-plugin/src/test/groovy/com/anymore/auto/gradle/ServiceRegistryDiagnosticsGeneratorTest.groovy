package com.anymore.auto.gradle

import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class ServiceRegistryDiagnosticsGeneratorTest {

    @Test
    void 'Debug诊断包含注册项排除项和可用状态'() {
        String source = new ServiceRegistryDiagnosticsGenerator().generate(catalog(), true).toString()

        assertTrue(source.contains('ServiceDiagnosticAvailability.AVAILABLE'))
        assertTrue(source.contains('ServiceDiagnosticStatus.REGISTERED'))
        assertTrue(source.contains('ServiceDiagnosticStatus.EXCLUDED'))
        assertTrue(source.contains('sample.RegisteredTask'))
        assertTrue(source.contains('sample.ExcludedTask'))
        assertTrue(source.contains('alias /debug/'))
    }

    @Test
    void 'Release诊断只保留不可用状态且不泄露候选元数据'() {
        String source = new ServiceRegistryDiagnosticsGenerator().generate(catalog(), false).toString()

        assertTrue(source.contains('ServiceDiagnosticAvailability.UNAVAILABLE_IN_NON_DEBUG_BUILD'))
        assertFalse(source.contains('sample.RegisteredTask'))
        assertFalse(source.contains('sample.ExcludedTask'))
        assertFalse(source.contains('alias /debug/'))
        assertFalse(source.contains('prod'))
    }

    @Test
    void 'Debug诊断生成代码不调用API24 Map默认方法'() {
        String source = new ServiceRegistryDiagnosticsGenerator().generate(catalog(), true).toString()

        assertFalse(source.contains('.getOrDefault('))
    }

    private static ServiceCatalog catalog() {
        ServiceCatalog catalog = new ServiceCatalog()
        ClassOrigin registered = new ClassOrigin('sample.RegisteredTask', 'classes', 'sample/RegisteredTask.class', 'a')
        ClassOrigin excluded = new ClassOrigin('sample.ExcludedTask', 'library.jar', 'sample/ExcludedTask.class', 'b')
        catalog.addClass(registered)
        catalog.addClass(excluded)
        catalog.addCandidate(new ServiceCandidate(
                'java.lang.Runnable', 'sample.RegisteredTask', 0, 'prod', true,
                ServiceCandidateStatus.REGISTERED, null, registered))
        catalog.addCandidate(new ServiceCandidate(
                'java.lang.Runnable', 'sample.ExcludedTask', 5, 'debug', false,
                ServiceCandidateStatus.EXCLUDED, 'alias /debug/', excluded))
        catalog
    }
}
