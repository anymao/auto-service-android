package com.anymore.auto.gradle

import org.gradle.api.GradleException
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertThrows
import static org.junit.Assert.assertTrue

class ServiceCatalogTest {

    @Test
    void '注册项按优先级和类名排序并保留排除项'() {
        def catalog = new ServiceCatalog()
        def zebraOrigin = new ClassOrigin('sample.Zebra', 'app', 'sample/Zebra.class', 'a')
        def alphaOrigin = new ClassOrigin('sample.Alpha', 'lib.jar', 'sample/Alpha.class', 'b')
        catalog.addClass(zebraOrigin)
        catalog.addClass(alphaOrigin)
        catalog.addCandidate(new ServiceCandidate(
                'java.lang.Runnable',
                'sample.Zebra',
                0,
                '',
                false,
                ServiceCandidateStatus.REGISTERED,
                null,
                zebraOrigin))
        catalog.addCandidate(new ServiceCandidate(
                'java.lang.Runnable',
                'sample.Alpha',
                -1,
                'debug',
                true,
                ServiceCandidateStatus.EXCLUDED,
                'alias /debug/',
                alphaOrigin))

        assertEquals(['sample.Zebra'],
                catalog.registeredFor('java.lang.Runnable')*.implementationClassName)
        assertEquals(['sample.Alpha'], catalog.excludedCandidates()*.implementationClassName)
        assertEquals(2, catalog.candidateImplementationCount())
    }

    @Test
    void '同优先级注册项按实现类名排序'() {
        def catalog = new ServiceCatalog()
        ['sample.Zebra', 'sample.Alpha'].eachWithIndex { String name, int index ->
            def origin = new ClassOrigin(name, 'classes', name.replace('.', '/') + '.class', index.toString())
            catalog.addClass(origin)
            catalog.addCandidate(new ServiceCandidate(
                    'java.lang.Runnable',
                    name,
                    0,
                    '',
                    false,
                    ServiceCandidateStatus.REGISTERED,
                    null,
                    origin))
        }

        assertEquals(
                ['sample.Alpha', 'sample.Zebra'],
                catalog.registeredFor('java.lang.Runnable')*.implementationClassName)
    }

    @Test
    void '不同来源出现同名普通类时失败'() {
        def catalog = new ServiceCatalog()
        catalog.addClass(new ClassOrigin(
                'sample.Duplicate',
                'first.jar',
                'sample/Duplicate.class',
                'a'))

        def exception = assertThrows(GradleException) {
            catalog.addClass(new ClassOrigin(
                    'sample.Duplicate',
                    'second.jar',
                    'sample/Duplicate.class',
                    'b'))
        }

        assertTrue(exception.message.contains('sample.Duplicate'))
        assertTrue(exception.message.contains('first.jar'))
        assertTrue(exception.message.contains('second.jar'))
    }

    @Test
    void '生成保留类允许由不同输入提供存根'() {
        def catalog = new ServiceCatalog()
        catalog.addClass(new ClassOrigin(
                'com.anymore.auto.ServiceRegistry',
                'first.jar',
                'com/anymore/auto/ServiceRegistry.class',
                'a'))
        catalog.addClass(new ClassOrigin(
                'com.anymore.auto.ServiceRegistry',
                'second.jar',
                'com/anymore/auto/ServiceRegistry.class',
                'b'))

        assertEquals('first.jar', catalog.classOrigin('com.anymore.auto.ServiceRegistry').containerName)
    }
}
