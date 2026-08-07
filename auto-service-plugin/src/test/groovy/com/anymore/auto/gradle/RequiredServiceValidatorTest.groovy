package com.anymore.auto.gradle

import org.gradle.api.GradleException
import org.junit.Test

import static org.junit.Assert.assertThrows
import static org.junit.Assert.assertTrue

class RequiredServiceValidatorTest {

    @Test
    void '没有任何候选时给出注解和插件提示'() {
        GradleException exception = assertThrows(GradleException) {
            new RequiredServiceValidator().validate(
                    ['sample.Missing': [''] as Set],
                    new ServiceCatalog())
        }

        assertTrue(exception.message.contains('No @AutoService implementation was found'))
        assertTrue(exception.message.contains('application module'))
    }

    @Test
    void '候选全部排除时按优先级和类名列出原因'() {
        ServiceCatalog catalog = new ServiceCatalog()
        add(catalog, 'sample.Later', 5, '', ServiceCandidateStatus.EXCLUDED, 'alias /later/')
        add(catalog, 'sample.First', -1, '', ServiceCandidateStatus.EXCLUDED, 'className /First/')

        GradleException exception = assertThrows(GradleException) {
            new RequiredServiceValidator().validate(['sample.Task': [''] as Set], catalog)
        }

        assertTrue(exception.message.contains('all were excluded'))
        assertTrue(exception.message.indexOf('sample.First') < exception.message.indexOf('sample.Later'))
        assertTrue(exception.message.contains('className /First/'))
    }

    @Test
    void '别名不匹配时列出稳定候选和可用别名'() {
        ServiceCatalog catalog = new ServiceCatalog()
        add(catalog, 'sample.Staging', 5, 'staging', ServiceCandidateStatus.REGISTERED, null)
        add(catalog, 'sample.Dev', 0, 'dev', ServiceCandidateStatus.REGISTERED, null)

        GradleException exception = assertThrows(GradleException) {
            new RequiredServiceValidator().validate(
                    ['sample.Task': ['production'] as Set],
                    catalog)
        }

        assertTrue(exception.message.contains('none match the requested alias'))
        assertTrue(exception.message.indexOf('sample.Dev') < exception.message.indexOf('sample.Staging'))
        assertTrue(exception.message.contains('Available aliases: ["dev", "staging"]'))
    }

    private static void add(
            ServiceCatalog catalog,
            String implementation,
            int priority,
            String alias,
            ServiceCandidateStatus status,
            String exclusionRule) {
        ClassOrigin origin = new ClassOrigin(
                implementation, 'classes', implementation.replace('.', '/') + '.class', implementation)
        catalog.addClass(origin)
        catalog.addCandidate(new ServiceCandidate(
                'sample.Task', implementation, priority, alias, false,
                status, exclusionRule, origin))
    }
}
